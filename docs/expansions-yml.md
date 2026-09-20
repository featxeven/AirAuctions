# expansions.yml - Currencies, Fees, Taxes & Discord

Everything money-related, plus the Discord webhook integration. Applies with
`/airauctions reload`.

## economy - display and input

```yml
economy:
  number-format: FORMATTED
  number-format-suffixes: ["", "k", "M", "B", "T", "Q"]
  allow-shorthand-input: true
  decimal-handling: REJECT
  min-price: 1
  max-price: 10000000
```

| Option | Values | Meaning |
| --- | --- | --- |
| `number-format` | `SHORT` (`1.25M`), `FORMATTED` (`1,250,000`), `RAW` (`1250000`) | How amounts are printed everywhere. |
| `number-format-suffixes` | list | Suffixes used by `SHORT`, from smallest to largest. |
| `allow-shorthand-input` | `true`/`false` | Lets players type `1.5k` instead of `1500`. |
| `decimal-handling` | `FLOOR`, `REJECT` | What happens to decimal prices on a currency with `allow-decimals: false`: silently truncate, or refuse the price. |
| `min-price` / `max-price` | number | Price bounds per listing. `max-price: -1` = no cap. |

## economy - purchase checks

```yml
  buy-check: MINIMUM
  buy-amount-trigger: 2
  min-partial-price: 1
```

| Option | Meaning |
| --- | --- |
| `buy-check` | `MINIMUM`: the player only needs enough money for the smallest partial purchase to open the quantity screen. `FULL`: they need the full stack price before the screen opens. |
| `buy-amount-trigger` | Listings with at least this many items open the quantity picker instead of buying the whole stack. Minimum `2`. |
| `min-partial-price` | Smallest total price a partial purchase may cost - prevents players buying items for a rounded-down price of zero. |

## economy - currencies

```yml
  multi-currency: true
  default-currency: "vault"
```

With `multi-currency: false`, only `default-currency` is used and the optional
`[economy]` command argument is ignored.

Each entry under `providers` is one currency. The **key** (`vault`, `exp`, ...) is
what players type in commands and what you use in `default-currency`. The order of
the entries is the cycle order of the currency filter in the menus. The reserved
`all` entry is the "no currency filter" option and can be moved anywhere in the
list to control where it appears.

```yml
  providers:
    all:
      display-name: "All"

    vault:
      enabled: true
      type: VAULT
      key: "Money"                       # shown in command tab-completion
      display-name: "<green>Money</green>"  # used by %economy% placeholders
      format: "<green>$%amount%</green>"    # how amounts of this currency are rendered
      allow-decimals: true
```

| Field | Meaning |
| --- | --- |
| `enabled` | Whether the currency is usable at all. |
| `type` | `VAULT`, `EXP`, `PLAYERPOINTS`, `EXCELLENTECONOMY`, `PLACEHOLDER`. |
| `key` | Friendly name used in tab-completion (`ECONOMY_OPTIONS`). |
| `display-name` | Name shown in menus/messages (MiniMessage formatting allowed). |
| `format` | Template for an amount; `%amount%` is replaced with the formatted number. |
| `allow-decimals` | If `false`, decimal prices are floored or rejected per `decimal-handling`. |
| `settings` | Extra options required by some types (see below). |

| Type | Requires | `settings` |
| --- | --- | --- |
| `VAULT` | Vault + an economy plugin | - |
| `EXP` | nothing - uses player experience | - |
| `PLAYERPOINTS` | PlayerPoints | - |
| `EXCELLENTECONOMY` | ExcellentEconomy | `currency:` must match a currency id in ExcellentEconomy's own config |
| `PLACEHOLDER` | PlaceholderAPI | `balance-placeholder`, `give-command`, `take-command` |

A `PLACEHOLDER` currency lets you wire up any plugin that exposes a balance
placeholder and give/take commands:

```yml
    crystals:
      enabled: false
      type: PLACEHOLDER
      key: "Crystals"
      display-name: "<gold>Crystals</gold>"
      format: "<gold>%amount% Crystals</gold>"
      allow-decimals: false
      settings:
        balance-placeholder: "%mycurrency_balance%"
        give-command: "mycurrency give %player% %amount%"
        take-command: "mycurrency take %player% %amount%"
```

The give/take commands are run from console, so they must work when typed there.

## Listing fee and sales tax

Two independent money sinks:

- **`listing-fee`** - charged to the seller the moment a listing is created (and
  not refunded if the listing expires unsold).
- **`sales-tax`** - deducted from the seller's payout when a sale completes.

```yml
  listing-fee:
    enabled: true
    type: PERCENTAGE   # PERCENTAGE of the asking price, or FIXED flat amount
    default: 5         # 5% (PERCENTAGE) or 5 currency units (FIXED)
    min: 1             # ignored when type is FIXED
    max: 1000          # -1 = no cap; ignored when type is FIXED
```

`sales-tax` uses exactly the same four fields.

Per-rank values are set with permissions rather than extra config entries:

| Permission | Effect |
| --- | --- |
| `airauctions.bypass.fee` | No listing fee at all |
| `airauctions.bypass.fee.<value>` | Use `<value>` instead of `default` |
| `airauctions.bypass.tax` | No sales tax at all |
| `airauctions.bypass.tax.<value>` | Use `<value>` instead of `default` |

Example: give VIPs a 2% fee with `airauctions.bypass.fee.2`.

## Discord webhooks

```yml
discord:
  enabled: false
  webhook-url: ""
  time-format: RELATIVE   # RELATIVE | DATETIME | DATETIME_RELATIVE | TEXT
  events:
    auction-new: { enabled: true, ... }
```

Set `enabled: true`, paste a channel webhook URL, then enable the events you want.
Each event can override `webhook-url` and `time-format` (leave blank to inherit
the global values), which lets you route e.g. admin deletions to a staff channel.

Available events:

| Event | Fires when | Enabled by default |
| --- | --- | --- |
| `listing-deleted` | Staff removes a listing | no |
| `auction-new` | A fixed-price listing is created | yes |
| `auction-sold` | A listing sells completely | yes |
| `auction-partial-sold` | Part of a stack is bought | yes |
| `auction-expired` | A listing expires unsold | no |
| `bid-new` | A bid listing is opened | yes |
| `bid-placed` | Someone places a bid | yes |
| `bid-ended` | A bid listing ends with a winner | yes |
| `bid-expired` | A bid listing ends with no bids | no |

Every event is a full Discord embed you can rewrite: `color` (decimal colour
value), `author`, `title`, `title-url`, `description`, `thumbnail-url`,
`image-url`, a list of `fields` (`name`, `value`, `inline`), `footer` and
`timestamp`.

Placeholders usable in any text field (availability depends on the event):
`%player%`, `%seller%`, `%buyer%`, `%bidder%`, `%winner%`, `%admin%`, `%item%`,
`%amount%`, `%remaining_amount%`, `%price%`, `%bid%`, `%bid_count%`, `%economy%`,
`%fee%`, `%tax%`, `%payout%`, `%expires%`, `%duration%`.

The default `thumbnail-url` uses `https://mc-heads.net/avatar/%player%/64` to show
the player's head; replace it with your own avatar service if you prefer.

Colours are decimal, not hex: `15158332` is `#E74C3C`. Convert a hex colour by
pasting it into any "hex to decimal" converter.
