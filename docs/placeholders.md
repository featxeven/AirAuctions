# PlaceholderAPI Placeholders

If [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) is installed,
AirAuctions registers the identifier `airauctions`. Use these anywhere PAPI works:
scoreboards, holograms, tab lists, chat formats and other plugins' menus.

No configuration is needed - the expansion registers itself on startup.

## Listing counts

| Placeholder | Returns |
| --- | --- |
| `%airauctions_max_listings%` | The player's active-listing limit (`-1` = unlimited) |
| `%airauctions_available_slots%` | Remaining listing slots for the player |
| `%airauctions_listings_active%` | The player's active listings |
| `%airauctions_listings_expired%` | The player's expired listings |
| `%airauctions_listings_storage%` | Items waiting in the player's storage |
| `%airauctions_listings_total%` | All of the player's listings |
| `%airauctions_global_listings_active%` | Active listings server-wide |
| `%airauctions_global_listings_expired%` | Expired listings server-wide |
| `%airauctions_global_listings_storage%` | Storage entries server-wide |
| `%airauctions_global_listings_total%` | All listings server-wide |

## Economy statistics

```
%airauctions_spent_<period>[_<currency>][_plain]%
%airauctions_earned_<period>[_<currency>][_plain]%
%airauctions_volume_<period>[_<currency>][_plain]%
```

- `spent` - what the player has paid, `earned` - what the player has received,
  `volume` - the whole server's trade turnover.
- `<period>` is `daily`, `weekly`, `monthly`, `yearly` or `total`. Periods start at
  midnight (Monday / 1st / Jan 1st) in the `formatting.timezone` set in
  [config.yml](config-yml.md#formatting).
- `<currency>` is a currency id from [expansions.yml](expansions-yml.md); omit it
  to use the default currency.
- `_plain` strips colour formatting - use it when the receiving plugin cannot
  handle MiniMessage tags.

```
%airauctions_earned_weekly%
%airauctions_spent_total_points%
%airauctions_volume_daily_vault_plain%
```

## GUI state

Only meaningful while the player has an AirAuctions menu open; they return an
empty string otherwise. Mostly used inside GUI item names and lore.

| Placeholder | Returns |
| --- | --- |
| `%airauctions_gui_open%` | `true` while a menu is open |
| `%airauctions_gui_id%` | Current GUI id, e.g. `browsing/global` |
| `%airauctions_gui_previous_id%` | The GUI the player came from |
| `%airauctions_gui_page%` / `%airauctions_gui_pages%` | Current page / page count |
| `%airauctions_gui_has_next_page%` / `%airauctions_gui_has_previous_page%` | `true`/`false` |
| `%airauctions_gui_target%` / `%airauctions_gui_target_name%` | UUID / name of the player being viewed |
| `%airauctions_gui_search%` | Active search query |
| `%airauctions_gui_filter_category%` | Display name of the selected category |
| `%airauctions_gui_filter_type%` | Display name of the selected listing type |
| `%airauctions_gui_filter_economy%` | Display name of the selected currency |
| `%airauctions_gui_sort_active%` | Display name of the active-listing sort mode |
| `%airauctions_gui_sort_unclaimed%` | Sort mode in expired/storage menus |
| `%airauctions_gui_sort_history%` | Sort mode in the history menu |

Each `filter_*` and `sort_*` placeholder has an `_id` variant
(`%airauctions_gui_filter_category_id%`) that returns the raw config key instead
of the display name - useful for conditions in
[GUI priority tiers](gui-framework.md#priority---conditional-appearance).

## Inside AirAuctions GUIs

GUI files also accept **internal** placeholders that do not need PlaceholderAPI -
`%page%`, `%pages%`, `%seller%`, `%price%`, `%item%`, `%current%`,
`%filter_category_list%` and so on. The bundled GUI files show which ones are
available on which screen. PAPI placeholders from *other* plugins work in those
files too, as long as PlaceholderAPI is installed.
