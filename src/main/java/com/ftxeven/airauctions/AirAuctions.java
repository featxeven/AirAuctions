package com.ftxeven.airauctions;

import com.ftxeven.airauctions.core.CoreInitializer;
import com.ftxeven.airauctions.core.CoreManager;
import com.ftxeven.airauctions.core.hook.HookManager;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.core.economy.EconomyManager; // Use Manager instead of Provider
import com.ftxeven.airauctions.config.*;
import com.ftxeven.airauctions.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class AirAuctions extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private LangManager langManager;
    private ItemTranslationManager itemTranslationManager;
    private FilterManager filterManager;
    private SchedulerUtil schedulerUtil;
    private CoreInitializer coreInitializer;
    private EconomyManager economyManager;
    private CoreManager coreManager;
    private HookManager hookManager;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.coreInitializer = new CoreInitializer(this);
        this.coreInitializer.initialize();
        getLogger().info("Plugin enabled @ featxeven");
    }

    @Override
    public void onDisable() {
        if (coreInitializer != null) {
            coreInitializer.shutdown();
        }
        getLogger().info("Plugin disabled @ featxeven");
    }

    public void setConfigManager(ConfigManager configManager) { this.configManager = configManager; }
    public void setDatabaseManager(DatabaseManager databaseManager) { this.databaseManager = databaseManager; }
    public void setLangManager(LangManager langManager) { this.langManager = langManager; }
    public void setItemTranslationManager(ItemTranslationManager itemTranslationManager) { this.itemTranslationManager = itemTranslationManager; }
    public void setFilterManager(FilterManager filterManager) { this.filterManager = filterManager; }
    public void setSchedulerUtil(SchedulerUtil schedulerUtil) { this.schedulerUtil = schedulerUtil; }
    public void setEconomyManager(EconomyManager economyManager) { this.economyManager = economyManager; }
    public void setCoreManager(CoreManager coreManager) { this.coreManager = coreManager; }
    public void setHookManager(HookManager hookManager) { this.hookManager = hookManager;  }

    public ConfigManager config() { return configManager; }
    public DatabaseManager database() { return databaseManager; }
    public LangManager lang() { return langManager; }
    public ItemTranslationManager itemTranslations() { return itemTranslationManager; }
    public FilterManager filters() { return filterManager; }
    public SchedulerUtil scheduler() { return schedulerUtil; }
    public EconomyManager economy() { return economyManager; }
    public CoreManager core() { return coreManager; }
    public HookManager getHookManager() { return hookManager; }
}