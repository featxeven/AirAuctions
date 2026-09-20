# Commands & Permissions

Two commands exist: the player command (`/auctionhouse`, alias `/ah`) and the
admin command (`/airauctions`). The player command's names, aliases and usage
strings are configurable in [commands.yml](commands-yml.md); the admin command is
fixed.

## Player commands

Default names - rename or disable any of them in `commands.yml`.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ah` | none | Opens the marketplace |
| `/ah sell <price> [amount] [economy]` | `airauctions.command.sell` | Lists the held item at a fixed price |
| `/ah bid <initial-price> [duration] [amount] [economy]` | `airauctions.command.bid` | Lists the held item as an auction |
| `/ah listings` | `airauctions.command.listings` | Own active listings |
| `/ah listings <player>` | `airauctions.command.listings.others` | Another player's active listings |
| `/ah expired` | `airauctions.command.expired` | Own expired listings |
| `/ah expired <player>` | `airauctions.command.expired.others` | Another player's expired listings |
| `/ah storage` | `airauctions.command.storage` | Own item storage (bought/won items) |
| `/ah storage <player>` | `airauctions.command.storage.others` | Another player's storage |
| `/ah history` | `airauctions.command.history` | Own transaction history |
| `/ah history <player>` | `airauctions.command.history.others` | Another player's history |
| `/ah search <query>` | `airauctions.command.search` | Searches listings |
| `/ah player <player>` | `airauctions.command.player` | Opens a player's listings |
| `/ah delete <id>` | `airauctions.command.delete` | Deletes any listing (staff) |
| `/ah slots give\|take <amount> [player]` | `airauctions.command.slots` | Gives or takes bonus listing slots |

`airauctions.use` is the bundle you normally give to everyone: sell, bid,
listings, storage, expired, history, search and player - but not `delete` or
`slots`.

```yml
# LuckPerms
/lp group default permission set airauctions.use true
/lp group moderator permission set airauctions.command.delete true
/lp group moderator permission set airauctions.command.listings.others true
```

## Admin commands

`/airauctions` requires `airauctions.admin`.

| Command | Purpose |
| --- | --- |
| `/airauctions reload` | Reloads configs, language, GUIs, hooks and economy providers |
| `/airauctions version` | Plugin version and update check |
| `/airauctions open <gui-id> <player> [category] [type] [economy] [sort] [target]` | Opens any GUI for a player - the fastest way to test layout changes |
| `/airauctions simulate ...` | Generates fake listings on a test server |

`/airauctions simulate` additionally requires `airauctions.admin.simulate`. It
creates real listings from generated data - use it on a test server only. Large
runs ask for confirmation before executing.

### What reload does and does not do

Reload re-reads `config.yml`, `expansions.yml`, `commands.yml` *values*,
`data/filter.yml`, `data/animations.yml`, the language files and all GUIs, and
re-resolves economy providers and hooks.

It does **not** re-register commands (names/aliases from `commands.yml`) and does
**not** reconnect the database or Redis. Those need a full server restart. See
[installation.md](installation.md#reload-vs-restart).

## Permission groups

| Permission | Grants |
| --- | --- |
| `airauctions.admin` | Everything: all commands and all bypasses |
| `airauctions.use` | The standard player set |
| `airauctions.command.*` | Every command, including `delete` and `slots` |
| `airauctions.bypass.*` | Every bypass |

Defaults are `op`, so without explicit grants only operators can use the plugin.
Give `airauctions.use` to your default group.

## Bypass permissions

| Permission | Effect |
| --- | --- |
| `airauctions.bypass.limit` | No active listing limit |
| `airauctions.bypass.cooldown` | No cooldown between listings |
| `airauctions.bypass.expire-time` | No listing lifetime cap |
| `airauctions.bypass.duration-time` | No auction duration cap |
| `airauctions.bypass.gamemodes` | Can list in blocked gamemodes |
| `airauctions.bypass.worlds` | Can list in blocked worlds |
| `airauctions.bypass.damaged-items` | Can list damaged items |
| `airauctions.bypass.blacklist` | Can list blacklisted items |
| `airauctions.bypass.fee` | Pays no listing fee |
| `airauctions.bypass.tax` | Pays no sales tax |

## Tiered (numeric) permissions

Several bypasses also work as **tiers** by appending a number - this is how you
build donor ranks without extra config.

| Permission pattern | Meaning | Picked |
| --- | --- | --- |
| `airauctions.bypass.limit.<n>` | Max active listings | Highest tier, never below the `config.yml` default |
| `airauctions.bypass.expire-time.<seconds>` | Max listing lifetime | Highest |
| `airauctions.bypass.duration-time.<seconds>` | Max auction duration | Highest |
| `airauctions.bypass.fee.<value>` | Listing fee for that player | Lowest |
| `airauctions.bypass.tax.<value>` | Sales tax for that player | Lowest |

The plain permission without a number always means *unlimited* (or *waived*, for
fee and tax). Fee and tax tiers are interpreted in the unit configured in
[expansions.yml](expansions-yml.md) - a percentage when `type: PERCENTAGE`, a flat
amount when `type: FIXED`.

```yml
# VIP: 10 listings, 2% tax instead of the default
airauctions.bypass.limit.10
airauctions.bypass.tax.2

# MVP: unlimited listings, no fee at all
airauctions.bypass.limit
airauctions.bypass.fee
```

Bonus slots granted with `/ah slots give <amount> [player]` stack on top of the
resolved limit and are stored per player in the database, so they survive rank
changes.
