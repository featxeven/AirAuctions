# storage.yml - Database, Redis & Networks

Where auction data lives and how multiple servers share it.

> **Two rules for this file**
> 1. It is **server-specific** - every server in a network needs its own copy and
>    is never synced from another server.
> 2. Changes require a **full server restart**; `/airauctions reload` does not
>    re-open database or Redis connections.

## server-id

```yml
server-id: ""
```

A unique identifier for this server instance. Leave it empty and one is generated
on first start and saved to `data/.server-id`. Set it manually (e.g. `survival-1`,
`skyblock-2`) when you want readable ids in a network - just make sure no two
servers share the same value.

## database

```yml
database:
  driver: SQLITE
  listing-id-format: AUTO_INCREMENT
  uuid-length: 8
  table-prefix: "aa_"
```

| Option | Values | Notes |
| --- | --- | --- |
| `driver` | `SQLITE`, `MYSQL`, `MARIADB`, `MONGODB` | See the table below. |
| `listing-id-format` | `AUTO_INCREMENT`, `UUID` | Determines what players see as a listing id (`/ah delete 42` vs `/ah delete a1b2c3d4`). |
| `uuid-length` | `4`-`32` | Length of generated ids when using `UUID`. Ignored otherwise. |
| `table-prefix` | any string | Prefix for all plugin tables/collections. Must be identical on every server sharing a database. |

| Driver | When to use | Requirements |
| --- | --- | --- |
| `SQLITE` | Single server, simplest setup | None - file at `data/database.db` |
| `MYSQL` | Any multi-server network | MySQL 8.0+ |
| `MARIADB` | Same as MySQL, MariaDB server | Uses the MySQL connector |
| `MONGODB` | Large networks already running Mongo | MongoDB 5.0+ (standalone, replica set or Atlas) |

On MongoDB, `AUTO_INCREMENT` works but costs one extra lookup per new listing.
High-volume Mongo servers should use `UUID`.

### SQLite

```yml
  sqlite:
    file: "data/database.db"
```

Path is relative to `plugins/AirAuctions/`. Good for a single server; not usable
for sharing data between servers.

### MySQL / MariaDB

```yml
  mysql:
    host: "localhost"
    port: 3306
    database: "airauctions"
    user: "root"
    password: ""
    ssl: false
    auto-reconnect: true
    pool:
      size: 5
      name: "AirAuctions-Pool"
      connection-timeout: 30000   # ms to wait for a free connection
      idle-timeout: 600000        # ms an idle connection is kept
      max-lifetime: 1800000       # ms before a connection is recycled
```

Use a dedicated database user with rights only on the AirAuctions database. The
pool defaults are fine for most servers; raise `pool.size` only if the console
reports connection timeouts. `max-lifetime` should stay below your MySQL
`wait_timeout`.

### MongoDB

```yml
  mongodb:
    uri: ""                       # full connection string; overrides the fields below
    host: "localhost"
    port: 27017
    database: "airauctions"
    user: ""
    password: ""
    auth-source: "admin"
    ssl: false
    pool:
      min-size: 0
      max-size: 5
      connect-timeout: 10000
      server-selection-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000
```

For Atlas, paste the `mongodb+srv://...` string into `uri` and leave the
individual fields as they are.

## redis

```yml
redis:
  enabled: false
  host: "localhost"
  port: 6379
  password: ""
  database: 0
  timeout: 3000          # ms before a Redis operation gives up
  pool-size: 4
  resync-interval: 60    # seconds between full cache resyncs from the database
```

Redis is only needed on **networks**. It keeps each server's cache in sync and
makes actions (a purchase, a new bid) appear instantly on the other servers.
Without Redis, servers still share the database but only see each other's changes
after the next resync, so players can briefly see stale listings.

`resync-interval` is the safety net: lower it if you run without Redis and want
staler data corrected faster (at the cost of more database queries).

## Multi-server setup

1. Create one MySQL/MariaDB/MongoDB database reachable from every server.
2. On **each** server, edit `storage.yml`:
   - same `driver` and credentials,
   - same `table-prefix`,
   - a **different** `server-id`,
   - `redis.enabled: true` with the same Redis instance.
3. Recommended: `listing-id-format: UUID` so ids never collide.
4. Copy `config.yml`, `expansions.yml`, `commands.yml`, `lang/` and `guis/` to every
   server so players see identical menus and rules. Never copy `storage.yml` or
   `data/.server-id`.
5. Restart all servers.

Schema creation and upgrades are handled by the plugin itself on startup - there
is nothing to import by hand.

## Backups

- **SQLite**: back up `plugins/AirAuctions/data/database.db` while the server is
  stopped (or use your host's snapshot tooling).
- **MySQL/MariaDB**: `mysqldump` of the AirAuctions database.
- **MongoDB**: `mongodump` of the configured database.

Listings hold real player items, so treat this database like the rest of your
world data and include it in your normal backup rotation.
