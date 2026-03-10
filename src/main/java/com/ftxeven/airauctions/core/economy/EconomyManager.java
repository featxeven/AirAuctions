package com.ftxeven.airauctions.core.economy;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.economy.providers.ExperienceProvider;
import com.ftxeven.airauctions.core.economy.providers.PlaceholderProvider;
import com.ftxeven.airauctions.core.economy.providers.VaultProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class EconomyManager {

    private final AirAuctions plugin;
    private final Map<String, EconomyProvider> providers = new HashMap<>();

    public EconomyManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadProviders();
    }

    public void loadProviders() {
        providers.clear();
        Set<String> keys = plugin.config().getEconomyProviders();

        for (String id : keys) {
            ConfigurationSection section = plugin.config().getProviderSection(id);
            if (section == null) continue;

            String type = section.getString("type", "VAULT").toUpperCase();
            String display = section.getString("display", "<green>$%amount%");
            boolean decimals = section.getBoolean("allow-decimals", true);

            switch (type) {
                case "VAULT" -> {
                    RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
                    if (rsp != null) {
                        providers.put(id, new VaultProvider(rsp.getProvider(), display, decimals, plugin.getLogger()));
                    } else {
                        plugin.getLogger().warning("Vault provider '" + id + "' was skipped because Vault is not installed!");
                    }
                }
                case "PLACEHOLDER" -> {
                    if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        providers.put(id, new PlaceholderProvider(plugin, id, section));
                    } else {
                        plugin.getLogger().warning("Placeholder provider '" + id + "' was skipped because PlaceholderAPI is not installed!");
                    }
                }
                case "EXP" ->
                        providers.put(id, new ExperienceProvider(plugin, id, display));
            }
        }
    }

        public EconomyProvider getProvider(String id) {
        EconomyProvider provider = providers.get(id.toLowerCase());
        if (provider == null) {
            return providers.get(plugin.config().economyDefaultProvider().toLowerCase());
        }
        return provider;
    }
}