[![AirAuctions Preview](https://img.youtube.com/vi/cpcWJ-uAaOc/maxresdefault.jpg)](https://www.youtube.com/watch?v=cpcWJ-uAaOc)
 
# Requirements
 
| Requirement | Notes |
| --- | --- |
| Server software | Paper (or a Paper fork). Folia is supported. |
| Minecraft version | API version `1.21` or newer |
| Java | The version required by your Paper build (Java 21+ for 1.21) |
 
# Optional plugins
 
| Plugin | Used for |
| --- | --- |
| Vault + an economy plugin | The `VAULT` currency type (server money) |
| PlaceholderAPI | The `%airauctions_...%` placeholders and `PLACEHOLDER` currencies |
| PlayerPoints | The `PLAYERPOINTS` currency type |
| ExcellentEconomy | The `EXCELLENTECONOMY` currency type |
| Nexo, ItemsAdder, CraftEngine | Custom items in GUIs and in the blacklist/categories |
 
# First start
 
1. Stop the server, place `AirAuctions.jar` in `plugins/`, start the server.
2. All configuration files are written to `plugins/AirAuctions/` on first start.
3. Stop the server again before editing `storage.yml` (database settings are only
   read at startup), then start it and configure the rest live with
   `/airauctions reload`.
