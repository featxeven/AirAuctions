package com.ftxeven.airauctions;

import com.ftxeven.airauctions.api.papi.AirAuctionsExpansion;
import com.ftxeven.airauctions.command.CommandManager;
import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.animation.AnimationManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.core.hook.HookRegistry;
import com.ftxeven.airauctions.database.cache.CacheManager;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.economy.EconomyRegistry;
import com.ftxeven.airauctions.listener.GuiListener;
import com.ftxeven.airauctions.listener.PlayerListener;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.Placeholders;
import com.ftxeven.airauctions.util.Scheduler;
import com.ftxeven.airauctions.util.Version;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AirAuctions extends JavaPlugin {

    private ConfigManager configs;
    private DatabaseManager database;
    private CacheManager cache;
    private HookRegistry hooks;
    private EconomyRegistry economy;
    private AnimationManager animations;
    private Messenger messenger;
    private ServiceManager services;
    private ListingGuiManager guis;
    private CommandManager commands;
    private AirAuctionsExpansion placeholders;
    private Metrics metrics;

    @Override
    public void onEnable() {
        getLogger().info("Running on " + Bukkit.getName() + " - " + Bukkit.getVersion());

        configs = new ConfigManager(this);
        if (!configs.load()) {
            getLogger().severe("One or more config files failed to load, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new DatabaseManager(this, configs);
        if (!database.connect()) {
            getLogger().severe("Failed to connect to the database, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cache = new CacheManager(this, configs, database);

        hooks = new HookRegistry(this);

        economy = new EconomyRegistry(this, configs);

        animations = new AnimationManager(this, configs.animations()::animations);
        animations.start();

        messenger = new Messenger(getLogger(), animations);

        services = new ServiceManager(this);
        services.expiry().start();

        guis = ListingGuiManager.create(this, messenger, configs, hooks, services, animations);
        if (guis == null) {
            getLogger().severe("One or more GUI files failed to load, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        commands = new CommandManager(this);
        commands.registerAll();

        getServer().getPluginManager().registerEvents(new PlayerListener(services, configs, messenger, guis()), this);
        getServer().getPluginManager().registerEvents(new GuiListener(guis.guis()), this);

        if (Placeholders.papiEnabled()) {
            placeholders = new AirAuctionsExpansion(this);
            placeholders.register();
        }

        metrics = new Metrics(this, 33241);

        Version.check();
    }

    @Override
    public void onDisable() {
        if (services != null) {
            services.expiry().stop();
        }

        if (guis != null) {
            guis.shutdown();
        }

        if (placeholders != null) {
            placeholders.unregister();
        }

        Scheduler.cancelGlobal();
        Scheduler.cancelAsync();

        if (animations != null) {
            animations.stop();
        }

        if (cache != null) {
            cache.close();
        }

        if (database != null) {
            database.close();
        }
    }

    public ConfigManager configs() { return configs; }

    public DatabaseManager database() { return database; }

    public CacheManager cache() { return cache; }

    public HookRegistry hooks() { return hooks; }

    public EconomyRegistry economy() { return economy; }

    public AnimationManager animations() { return animations; }

    public Messenger messenger() { return messenger; }

    public ServiceManager services() { return services; }

    public GuiManager guis() { return guis.guis(); }

    public ListingGuiManager listingGuis() { return guis; }

    public CommandManager commands() { return commands; }

    public Metrics metrics() { return metrics; }
}