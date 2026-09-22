# AirAuctions Menus

The menus in `plugins/AirAuctions/guis/` are built on the generic menu engine.
This page covers only what is **specific to AirAuctions**: which file is which
screen, how listings are rendered, the filter/sort cyclers, the auction actions
and the auction flags.

For the parts that are the same in every plugin using the framework - `settings`,
slots, item options, templates and aliases, the generic actions, `priority`
conditions, `contexts` and input prompts - see the
[GUI framework reference](gui-framework.md).

Changes apply with `/airauctions reload`.

## Menu map

| File | Screen |
| --- | --- |
| `guis/shared.yml` | Defaults, aliases and item templates for every menu |
| `guis/browsing/global.yml` | The main marketplace |
| `guis/browsing/categories.yml` | Category selection screen |
| `guis/browsing/search.yml` | Search results |
| `guis/browsing/view_bid.yml` | Bid listing detail with bid history |
| `guis/browsing/view_shulker.yml` | Preview of a shulker box's contents |
| `guis/player/active.yml`, `expired.yml`, `storage.yml`, `history.yml` | The viewer's own listings |
| `guis/target/active.yml`, `expired.yml`, `storage.yml`, `history.yml` | Another player's listings (staff view) |
| `guis/confirm/buy.yml`, `buy_amount.yml`, `buy_shulker.yml` | Purchase flow |
| `guis/confirm/auction.yml`, `bid.yml` | Seller confirmation before a listing goes live |
| `guis/confirm/place_bid.yml` | Bid amount confirmation |
| `guis/confirm/cancel.yml`, `delete.yml` | Cancel own listing / staff delete |
| `guis/confirm/claim.yml`, `claim_all.yml`, `collect.yml`, `collect_all.yml` | Reclaiming expired and won items |
| `guis/.input/chat.yml`, `sign.yml`, `dialog.yml` | How players are prompted to type values |

Menu ids are the path without `.yml` (`player/active`), which is what
`/airauctions open <gui-id> <player>` and `[open] gui:<id>` expect.

### Disabling a confirmation screen

Every `confirm/*.yml` has `settings.enabled`. Setting it to `false` skips the
menu; whether the action then happens instantly or via a chat confirmation
depends on the matching option in [config.yml](config-yml.md) (for example
`require-delete-confirmation`).

## layout - dynamic content

`layout` is the AirAuctions-specific section. It controls generated content
(listings, bid history, shulker contents) rather than fixed buttons.

| Key | Meaning |
| --- | --- |
| `listing-slots` | Slots listings are drawn into - the count determines the page size |
| `listing` | How a listing is rendered |
| `bidder-slots` / `bidder` | Where and how bid history rows are drawn (`browsing/view_bid.yml`) |
| `shulker-slots` | Where shulker contents are drawn (`browsing/view_shulker.yml`) |
| `available-slots` | `enabled` + an item template showing the viewer's remaining listing slots |
| `filters` / `sorts` | The cyclers, below |

### Rendering listings

Either one template for every listing:

```yml
layout:
  listing-slots: [9-35]
  listing:
    template: row-market
```

Or named variants, one per row type - the menu only renders the types it defines:

```yml
layout:
  listing-slots: [9-35]
  listing:
    purchase:
      template: row-history-purchase
    sale:
      template: row-history-sale
```

A listing block accepts the same fields as any item, so you can override the
template inline (`display-name`, `lore`, `actions`, ...).

Template naming in the bundled files:

| Prefix | Purpose |
| --- | --- |
| `row-*` | Interactive listing card in a paginated list; carries its own click actions |
| `card-*` | Read-only preview card used in confirmations and previews |
| `view-bid-card` | Read-only card that keeps a live "click to bid" action |

### Filters and sorting

```yml
layout:
  filters:
    excluded: []            # category, type, economy
    format:
      category:
        selected: "<aqua>> <white>%name% <gray>(%count%)"
        unselected: "<dark_gray>  %name% <gray>(%count%)"
  sorts:
    excluded: []            # active, unclaimed, history
    format:
      active:
        selected: "<aqua>> <white>%name%"
        unselected: "<dark_gray>  %name%"
```

- `excluded` removes a dimension from that menu entirely.
- `format` styles the option list rendered by the `%filter_<dim>_list%` and
  `%sort_<dim>_list%` placeholders used in the button lore.

The options themselves come from elsewhere: categories from
[data/filter.yml](filter-yml.md), currencies from
[expansions.yml](expansions-yml.md), listing types and sort modes from
[config.yml](config-yml.md). Adding a category there makes it appear in the
cycler without any menu edit.

## Auction actions

In addition to the [generic actions](gui-framework.md#built-in-actions):

| Action | Purpose |
| --- | --- |
| `[buy]` | Purchase the clicked listing |
| `[bid]` | Start the bid flow on the clicked listing |
| `[place_bid]` | Submit the bid currently being composed |
| `[list]` | Create the listing being previewed (seller confirmation) |
| `[cancel]` | Cancel the viewer's own listing |
| `[delete]` | Staff deletion of any listing |
| `[claim]` / `[claim_all]` | Reclaim expired items |
| `[collect]` / `[collect_all]` | Collect bought/won items from storage |
| `[view_bid]` | Open the bid detail screen |
| `[view_shulker]` | Open the shulker preview |
| `[search]` | `[search]` prompts for a query; accepts navigation tokens plus `query:<text>` |
| `[filter]` | `[filter] by:category\|type\|economy to:next\|previous\|first\|last\|N` |
| `[sort]` | `[sort] by:active\|unclaimed\|history to:next\|previous\|first\|last\|N` |
| `[amount]` | `[amount]` opens the value prompt; `[amount] +10` / `[amount] -1` adjusts it |

`[buy]`, `[cancel]`, `[delete]`, `[claim]`, `[claim_all]`, `[collect]` and
`[collect_all]` route through their `confirm/` menu when that file has
`enabled: true`, and execute immediately when it does not.

`[filter]`, `[sort]` and `[search]` also accept the navigation tokens of
`[open]`, so a button can filter and jump to another menu in one line:

```yml
        - "[filter] by:category to:next restore:<page:1>"
        - "[search] gui:browsing/search restore:<page:1>"
```

## Flag reference

Flags prefix an action line and gate it - see
[flag syntax](gui-framework.md#flags---conditional-action-lines). AirAuctions
resolves these keys against the clicked listing and the viewer:

| Flag | True when |
| --- | --- |
| `has:<permission>` | The viewer has that permission |
| `is:valid` | The listing still belongs to the screen it is shown on (not sold/expired in the meantime) |
| `is:owned` | The viewer is the seller - or, in bid screens, the current highest bidder |
| `is:auction` / `is:bid` | The listing is a fixed-price / bid listing |
| `is:shulker` | The listed item is a shulker box |
| `can:buy` | The viewer may buy it right now (price, stock, self-purchase rules, funds) |
| `can:bid` | The viewer may bid right now |
| `can:cancel` | The viewer may cancel it |
| `can:claim` | The row is in the expired screen and is reclaimable |
| `can:collect` | The row is in the storage screen and is collectable |
| `can:list` | The previewed draft passes every check (limits, restrictions, price bounds, fee) |

`can:*` is always false when there is no listing under the cursor, so
`!=can:buy` is the natural way to render an "unavailable" variant.

## Testing your changes

- `/airauctions open <gui-id> <player> [category] [type] [economy] [sort] [target]`
  opens any menu in any state without having to reproduce it in-game.
- Delete a file and run `/airauctions reload` to restore its default.
- Console warnings name the file, item and field for every mistake; a broken menu
  file is skipped without affecting the others.
