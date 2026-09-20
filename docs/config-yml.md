# config.yml - Core Behaviour

This is the main file: limits, timings, confirmations, sorting/filtering options
and item restrictions. Changes apply with `/airauctions reload`.

Throughout the file, **`-1` means "disabled" or "no limit"**.

## general

| Option | Default | What it does |
| --- | --- | --- |
| `lang` | `en_US` | Which file under `lang/messages/` is used. Set to `fr_FR` to load `lang/messages/fr_FR.yml`. |
| `items-lang` | `en_US` | Which file under `lang/items/` provides translated item names. |
| `notify-updates` | `true` | Tells admins on join when a newer plugin version exists. |
| `console-feedback` | `true` | When console runs a command targeting a player, that player also gets the feedback message. |
| `strict-args` | `true` | Rejects commands with extra trailing arguments instead of ignoring them. |

## formatting

Controls how times and dates are rendered in messages and menus.

```yml
formatting:
  duration:
    mode: CUSTOM       # DETAILED | SEQUENTIAL | CUSTOM
    granularity: 2     # only used by CUSTOM: 1 = largest unit only, 4 = full breakdown
  time: "HH:mm"        # e.g. "hh:mm a" for 02:05 PM
  date: "dd/MM/yy"     # e.g. "MMMM dd, yyyy" for March 08, 2026
  timezone: "system"   # IANA zone id (e.g. "Europe/Madrid") or "system"
```

| `duration.mode` | Example output |
| --- | --- |
| `DETAILED` | `2 days 3 hours 4 minutes 36 seconds` |
| `SEQUENTIAL` | one unit at a time, largest first |
| `CUSTOM` | up to `granularity` units, e.g. `2 days 3 hours` |

The words used for units (`day`, `hours`, ...) come from
`lang/messages/<lang>.yml` under `placeholders.time`, so they translate with the
rest of the language file.

## listings

Applies to both auction listings and bid (timed auction) listings.

### Limits

| Option | Default | Meaning |
| --- | --- | --- |
| `max-active` | `3` | Active listings a player may have. Bypass/extend with `airauctions.bypass.limit.<number>`. |
| `max-history` | `150` | Entries shown in the history menu per player. `-1` = unlimited. |
| `max-expired` | `50` | Maximum entries in the expired menu. When full, the player must claim items before listing again. `-1` = unlimited. |
| `cooldown` | `5` | Seconds between two listings by the same player. `-1` = off. Bypass: `airauctions.bypass.cooldown`. |

Extra slots can also be granted at runtime with `/ah slots give <amount> [player]`
(see [Commands](commands-and-permissions.md)); those stack on top of `max-active`.

### Item amount

| Option | Default | Meaning |
| --- | --- | --- |
| `amount-mode` | `OPTIONAL` | How `/ah sell` and `/ah bid` treat the `[amount]` argument: `REQUIRED` (always typed), `OPTIONAL` (defaults to the whole held stack), `DISABLED` (always the whole stack). |
| `min-amount` | `1` | Minimum items per listing. `1` or `-1` = no minimum. |

### Lifecycle

| Option | Default | Meaning |
| --- | --- | --- |
| `drop-on-full-inventory` | `false` | If the player's inventory is full when items are returned, drop them at their feet instead of keeping them in the menu. |
| `instant-cancel` | `false` | Cancelled listings go straight back to the inventory instead of the expired menu. |
| `expired-purge-delay` | `604800` (7 days) | Seconds before an unclaimed expired listing is deleted permanently. `-1` = never purge. |
| `sweep-interval` | `5` | Seconds between expiry checks. Lower = more precise expiry, slightly more database work. |

> `expired-purge-delay` deletes items players never claimed. Set it to `-1` if you
> do not want players to ever lose items, at the cost of the table growing forever.

### Deletion (staff)

| Option | Default | Meaning |
| --- | --- | --- |
| `require-delete-confirmation` | `true` | Staff must confirm before a listing is deleted with `/ah delete <id>`. |
| `delete-confirmation-timeout` | `60` | Seconds the pending confirmation stays valid. Only used as a chat fallback when `guis/confirm/delete.yml` has `enabled: false`. |

### Sort and filter options

`listings.sort` defines the sort modes offered in the menus, split into `active`,
`unclaimed` (expired + storage) and `history`. `listings.filter.type` defines the
listing-type filter (`all`, `auctions`, `bids`).

Each entry is `key: "Display Name"`. **The order in the file is the cycle order in
the menus**, and removing an entry removes that option from the menus. The keys
themselves are recognised by the plugin - rename the display text freely, but do
not invent new keys.

```yml
  sort:
    active:
      newest_date: "Newest Date"
      highest_price: "Highest Price"
```

Category and currency filters are configured elsewhere:
[data/filter.yml](filter-yml.md) and [expansions.yml](expansions-yml.md).

## auctions

Fixed-price listings.

| Option | Default | Meaning |
| --- | --- | --- |
| `allow-self-purchase` | `false` | Whether a seller can buy their own listing. |
| `lifetime` | `86400` (24 h) | Seconds before an unsold listing expires. `-1` = never. Per-rank override: `airauctions.bypass.expire-time.<seconds>`. |
| `require-confirmation` | `true` | Seller sees a confirmation screen before the listing goes live. |
| `confirmation-timeout` | `60` | Seconds before an unconfirmed listing is discarded. Chat fallback used when `guis/confirm/auction.yml` is disabled. |
| `announce-to-seller` | `false` | Whether the global "new listing" broadcast is also sent to the seller. |

## bids

Timed listings where players bid against each other.

| Option | Default | Meaning |
| --- | --- | --- |
| `allow-self-bidding` | `false` | Whether a seller can bid on their own listing. |
| `min-increment` | `5` | A new bid must beat the current one by at least this much. |
| `max-increment` | `100000` | Largest allowed jump over the current bid. `-1` = no limit. |
| `min-duration` / `max-duration` | `60` / `3600` | Allowed listing durations in seconds. `max-duration: -1` = unlimited. Per-rank override: `airauctions.bypass.duration-time.<seconds>`. |
| `default-duration` | `300` | Used when the player does not type a duration. |
| `require-confirmation` | `true` | Confirmation screen before the bid listing goes live. |
| `confirmation-timeout` | `60` | Seconds before an unconfirmed bid listing is discarded. |
| `announce-to-seller` | `false` | Send the global broadcast to the seller too. |

### Sniper protection

| Option | Default | Meaning |
| --- | --- | --- |
| `snipe-window` | `5` | A bid placed within this many seconds of the end counts as a snipe. |
| `snipe-extend` | `20` | Seconds added to the listing when a snipe happens, giving others time to answer. `-1` = disabled. |

### Won items

| Option | Default | Meaning |
| --- | --- | --- |
| `instant-collect` | `false` | Deliver won items straight to the winner instead of the storage menu. |
| `collect-purge-delay` | `604800` | Seconds before an uncollected won item is purged. `-1` = never. |
| `max-uncollected` | `10` | How many uncollected wins a player may stack before they must collect to bid again. `-1` = unlimited. |

## notifications

| Option | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch for offline/pending notifications. |
| `join-delay` | `40` ticks (2 s) | Delay after join before pending messages are shown. `-1` = disabled. 20 ticks = 1 second. |
| `repeat-times` | `3` | How many separate joins the "you have uncollected items" reminder is shown on. Resets once the player collects everything. `-1` = always remind. |

## restrictions

### Gamemodes, worlds and items

```yml
restrictions:
  blocked-gamemodes:        # bypass: airauctions.bypass.gamemodes
    - CREATIVE
    - SPECTATOR
  blocked-worlds:           # bypass: airauctions.bypass.worlds
    - "creative_world"
  allow-damaged-items: true # bypass: airauctions.bypass.damaged-items
  max-search-length: 32
```

Blocked gamemodes and worlds prevent **creating listings and placing bids**;
browsing still works.

### Blacklist (or whitelist)

`restrictions.blacklist` stops matching items from being listed. Set
`as-whitelist: true` to invert it - then **only** matching items can be listed.
Staff with `airauctions.bypass.blacklist` ignore it entirely.

An item is matched if it matches **any** rule. All rules are optional; leave a list
empty to skip it.

| Rule | Matches on | Example entries |
| --- | --- | --- |
| `materials` | Bukkit material name | `BEDROCK` |
| `names` | Substring of the display name, ignoring colour codes | `"Soulbound Item"` |
| `lores` | Substring of any lore line, ignoring colour codes | `"Not for Sale"` |
| `enchantments` | `ENCHANT` or `ENCHANT:LEVEL` | `BINDING_CURSE`, `UNBREAKING:3` |
| `nbt-keys` | Presence of an NBT key | `undroppable` |
| `custom-model-data` | `DATA` or `MATERIAL:DATA` | `1005`, `PAPER:1006` |
| `item-models` | Item model key, 1.21.4+ (`namespace:path`) | `mypack:swords/fire` |
| `plugin-items` | Nexo / ItemsAdder / CraftEngine item id | `nexo:ruby`, `itemsadder:pack:ruby`, `craftengine:pack:ruby` |

The same rule syntax is reused for categories in
[data/filter.yml](filter-yml.md), so anything you learn here applies there too.

### Common recipes

**Ban a specific soulbound item everywhere**

```yml
  blacklist:
    as-whitelist: false
    lores:
      - "Soulbound"
```

**Only allow vanilla farming products to be sold**

```yml
  blacklist:
    as-whitelist: true
    materials:
      - WHEAT
      - CARROT
      - POTATO
```
