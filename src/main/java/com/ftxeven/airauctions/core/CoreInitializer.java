package com.ftxeven.airauctions.core;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.api.AirAuctionsPAPIExpansion;
import com.ftxeven.airauctions.command.CoreCommand;
import com.ftxeven.airauctions.command.MainCommand;
import com.ftxeven.airauctions.core.hook.HookManager;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.core.economy.EconomyManager;
import com.ftxeven.airauctions.listener.GuiListener;
import com.ftxeven.airauctions.listener.PlayerLifecycleListener;
import com.ftxeven.airauctions.config.*;
import com.ftxeven.airauctions.util.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.command.*;
import org.bukkit.plugin.PluginManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CoreInitializer {

    private final AirAuctions plugin;

    public CoreInitializer(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.setSchedulerUtil(new SchedulerUtil(plugin));
        logServerType();

        plugin.setConfigManager(new ConfigManager(plugin));
        plugin.setLangManager(new LangManager(plugin));
        plugin.setItemTranslationManager(new ItemTranslationManager(plugin));

        HookManager hookManager = new HookManager();
        plugin.setHookManager(hookManager);

        plugin.setFilterManager(new FilterManager(plugin));

        DatabaseManager db = new DatabaseManager(plugin);
        try {
            db.init();
            plugin.setDatabaseManager(db);
        } catch (Exception e) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        if (!setupEconomy()) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        CoreManager core = new CoreManager(plugin);
        plugin.setCoreManager(core);
        core.startServices();

        registerListeners();
        registerCommands();
        setupUtilities();

        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AirAuctionsPAPIExpansion(plugin).register();
        }

        checkUpdates();
    }

    private void checkUpdates() {
        plugin.scheduler().runAsync(() -> {
            try (InputStream is = URI.create("https://api.spiget.org/v2/resources/133357/versions/latest").toURL().openStream();
                 InputStreamReader reader = new InputStreamReader(is)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("name")) {
                    String latest = json.get("name").getAsString();
                    String current = plugin.getPluginMeta().getVersion();
                    if (!current.equalsIgnoreCase(latest)) {
                        plugin.setLatestVersion(latest);
                        plugin.getLogger().warning("A new update is available! Current: " + current + " | Latest: " + latest);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void logServerType() {
        String type = plugin.scheduler().isFoliaServer() ? "Folia (Region-based)" : "Paper/Spigot (Standard)";
        plugin.getLogger().info("Server is running " + type);
    }

    public boolean setupEconomy() {
        EconomyManager economyManager = new EconomyManager(plugin);
        plugin.setEconomyManager(economyManager);

        String defaultId = plugin.config().economyDefaultProvider();

        if (economyManager.getProvider(defaultId) instanceof EconomyManager.NullProvider) {
            plugin.getLogger().severe("The default provider '" + defaultId + "' is missing or not loaded!");
            return false;
        }
        return true;
    }

    public void registerCommands() {
        regMain(new CoreCommand(plugin));
        regDynamic("auctionhouse", new MainCommand(plugin));
    }

    private void regMain(CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand("airauctions");
        if (cmd == null) return;
        cmd.setExecutor(executor);
        if (executor instanceof TabCompleter tc) cmd.setTabCompleter(tc);
        cmd.setUsage("/airauctions <reload|version>");
    }

    private void regDynamic(String internalName, CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(internalName);
        if (cmd == null) return;

        cmd.setExecutor(executor);
        if (executor instanceof TabCompleter tc) cmd.setTabCompleter(tc);

        String customName = plugin.config().getCommandName("main", internalName);
        String usage = plugin.config().getMainUsage("main", customName);
        if (usage != null) cmd.setUsage(usage);

        List<String> aliases = new ArrayList<>(plugin.config().getCommandAliases("main"));
        aliases.removeIf(a -> a == null || a.isBlank());

        try {
            Map<String, Command> knownCommands = getKnownCommands();
            String prefix = plugin.getName().toLowerCase() + ":";

            List<String> allTriggers = new ArrayList<>(aliases);
            allTriggers.add(customName);

            for (String trigger : allTriggers) {
                knownCommands.put(trigger.toLowerCase(), cmd);
                knownCommands.put(prefix + trigger.toLowerCase(), cmd);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands() throws Exception {
        Field f = plugin.getServer().getClass().getDeclaredField("commandMap");
        f.setAccessible(true);
        SimpleCommandMap map = (SimpleCommandMap) f.get(plugin.getServer());
        Field k = SimpleCommandMap.class.getDeclaredField("knownCommands");
        k.setAccessible(true);
        return (Map<String, Command>) k.get(map);
    }

    public void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new PlayerLifecycleListener(plugin), plugin);
        pm.registerEvents(new GuiListener(plugin), plugin);
    }

    private void setupUtilities() {
        plugin.scheduler().runTask(() -> MessageUtil.init(plugin));
        ItemSerializerUtil.init(plugin);
        TitleUtil.init(plugin.scheduler());
        SoundUtil.init(plugin.scheduler());
        ActionbarUtil.init(plugin.scheduler());
        BossbarUtil.init(plugin.scheduler());
    }

    public void shutdown() {
        if (plugin.scheduler() != null) {
            plugin.scheduler().cancelAll();
        }
        if (plugin.core() != null && plugin.core().profiles() != null) {
            plugin.core().profiles().unloadAll();
        }
        if (plugin.database() != null) {
            plugin.database().close();
        }
    }
}