[![AirAuctions Preview](https://img.youtube.com/vi/cpcWJ-uAaOc/maxresdefault.jpg)](https://www.youtube.com/watch?v=cpcWJ-uAaOc)

# Installation
 
## Requirements
 
| Requirement | Notes |
| --- | --- |
| Server software | Paper (or a Paper fork). Folia is supported. |
| Minecraft version | API version `1.21` or newer |
| Java | The version required by your Paper build (Java 21+ for 1.21) |
 
### Optional plugins
 
| Plugin | Used for |
| --- | --- |
| Vault + an economy plugin | The `VAULT` currency type (server money) |
| PlaceholderAPI | The `%airauctions_...%` placeholders and `PLACEHOLDER` currencies |
| PlayerPoints | The `PLAYERPOINTS` currency type |
| ExcellentEconomy | The `EXCELLENTECONOMY` currency type |
| Nexo, ItemsAdder, CraftEngine | Custom items in GUIs and in the blacklist/categories |
 
## First start
 
1. Stop the server, place `AirAuctions.jar` in `plugins/`, start the server.
2. All configuration files are written to `plugins/AirAuctions/` on first start.
3. Stop the server again before editing `storage.yml` (database settings are only
   read at startup), then start it and configure the rest live with
   `/airauctions reload`.
 
### What each file controls
 
| File | Controls
| --- | --- |
| `config.yml` | Listing limits, cooldowns, expiry, sniper protection, notifications, blacklist |
| `expansions.yml` | Currencies and their display, listing fee, sales tax, Discord embeds |
| `storage.yml` | Where data is stored and how servers share it |
| `commands.yml` | The `/ah` command name, aliases, subcommand names, tab-complete |
| `data/filter.yml` | The categories players can filter by |
| `data/animations.yml` | Named animated text snippets usable anywhere |
| `lang/**` | Every piece of text the plugin sends, plus item name translations |
| `guis/**` | Every GUI: size, title, slots, items, click actions |
