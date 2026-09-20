# commands.yml - Command Names, Aliases & Tab-Completion

Controls the player-facing command (`/ah` by default): its name, aliases,
subcommand names, usage lines and tab-completion.

> **This file requires a full server restart.** Commands are registered with the
> server at startup, so `/airauctions reload` will not rename them.

The admin command `/airauctions` is fixed and not configured here - see
[Commands & permissions](commands-and-permissions.md).

## Main command

```yml
main:
  name: "auctionhouse"
  aliases: [ah]
  usage: "/%label% [subcommand] [...]"
```

`%label%` is replaced with whatever the player actually typed (`/ah` or
`/auctionhouse`). Add more aliases freely, e.g. `aliases: [ah, auction, market]`.
Avoid aliases that another plugin already registers.

## Shortcuts

Standalone commands that run a full AirAuctions command:

```yml
shortcuts:
  auction:
    runs: "ah sell"
    aliases: []
  bid:
    runs: "ah bid"
    aliases: []
```

With the defaults above, `/auction 500` behaves like `/ah sell 500`. Add your own
by copying a block; remove one to unregister that shortcut.

## Duration units

Suffixes accepted wherever a duration is typed (`/ah bid 500 30m`):

```yml
duration-units:
  t: { ticks: 1 }
  s: { ticks: 20 }
  m: { ticks: 1200 }
  h: { ticks: 72000 }
  d: { ticks: 1728000 }
  w: { ticks: 12096000 }
```

A number typed with **no** suffix uses the unit listed **first** in this section.
With the defaults that is `t` (ticks); if you would rather have bare numbers mean
seconds, move the `s` entry to the top.

## Subcommands

Each entry under `subcommands` configures one player subcommand:

```yml
  sell:
    enabled: true
    name: "sell"
    aliases: []
    usage: "/%label% %sublabel% <price> [amount] [economy]"
    tab-complete:
      1:
        sources: [100, 500, 1000, 5000, 10000]
        requires: airauctions.command.sell
```

| Key | Meaning |
| --- | --- |
| `enabled` | `false` removes the subcommand completely. |
| `name` | What players type. Rename `sell` to `vender` and `/ah vender` works. |
| `aliases` | Extra names for the same subcommand. |
| `usage` | Usage line shown on wrong input. `%label%` = main command, `%sublabel%` = subcommand name. |
| `usage-others` | Alternative usage line shown to staff who may target other players. |
| `tab-complete` | Suggestions per argument position (`1` = first argument after the subcommand). |

The bundled subcommands are `sell`, `bid`, `listings`, `expired`, `history`,
`storage`, `search`, `player`, `delete` and `slots`.

### Tab-complete sources

Each numbered position takes a `sources` list and an optional `requires`
permission (suggestions are hidden from players without it).

| Source | Suggests |
| --- | --- |
| literal values, e.g. `100`, `64` | Those exact strings |
| `ONLINE_PLAYERS:<n>` | Online player names, capped at `<n>` suggestions |
| `ECONOMY_OPTIONS` | Enabled currencies from [expansions.yml](expansions-yml.md) |
| `LISTING_IDS:<n>` | Existing listing ids, capped at `<n>` |
| `DURATION_UNITS:<a, b, c>` | The listed duration units |
| `ACTIONS` | The action names defined on that subcommand (e.g. `give`/`take`) |

`suffix-mode: true` (used by the duration argument of `/ah bid`) appends the unit
to the number the player is typing, so typing `30` suggests `30s`, `30m`, `30h`.

### Renaming action words

`slots` exposes its action words so they can be translated too:

```yml
  slots:
    actions:
      give: "give"
      take: "take"
```

Change the values (not the keys) to translate `/ah slots give 5 Steve` into e.g.
`/ah slots dar 5 Steve`.
