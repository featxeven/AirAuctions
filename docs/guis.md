# GUI Configuration

Every menu is a YAML file under `plugins/AirAuctions/guis/`. You can move items,
change materials and text, add decorative or command buttons, and disable
confirmation screens - all without touching code. Changes apply with
`/airauctions reload`.

## Folder map

| Path | Menu |
| --- | --- |
| `guis/shared.yml` | Defaults, aliases and item templates used by every GUI |
| `guis/browsing/global.yml` | The main marketplace |
| `guis/browsing/categories.yml` | Category selection screen |
| `guis/browsing/search.yml` | Search results |
| `guis/browsing/view_bid.yml` | Bid listing detail with bid history |
| `guis/browsing/view_shulker.yml` | Preview of a shulker box's contents |
| `guis/player/active.yml`, `expired.yml`, `storage.yml`, `history.yml` | The viewer's own listings |
| `guis/target/active.yml`, `expired.yml`, `storage.yml`, `history.yml` | Another player's listings (staff view) |
| `guis/confirm/*.yml` | Confirmation screens: buy, buy_amount, bid, place_bid, auction, cancel, delete, claim, claim_all, collect, collect_all, buy_shulker |
| `guis/.input/chat.yml`, `sign.yml`, `dialog.yml` | How players are prompted to type values |

A GUI's **id** is its path under `guis/` without `.yml` - e.g.
`guis/player/active.yml` has the id `player/active`. Ids are used by
`/airauctions open <gui-id> <player>` and by `[open] gui:<id>` actions.

`shared.yml` and the `.input` folder are reserved. Any other `.yml` file you add
under `guis/` is loaded as an extra GUI and can be opened by id.

## Anatomy of a GUI file

```yml
settings:
  title: "<gradient:#5AD1F2:#736EFE><bold>Marketplace</bold></gradient> <dark_gray>- Page %page%/%pages%"
  rows: 5

layout:
  listing-slots: [9-35]
  listing:
    template: row-market

items:
  filler:
    template: filler
    slots: [1-2, 6-7, 37-38, 42-43]
  back:
    slots: 0
    material: OAK_DOOR
    display-name: "<white><bold>Back</bold>"
    actions:
      any:
        - "[open] gui:browsing/categories"
        - "[sound] $click"
```

- `settings` - size, title and behaviour of the window.
- `layout` - where dynamic content (listings, bidders, shulker contents) is drawn.
- `items` - the static buttons and decoration.
- `contexts` (optional) - overrides applied depending on which menu the player
  came from.

### settings

| Key | Default | Meaning |
| --- | --- | --- |
| `title` | - | Window title, MiniMessage + placeholders |
| `rows` | - | Inventory height, 1-6 (slots are numbered `0` to `rows*9-1`) |
| `enabled` | `true` | On confirmation GUIs, `false` skips the screen and falls back to the chat confirmation (see [config.yml](config-yml.md)) |
| `trim-lore` | from `shared.yml` | Collapse consecutive blank lore lines |
| `force-reopen` | from `shared.yml` | Re-open instead of refreshing in place - needed when the title contains placeholders like `%page%` |
| `refresh-interval` | from `shared.yml` | Auto-refresh in ticks; `-1` = off |
| `input-type` | from `shared.yml` | `CHAT`, `SIGN` (1.20+) or `DIALOG` (1.21.6+) |
| `open-actions` / `close-actions` | from `shared.yml` | Actions run when the menu opens/closes |

### Slots

`slots` accepts a single number, a range, or a list mixing both:

```yml
    slots: 22
    slots: [0-8, 17, 26-35]
    slots: "0-8, 45"
```

Slots outside the inventory are skipped with a console warning, and an item with
no valid slot never renders (also warned about).

## shared.yml - defaults, aliases and templates

### defaults

Values every GUI inherits unless it overrides them in its own `settings`:

```yml
defaults:
  trim-lore: true
  force-reopen: true
  refresh-interval: -1
  input-type: DIALOG
  open-actions:
    - "[sound] ui.toast.in 1 2"
  close-actions:
    - "[sound] ui.toast.out 1 2"
```

`input-type: DIALOG` requires clients on 1.21.6+. Use `SIGN` (1.20+) or `CHAT` if
your players are on older versions.

### aliases

Named strings you can reuse anywhere with `$name`:

```yml
aliases:
  click: "ui.button.click 1 1.2"
  success: "entity.player.levelup 1 2"
```

Used as `- "[sound] $click"`. Changing the alias restyles every button at once.

### templates

Reusable item definitions. A GUI item that sets `template: <name>` starts from the
template and may override any field:

```yml
templates:
  filler:
    material: BLACK_STAINED_GLASS_PANE
    hide-tooltip: true
```

```yml
items:
  filler:
    template: filler
    slots: [0-8]
```

Template naming in the bundled files:

| Prefix | Purpose |
| --- | --- |
| `row-*` | Interactive listing card in a paginated list; carries its own click actions |
| `card-*` | Read-only preview card used in confirmation screens and previews |
| `view-bid-card` | Read-only card that keeps a live "click to bid" action |

## Item options

Any of these can be used on a GUI item or inside a template:

| Key | Meaning |
| --- | --- |
| `material` | Bukkit material, a player head, or a plugin item (`nexo:...`, `itemsadder:...`, `craftengine:...`) |
| `display-name` | Item name (MiniMessage + placeholders) |
| `lore` | List of lore lines |
| `amount` | Stack size shown |
| `glow` | Enchantment glint on/off |
| `hide-tooltip` | Hide the tooltip entirely (1.20.5+) |
| `unbreakable` | Mark the item unbreakable |
| `custom-model-data` | Resource-pack model id |
| `item-model` | Item model key, 1.21.4+ |
| `tooltip-style` | Tooltip style key, 1.21.2+ |
| `item-flags` | Bukkit item flags to hide, e.g. `HIDE_ATTRIBUTES` |
| `enchants` | List of `ENCHANT:LEVEL` |
| `damage` | Durability damage |
| `leather-color` / `potion-color` | Colour for leather armour / potions |
| `cooldown` | Seconds before the same player can click this item again |
| `cooldown-message` | Message shown while on cooldown (`%timeout%` available) |
| `actions` | Click actions (below) |
| `animation` | Animated item: `interval` in ticks, `loop`, and a list of `frames`, each frame being a set of item fields |
| `priority` | Conditional appearance (below) |

### Click actions

```yml
    actions:
      left:
        - "[sound] $click"
        - "[open] gui:player/active page:1"
      right:
        - "[message] <gray>Right click!"
      any:
        - "[refresh]"
```

Click keys: `left`, `right`, `left-shift`, `right-shift`, `shift` (both shift
clicks), `drop`, `control-drop`, `number`, `offhand`, and `any` (used for every
click type not explicitly defined).

General actions available in every GUI:

| Action | Syntax / notes |
| --- | --- |
| `[close]` | Closes the menu |
| `[refresh]` | Redraws the menu |
| `[open]` | `[open] gui:<id> [page:N] [target:<player>] [restore:<...>]`, or `[open] context:true` to go back |
| `[page]` | `[page] to:previous\|next\|first\|last\|N` |
| `[message]` | Sends a message to the clicker |
| `[broadcast]` | Sends a message to everyone |
| `[player]` | Runs a command as the player |
| `[console]` | Runs a command as console |
| `[sound]` | `[sound] <key> <volume> <pitch>` |
| `[title]` | `[title] 'text' <fadeIn:10> <stay:40> <fadeOut:10>` (ticks) |
| `[subtitle]` | Uses the timings of a `[title]` earlier in the same list |
| `[actionbar]` | Action bar text |
| `[bossbar]` | `[bossbar] 'text' <duration:100> <color:RED> <overlay:PROGRESS> <progress:1.0> <countdown:true>` |

Auction-specific actions:

| Action | Purpose |
| --- | --- |
| `[buy]`, `[bid]`, `[place_bid]`, `[list]` | Purchase, bid, open the bid flow, create the listing |
| `[cancel]`, `[delete]` | Cancel own listing / staff delete |
| `[claim]`, `[claim_all]`, `[collect]`, `[collect_all]` | Reclaim expired items and won items |
| `[filter]` | `[filter] by:category\|type\|economy to:next\|previous\|first\|last\|N` |
| `[sort]` | `[sort] by:active\|unclaimed\|history to:next\|previous\|first\|last\|N` |
| `[search]` | Opens the search prompt / applies a query |
| `[amount]` | `[amount]` opens the value prompt, `[amount] +10` / `[amount] -1` adjusts it |
| `[view_bid]`, `[view_shulker]` | Open the bid-detail / shulker-preview screens |

`[buy]`, `[cancel]`, `[delete]`, `[claim]`, `[collect]`, `[claim_all]` and
`[collect_all]` route through their confirmation GUI when that file has
`enabled: true`.

### Conditional lines (flags)

An action line may be prefixed with one or more flags; the line only runs when all
of them are true. `!=` negates.

```yml
      any:
        - "[buy]"
        - "=can:buy =is:valid [sound] $success"
        - "!=can:buy [message] <red>You cannot afford this."
        - "[open] context:true"
```

Flags describe the clicked listing and the viewer (affordability, ownership,
validity, availability). The bundled files are the best reference for which flag
fits which screen - copy the line from a comparable menu.

### priority - conditional appearance

`priority` tiers change how an item looks depending on live values. The first tier
whose conditions all pass wins; tiers can be nested.

```yml
  no-listings:
    priority:
      - conditions:
          - "%current% == 0"
        material: BARRIER
        display-name: "<red><bold>Empty"
        lore:
          - "<gray>No listings in this GUI yet."
```

Conditions compare placeholders and numbers with `==`, `!=`, `>`, `>=`, `<`, `<=`,
support `AND` / `OR` (either as a prefix - `AND` on the first line followed by the
comparisons - or inline with parentheses), and allow arithmetic inside
parentheses.

## Layout - dynamic content

The `layout` section controls generated content rather than fixed buttons.

| Key | Meaning |
| --- | --- |
| `listing-slots` | Slots the listings are drawn into (pagination is based on how many slots this is) |
| `listing` | How each listing is rendered - a template reference, or named variants |
| `bidder-slots` / `bidder` | Where and how bid history entries are drawn (`browsing/view_bid.yml`) |
| `shulker-slots` | Where shulker contents are drawn (`browsing/view_shulker.yml`) |
| `available-slots` | Optional `enabled` + item shown for a player's remaining listing slots |

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
    excluded: []            # active
    format:
      active:
        selected: "<aqua>> <white>%name%"
        unselected: "<dark_gray>  %name%"
```

- `excluded` removes a filter/sort dimension from that menu.
- `format` styles the list rendered by the `%filter_<dim>_list%` and
  `%sort_<dim>_list%` placeholders used in the button lore.

The options themselves come from elsewhere: categories from
[data/filter.yml](filter-yml.md), currencies from
[expansions.yml](expansions-yml.md), listing types and sort modes from
[config.yml](config-yml.md).

## Contexts - one GUI, several entry points

Confirmation screens are shared by many menus. `contexts` overrides fields based
on which GUI the player came from; the key is the source GUI id, and `|` chains
several steps of navigation. The most specific match wins.

```yml
contexts:
  target/active:
    settings:
      title: "<red>Delete %seller%'s listing"
    items:
      confirm:
        lore:
          - "<gray>This removes another player's listing."
```

## Input prompts - guis/.input/

How players type values (search query, price, quantity, bid).

- `chat.yml` - typed in chat; `cancel-key` is the word that aborts.
- `sign.yml` - typed on a sign (1.20+).
- `dialog.yml` - typed in a dialog window (1.21.6+, recommended).

Which one is used comes from `input-type` in `shared.yml` (or a GUI's own
`settings`). All three files define the same `contexts`: `search`, `quantity`,
`bid`, `price`, `initial-price`.

```yml
on-cancel: BACK    # BACK = reopen the previous GUI | CLOSE = do nothing

contexts:
  search:
    title: "Search"
    input:
      label: "Item name"
      max-length: 32
    buttons:
      submit: { text: "<green>Search", width: 100 }
      cancel: { text: "<red>Cancel", width: 100 }
```

`on-cancel` is global with a per-context override.

## Practical tips

- Test layout changes with `/airauctions open <gui-id> <player>` - it opens any
  GUI by id, optionally with a category, type, currency, sort and target.
- Console warnings name the file, item key and field for every mistake
  (unknown template, out-of-range slot, malformed action), so keep the console
  open while editing.
- A broken GUI file does not break the others: it is reported and skipped.
- To reset a GUI, delete the file and run `/airauctions reload` - the default is
  written back from the jar.
