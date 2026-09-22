# data/filter.yml - Item Categories

Defines the categories players can browse and filter by in the marketplace
(Gear, Blocks, Food, ...). Applies with `/airauctions reload`.

## Structure

```yml
all:
  display-name: "All"

gear:
  display-name: "Gear"
  match-rules:
    materials:
      - diamond_sword
      - netherite_chestplate
```

- Each top-level key is one category. The **order of the sections is the cycle
  order** in the filter menu.
- `all` is reserved: it matches everything and represents the "no filter" option.
  You may rename its `display-name` or move it, but keep the section.
- Every other section is yours to add, rename or delete.
- Category names support MiniMessage formatting, e.g.
  `display-name: "<gold>Rare Loot</gold>"`.

## match-rules

`match-rules` uses the same matching syntax as the blacklist in
[config.yml](config-yml.md#blacklist-or-whitelist). An item belongs to the
category when it matches **any** rule.

| Rule | Matches on | Example |
| --- | --- | --- |
| `materials` | Bukkit material name (case-insensitive) | `diamond_sword` |
| `names` | Substring of the display name, ignoring colours | `"Legendary"` |
| `lores` | Substring of any lore line, ignoring colours | `"Season 3"` |
| `enchantments` | `ENCHANT` or `ENCHANT:LEVEL` | `SHARPNESS:5` |
| `nbt-keys` | Presence of an NBT key | `custom_gear` |
| `custom-model-data` | `DATA` or `MATERIAL:DATA` | `1005`, `PAPER:1006` |
| `item-models` | Item model key, 1.21.4+ | `mypack:swords/fire` |
| `plugin-items` | Nexo / ItemsAdder / CraftEngine id | `nexo:ruby` |

## Adding a category

```yml
custom_gear:
  display-name: "<light_purple>Custom Gear"
  match-rules:
    plugin-items:
      - "nexo:ruby_sword"
      - "itemsadder:mypack:ruby_helmet"
    lores:
      - "Custom Forged"
```

After a reload the category appears in the filter cycle of every browse menu. No
GUI edit is required - the filter button cycles whatever is defined here.

## Notes and gotchas

- An item can match several categories; it then shows up under each of them.
- Items that match no category are still listed and still appear under `all` -
  they are just not reachable through a specific category filter.
- Categories are a **display filter only**; they do not control what may be sold.
  Use the blacklist in [config.yml](config-yml.md) for that.
- Individual browse menus can hide specific filters via
  `layout.filters.excluded` - see [AirAuctions menus](guis.md#filters-and-sorting).
- The bundled file is long because it lists vanilla materials one by one. Keeping
  it as a reference and editing in place is usually easier than starting over.
