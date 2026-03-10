package com.ftxeven.airauctions.core.economy;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.economy.providers.ExperienceProvider;
import com.ftxeven.airauctions.core.economy.providers.PlaceholderProvider;
import com.ftxeven.airauctions.core.economy.providers.VaultProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class EconomyManager {

    private final AirAuctions plugin;
    private final Map<String, EconomyProvider> providers = new HashMap<>();
    private final EconomyProvider nullProvider = new NullProvider();

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
                        providers.put(id.toLowerCase(), new VaultProvider(rsp.getProvider(), display, decimals, plugin.getLogger()));
                    } else {
                        plugin.getLogger().severe("Economy provider '" + id + "' requires Vault, but Vault was not found!");
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
        if (id == null) return nullProvider;

        EconomyProvider provider = providers.get(id.toLowerCase());
        if (provider != null) return provider;

        String defaultId = plugin.config().economyDefaultProvider();
        return providers.getOrDefault(defaultId.toLowerCase(), nullProvider);
    }

    public static final class NullProvider implements EconomyProvider {
        @Override public String getId() { return "none"; }
        @Override public String getDisplay(String amount) { return "[Missing Economy] " + amount; }
        @Override public boolean hasDecimals() { return false; }
        @Override public double getBalance(Player player) { return 0; }
        @Override public boolean has(Player player, double amount) { return false; }
        @Override public void withdraw(Player player, double amount) {}
        @Override public void deposit(OfflinePlayer player, double amount) {}
    }
}