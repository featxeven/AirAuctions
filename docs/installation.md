# Installation & File Layout

## Requirements

| Requirement | Notes |
| --- | --- |
| Server software | Paper (or a Paper fork). Folia is supported. |
| Minecraft version | API version `1.21` or newer |
| Java | The version required by your Paper build (Java 21+ for 1.21) |

### Optional plugins

None of these are required; AirAuctions detects them at startup and again on every
`/airauctions reload`.

| Plugin | Used for |
| --- | --- |
| Vault + an economy plugin | The `VAULT` currency type (server money) |
| PlaceholderAPI | The `%airauctions_...%` placeholders and `PLACEHOLDER` currencies |
| PlayerPoints | The `PLAYERPOINTS` currency type |
| ExcellentEconomy | The `EXCELLENTECONOMY` currency type |
| Nexo, ItemsAdder, CraftEngine | Custom items in GUIs and in the blacklist/categories |

## First start

1. Stop the server, place `AirAuctions.jar` in `plugins/`, start the server.
2. All configuration files are written to `plugins/AirAuctions/` on first start.
3. Stop the server again before editing `storage.yml` (database settings are only
   read at startup), then start it and configure the rest live with
   `/airauctions reload`.

Missing files are re-created from the jar on every startup and reload, so deleting
a file is a safe way to reset it to defaults. Existing files are **never**
overwritten - new options added by a plugin update do not appear automatically in
your old files; compare against the defaults in the jar after updating.

## File layout

```
plugins/AirAuctions/
├── config.yml                  Core behaviour (limits, lifetimes, restrictions)
├── expansions.yml              Currencies, fees, taxes, Discord webhooks
├── storage.yml                 Database, Redis, server id  (server-specific)
├── commands.yml                Command names, aliases, tab-completion
├── data/
│   ├── filter.yml              Item categories for the browse menu
│   ├── animations.yml          Reusable text animations
│   ├── database.db             SQLite database (only when driver: SQLITE)
│   └── .server-id              Auto-generated id for this server instance
├── lang/
│   ├── messages/en_US.yml      All chat/actionbar/title messages
│   └── items/en_US.yml         Translated item names used by %item%
└── guis/
    ├── shared.yml              Defaults, aliases and item templates for all GUIs
    ├── browsing/               Marketplace, categories, search, bid/shulker views
    ├── player/                 The viewer's own active/expired/storage/history menus
    ├── target/                 Another player's listings (staff view)
    ├── confirm/                Confirmation screens (buy, bid, cancel, delete, ...)
    └── .input/                 Chat / sign / dialog prompt definitions
```

### What each file controls

| File | Controls | Guide |
| --- | --- | --- |
| `config.yml` | Listing limits, cooldowns, expiry, sniper protection, notifications, blacklist | [config.yml](config-yml.md) |
| `expansions.yml` | Currencies and their display, listing fee, sales tax, Discord embeds | [expansions.yml](expansions-yml.md) |
| `storage.yml` | Where data is stored and how servers share it | [storage.yml](storage-yml.md) |
| `commands.yml` | The `/ah` command name, aliases, subcommand names, tab-complete | [commands.yml](commands-yml.md) |
| `data/filter.yml` | The categories players can filter by | [filter.yml](filter-yml.md) |
| `data/animations.yml` | Named animated text snippets usable anywhere | [Language & animations](language-and-animations.md) |
| `lang/**` | Every piece of text the plugin sends, plus item name translations | [Language & animations](language-and-animations.md) |
| `guis/**` | Every menu: size, title, slots, items, click actions | [GUI configuration](guis.md) |

## Reload vs restart

Run `/airauctions reload` after editing configuration. It reloads all config
files, language files, GUIs, economy providers and third-party hooks, and reports
how long it took.

| Change | Reload is enough | Restart required |
| --- | --- | --- |
| `config.yml`, `expansions.yml` | Yes | - |
| `data/filter.yml`, `data/animations.yml` | Yes | - |
| `lang/**`, `guis/**` | Yes | - |
| `commands.yml` (names, aliases, shortcuts, duration units) | - | Yes |
| `storage.yml` (database, Redis, server id) | - | Yes |

If a reload fails you get *"Reload failed. Check console for details."* in chat -
the console line names the exact file and the YAML error. The previously loaded
values stay active until the problem is fixed, so a broken edit does not take the
auction house down.

## Multi-server networks

`storage.yml` is deliberately **not** synced between servers: each server needs its
own copy with its own `server-id`, but pointing at the same database. Everything
else (config, GUIs, language) can be copied freely between servers. See
[storage.yml](storage-yml.md#multi-server-setup).
