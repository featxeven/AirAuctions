# GUI Framework Reference

This page documents the **generic menu engine** (`core/gui`) that AirAuctions is
built on. Everything here behaves the same in any plugin that ships the
framework - file loading, `settings`, slots, item options, templates and aliases,
the generic actions, flag conditions, `priority` tiers, navigation contexts and
input prompts.

Everything specific to auctions - the bundled menus, `layout` listing rendering,
filters/sorts and the auction actions - is documented separately in
[AirAuctions menus](guis.md).

## Files and ids

- Every `.yml` file under the plugin's `guis/` directory is one menu, loaded
  recursively.
- A menu's **id** is its path under `guis/` without the extension:
  `guis/player/active.yml` → `player/active`.
- `guis/shared.yml` is reserved for shared defaults, aliases and templates.
- `guis/.input/` is reserved for input prompt definitions.
- Missing files are re-extracted from the jar on startup, so deleting a file and
  reloading restores its default.
- A malformed menu file is reported in console and skipped; the other menus keep
  working.

## File structure

```yml
settings:
  title: "<gradient:#5AD1F2:#736EFE><bold>Menu</bold></gradient>"
  rows: 5

items:
  back:
    slots: 0
    material: OAK_DOOR
    display-name: "<white><bold>Back</bold>"
    actions:
      any:
        - "[open] context:true"
        - "[sound] $click"

contexts:
  some/other-gui:
    settings:
      title: "<red>Different title"
```

| Section | Purpose |
| --- | --- |
| `settings` | Size, title and behaviour of the window |
| `items` | Static buttons and decoration |
| `contexts` | Overrides applied depending on where the player came from |
| `layout` | Dynamic content - plugin-specific, see [AirAuctions menus](guis.md) |

## settings

| Key | Meaning |
| --- | --- |
| `title` | Window title; MiniMessage + placeholders |
| `rows` | Inventory height, 1-6. Slots are `0` to `rows*9-1` |
| `enabled` | `false` disables the menu; screens that are optional (such as confirmations) then fall back to their non-GUI flow |
| `trim-lore` | Collapse consecutive blank lore lines |
| `force-reopen` | Re-open instead of refreshing in place - required when the title contains changing placeholders |
| `refresh-interval` | Auto-refresh in ticks; `-1` disables it |
| `input-type` | `CHAT`, `SIGN` (1.20+) or `DIALOG` (1.21.6+) |
| `open-actions` / `close-actions` | Action lists run when the menu opens/closes |

Every key except `title` and `rows` falls back to the `defaults` block in
`shared.yml` when omitted.

## Slots

`slots` accepts a number, a range string, or a list mixing both:

```yml
    slots: 22
    slots: [0-8, 17, 26-35]
    slots: "0-8, 45"
```

Out-of-range slots are skipped with a console warning naming the item and the
inventory size. An item that ends up with no valid slot is never rendered (also
warned about).

## shared.yml

### defaults

Inherited by every menu that does not override the key in its own `settings`:

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

### aliases

Named strings reusable anywhere with `$name`:

```yml
aliases:
  click: "ui.button.click 1 1.2"
  success: "entity.player.levelup 1 2"
```

```yml
        - "[sound] $click"
```

Editing the alias restyles every place that uses it.

### templates

Reusable item definitions. An item with `template: <name>` starts from the
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

An unknown template name is reported in console and the item falls back to its
own fields.

## Item options

Usable on any item and inside templates.

| Key | Meaning |
| --- | --- |
| `material` | Bukkit material, player head, or a hooked plugin item (`nexo:`, `itemsadder:`, `craftengine:`) |
| `display-name` | Item name (MiniMessage + placeholders) |
| `lore` | List of lore lines |
| `amount` | Stack size shown |
| `glow` | Enchantment glint |
| `hide-tooltip` | Hide the tooltip entirely (1.20.5+) |
| `unbreakable` | Mark the item unbreakable |
| `custom-model-data` | Resource-pack model id |
| `item-model` | Item model key (1.21.4+) |
| `tooltip-style` | Tooltip style key (1.21.2+) |
| `item-flags` | Bukkit item flags to hide, e.g. `HIDE_ATTRIBUTES` |
| `enchants` | List of `ENCHANT:LEVEL` |
| `damage` | Durability damage |
| `leather-color` / `potion-color` | Colour for leather armour / potions |
| `cooldown` | Seconds before the same player may click the item again |
| `cooldown-message` | Message shown while on cooldown (`%timeout%`) |
| `actions` | Click actions |
| `animation` | `interval` in ticks, `loop`, and `frames` - each frame a set of item fields |
| `priority` | Conditional appearance |
| `template` | Start from a shared template |

## Click actions

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

Click keys: `left`, `right`, `left-shift`, `right-shift`, `shift`, `drop`,
`control-drop`, `number`, `offhand`, and `any` (every click type not listed
explicitly). Actions run top to bottom.

### Built-in actions

| Action | Syntax / notes |
| --- | --- |
| `[close]` | Closes the menu |
| `[refresh]` | Redraws the menu |
| `[open]` | `[open] gui:<id> [page:N] [target:<player>] [restore:<...>]`, or `[open] context:true` for back navigation |
| `[page]` | `[page] to:previous\|next\|first\|last\|N` |
| `[message]` | Message to the clicker |
| `[broadcast]` | Message to everyone |
| `[player]` | Runs a command as the player |
| `[console]` | Runs a command as console |
| `[sound]` | `[sound] <key> <volume> <pitch>` |
| `[title]` | `[title] 'text' <fadeIn:10> <stay:40> <fadeOut:10>` (ticks) |
| `[subtitle]` | Uses the timings of a `[title]` earlier in the same list |
| `[actionbar]` | Action bar text |
| `[bossbar]` | `[bossbar] 'text' <duration:100> <color:RED> <overlay:PROGRESS> <progress:1.0> <countdown:true>` |

Plugins register additional actions on top of these - for AirAuctions see
[AirAuctions menus](guis.md#auction-actions).

### Navigation parameters

`[open]` and any other forward-navigating action accept the same tokens:

| Token | Effect |
| --- | --- |
| `gui:<id>` | Target menu |
| `page:<N>` | Page to open on |
| `target:<player>` | Whose data the target menu shows |
| `context:true` | Go back to the previous screen instead (cannot be combined with the tokens above) |
| `restore:<...>` | Comma-separated values written back onto the **current** screen before navigating, e.g. `restore:<page:1>` |

Any other `key:value` token is passed through as a plugin-defined dimension
(AirAuctions uses `filter:<dimension>:<value>` and `sort:<dimension>:<value>`).

The engine remembers the chain of screens a player moved through, so
`[open] context:true` returns to the exact previous screen, page and state.

## Flags - conditional action lines

An action line may be prefixed with one or more flags; the line only runs when
every flag is true. `!=` negates a flag, and several may be chained.

```yml
      any:
        - "[buy]"
        - "=can:buy =is:valid [sound] $success"
        - "!=can:buy [message] <red>You cannot afford this."
        - "[open] context:true"
```

The framework defines the **syntax**; the flag keys themselves are supplied by the
plugin. `has:<permission>` is available wherever flags are, and evaluates the
viewer's permissions:

```yml
        - "=has:myplugin.staff [message] <gray>Staff-only hint."
```

For the auction-specific `is:` and `can:` flags see
[AirAuctions menus](guis.md#flag-reference).

## priority - conditional appearance

`priority` is a list of tiers; the first tier whose conditions all pass decides
how the item looks. Tiers may be nested, and fields not set by the winning tier
fall back to the item's own fields.

```yml
  status:
    slots: 4
    material: PAPER
    display-name: "<white>Listings: %current%"
    priority:
      - conditions:
          - "%current% == 0"
        material: BARRIER
        display-name: "<red><bold>Empty"
        lore:
          - "<gray>Nothing here yet."
```

Conditions compare placeholders, numbers and strings with `==`, `!=`, `>`, `>=`,
`<`, `<=`. They support `AND` / `OR` - either as a keyword on its own first line
followed by the comparisons, or inline with parentheses - and arithmetic inside
parentheses:

```yml
        conditions:
          - "OR"
          - "%current% >= (%max% - 1)"
          - "%airauctions_available_slots% == 0"
```

## contexts - one menu, several entry points

A menu that is reachable from several places can override any part of itself
based on where the player came from. The key is the source menu id; `|` chains
multiple navigation steps, and the most specific match wins.

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

Overrides are merged field by field onto the base definition, so you only repeat
what actually changes.

## Input prompts - guis/.input/

How the plugin asks a player to type a value. One file per input method; which
one is used comes from `input-type` (per menu, or from `shared.yml` defaults).

| File | Method | Requires |
| --- | --- | --- |
| `chat.yml` | Typed in chat | any version |
| `sign.yml` | Typed on a sign | 1.20+ |
| `dialog.yml` | Typed in a dialog window | 1.21.6+ |

```yml
on-cancel: BACK    # BACK = reopen the previous menu | CLOSE = do nothing

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

- `on-cancel` is global with a per-context override.
- `chat.yml` additionally defines a `cancel-key` - the word that aborts the
  prompt.
- The available `contexts` are defined by the plugin; AirAuctions uses `search`,
  `quantity`, `bid`, `price` and `initial-price`.

## Debugging

Console warnings name the file, the item key and the field for every problem:
unknown template, out-of-range slot, malformed action, malformed navigation
token, unknown action name. Keep the console open while editing, then run the
plugin's reload command.
