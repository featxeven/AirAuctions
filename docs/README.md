# AirAuctions - Server Administrator Guide

Documentation for **server owners and administrators** who install and configure
AirAuctions. No Java or plugin-development knowledge is required - everything here
is done by editing YAML files inside `plugins/AirAuctions/` and running in-game
commands.

## Start here

| Guide | What it covers |
| --- | --- |
| [Installation & file layout](installation.md) | Requirements, first start, which file does what, when a reload is enough and when a restart is required |
| [config.yml](config-yml.md) | Core behaviour: limits, lifetimes, confirmations, sorting, restrictions and the item blacklist |
| [storage.yml](storage-yml.md) | Database (SQLite / MySQL / MariaDB / MongoDB), Redis, multi-server networks |
| [expansions.yml](expansions-yml.md) | Currencies, listing fees, sales tax, Discord webhooks |
| [commands.yml](commands-yml.md) | Renaming commands, aliases, shortcuts, tab-completion, duration units |
| [data/filter.yml](filter-yml.md) | Item categories shown in the browse menu |
| [Language & animations](language-and-animations.md) | Translating messages, item names, text animations |
| [GUI framework reference](gui-framework.md) | The generic menu engine shared with other projects: settings, slots, items, templates, actions, flags, conditions, input prompts |
| [AirAuctions menus](guis.md) | What is specific to this plugin: which file is which screen, listing layouts, filters/sorts, auction actions |
| [Commands & permissions](commands-and-permissions.md) | Every command and permission node, with recommended setups |
| [Placeholders](placeholders.md) | PlaceholderAPI placeholders for scoreboards, holograms and chat |
| [Troubleshooting](troubleshooting.md) | Common errors and how to fix them |

## The 5-minute setup

1. Drop the jar in `plugins/` and start the server once so all files are generated.
2. Open `plugins/AirAuctions/config.yml` and set the listing limits, lifetimes and
   restrictions you want. See [config.yml](config-yml.md).
3. Open `plugins/AirAuctions/expansions.yml` and enable the currencies your server
   uses, plus the listing fee and sales tax. See [expansions.yml](expansions-yml.md).
4. If you run more than one server, configure the shared database and Redis in
   `plugins/AirAuctions/storage.yml`. See [storage.yml](storage-yml.md).
5. Give players `airauctions.use` and staff `airauctions.admin`. See
   [Commands & permissions](commands-and-permissions.md).
6. Run `/airauctions reload` after config edits (some files need a restart - the
   table in [Installation](installation.md#reload-vs-restart) says which).

> Official online documentation: <https://airdevelopment.gitbook.io/docs/>
