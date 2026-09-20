# Language Files & Animations

All text the plugin sends lives in `lang/`, and reusable animated text in
`data/animations.yml`. Both apply with `/airauctions reload`.

## Choosing a language

```yml
# config.yml
general:
  lang: "en_US"        # loads lang/messages/en_US.yml
  items-lang: "en_US"  # loads lang/items/en_US.yml
```

To translate the plugin, copy `lang/messages/en_US.yml` to
`lang/messages/es_ES.yml`, translate the values, then set `lang: "es_ES"`.

`en_US.yml` is always re-created if missing and acts as the fallback: if your file
is missing a key, the English text is used and a warning naming the missing key is
printed to console. That makes plugin updates safe - new messages keep working
until you translate them.

## lang/messages/&lt;lang&gt;.yml

Grouped into `references`, `general`, `placeholders`, `errors`, `listings`,
auction/bid sections and so on. A value may be a single line or a list of lines
(each list entry is sent as its own line).

### References

Reusable snippets defined at the top of the file:

```yml
references:
  prefix: "<gray>[<gradient:#00ffcc:#0066ff>AirAuctions</gradient>]</gray>"
  url_spigot: "https://www.spigotmc.org/resources/133357/"
```

Use them anywhere with `<ref:prefix>` or the short form `<r:prefix>`. Changing
`prefix` in one place restyles every message.

### Formatting

Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
formatting: `<red>`, `<bold>`, `<gradient:#a:#b>`, `<hover:show_text:'...'>`,
`<click:open_url:'...'>` and so on. Legacy `&`-codes are not the intended format.

`%placeholder%` values (`%player%`, `%price%`, `%amount%`, `%item%`, `%timeout%`,
...) are filled in by the plugin. Keep the ones you need; any placeholder you
delete simply is not shown.

### Extra effect tags

Beyond MiniMessage, these tags can be embedded in any message line:

| Tag | Effect |
| --- | --- |
| `<sound:key:volume:pitch>` | Plays a sound, e.g. `<sound:entity.player.levelup:1:2>` |
| `<actionbar:'text'>` | Sends the text to the action bar instead of chat |
| `<title:'text':fadeIn:stay:fadeOut>` | Shows a title (times in ticks) |
| `<subtitle:'text'>` | Subtitle; inherits the timings of a `<title:...>` on the same message |
| `<bossbar:'text':duration:color:overlay:progress:countdown>` | Shows a temporary boss bar |

A message that contains only a `<sound:...>` tag plays the sound and sends no
chat line - that is how the defaults attach sounds to events:

```yml
    reload:
      - "<ref:prefix> <green>Configuration reloaded (<white>%time%ms</white>)."
      - "<sound:block.note_block.chime:1:2>"
```

### Useful sections

| Section | Contains |
| --- | --- |
| `general.commands` | Admin command output: usage, reload, version, update notice |
| `general.simulate` | Output of `/airauctions simulate` |
| `placeholders` | Shared fragments: `never`, `unlimited`, per-unit price suffix, empty-value fallbacks, and the duration unit words (`second`, `hours`, ...) |
| `errors.access` | Permission, usage and target errors |
| `errors.item` / `errors.economy` / `errors.limits` | Rejection reasons shown to players |
| `listings.*`, `auctions.*`, `bids.*` | Claim/cancel/delete/purchase/bid feedback and broadcasts |

The duration words under `placeholders.time` are what
`formatting.duration` in [config.yml](config-yml.md#formatting) renders with, so
translate them together.

## lang/items/&lt;lang&gt;.yml

A flat map of Minecraft material names to display names:

```yml
diamond_sword: "Diamond Sword"
acacia_door: "Acacia Door"
```

This is what `%item%` prints. Translate the values to localise item names, or
rename a few entries to match your server's theme. Missing entries fall back to
`en_US`.

## data/animations.yml

Named text animations usable anywhere MiniMessage text is accepted - messages,
titles, action bars, boss bars and GUI item names/lore:

```yml
animations:
  rainbow:
    interval: 5        # ticks between frames (20 ticks = 1 second)
    mode: RANDOM       # LOOP | PING_PONG | RANDOM
    frames:
      - "<red>"
      - "<gold>"
      - "<yellow>"
```

Reference an animation with `<anim:rainbow>` or the short form `<a:rainbow>`.

| `mode` | Playback |
| --- | --- |
| `LOOP` | Frames in order, then start over |
| `PING_PONG` | Forward then backwards |
| `RANDOM` | A random frame each interval |

Frames can be colour tags (animating the colour of the text that follows) or whole
words (animating the text itself), as in the bundled `loading_dots` and
`auction_wave` examples.

Keep `interval` at 3 ticks or more; very fast animations refresh menus often and
can look noisy.
