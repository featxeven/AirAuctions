# Troubleshooting

Start with the console: AirAuctions names the file, the section and the key for
almost every configuration problem.

## The plugin does not enable

| Symptom in console | Cause | Fix |
| --- | --- | --- |
| `Unsupported API version` / plugin not loading | Server older than 1.21 | Update the server, or use a build that matches your version |
| YAML parse error naming a file | A broken edit in that file | Fix the indentation/quotes, or delete the file and reload to regenerate it |
| Database connection error | Wrong credentials/host in `storage.yml`, or the database is unreachable | Correct [storage.yml](storage-yml.md) and restart |

A single malformed GUI file does not stop the plugin - it is reported and skipped.
A malformed core config file does.

## Changes do not take effect

1. Did you run `/airauctions reload`?
2. Some things require a **full restart**: command names and aliases from
   `commands.yml`, and any database/Redis change in `storage.yml`. See the table
   in [installation.md](installation.md#reload-vs-restart).
3. Check the console for a warning about the key you edited - unknown keys are
   ignored.

## Economy problems

| Symptom | Likely cause |
| --- | --- |
| `No economy provider found` | Vault or the economy plugin is missing, or `type:` in [expansions.yml](expansions-yml.md) points at a plugin you do not run |
| Prices show `0` or odd formatting | `decimals` / number format in `expansions.yml`; providers that do not allow decimals round the amount |
| Players cannot afford anything | A currency is configured against the wrong provider - verify the currency id used by `/ah sell <price> [economy]` |
| Sellers get less than expected | `sales-tax` (and `listing-fee`) in `expansions.yml`; grant `airauctions.bypass.tax` to test |

## Players cannot sell

Check in this order:

1. **Permission** - they need `airauctions.command.sell` (usually via
   `airauctions.use`).
2. **Limit** - `listings.max-active` in [config.yml](config-yml.md), plus any
   `airauctions.bypass.limit.<n>` tier and bonus slots.
3. **Cooldown** - `listings.cooldown`.
4. **Restrictions** - blocked gamemode, blocked world, damaged item, or the item
   blacklist. The rejection message tells you which one.
5. **Price bounds** - `min-price` / `max-price` in `expansions.yml`.

Grant yourself `airauctions.bypass.*` temporarily to confirm which rule is
firing.

## Menu problems

| Symptom | Fix |
| --- | --- |
| An item does not appear | Its `slots` are outside the `rows` of the menu, or missing entirely - the console warns about both |
| Title placeholders never update | Set `force-reopen: true` for that GUI (or in `shared.yml` defaults) |
| A button does nothing | The action name is misspelled, or a flag prefix (`=can:buy`) is not satisfied - console warns on unknown actions |
| The category filter is empty | The category has no `match-rules` in [data/filter.yml](filter-yml.md), or it is listed under `layout.filters.excluded` |
| Input prompts do not open | `input-type: DIALOG` needs clients on 1.21.6+; use `SIGN` or `CHAT` instead |

To reset any GUI to its default, delete the file and run `/airauctions reload`.
Details: [AirAuctions menus](guis.md) for the auction-specific parts,
[GUI framework reference](gui-framework.md) for settings, slots, actions and
conditions.

## Multi-server issues

| Symptom | Cause |
| --- | --- |
| Listings created on one server are missing on another | The servers use different databases, or one is still on SQLite - all must share MySQL/MariaDB/MongoDB |
| Listings appear only after a relog or delay | Redis is not enabled; without it each server only refreshes its cache periodically |
| Strange duplicate or overwritten data | Two servers share the same `server-id`; each needs its own |

Never copy `storage.yml` between servers unchanged - see
[storage.yml](storage-yml.md).

## Messages look wrong

- Raw tags such as `<gray>` printed in chat: the message is being passed through
  another plugin that strips or escapes MiniMessage. Check chat-formatting
  plugins.
- Missing message with a console warning naming a key: your language file is
  missing that key. It falls back to `en_US`; copy the key over from
  `lang/messages/en_US.yml`.
- Wrong dates/times: `formatting.timezone` and `formatting.date` in
  [config.yml](config-yml.md).

## Performance

- Prefer MySQL/MariaDB over SQLite for busy or networked servers.
- Keep GUI `refresh-interval` at `-1` unless a menu really needs live updates.
- Keep animation `interval` values at 3 ticks or higher.
- Reduce `max-history` and the purge delays in `config.yml` if the database grows
  large.

## Before asking for help

Collect:

1. Server version and type (Paper, Folia, ...), and `/airauctions version`.
2. The console output from startup, and the error itself.
3. The config file section you changed.

Then open an issue at
<https://github.com/featxeven/AirAuctions/issues>.
