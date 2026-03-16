package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.AirAuctions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ConfigManager {

    private final AirAuctions plugin;
    private FileConfiguration config;

    public ConfigManager(AirAuctions plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    private String s(String p, String d) { return config.getString(p, d); }
    private boolean b(String p) { return config.getBoolean(p, true); }
    private int i(String p, int d) { return config.getInt(p, d); }
    private double d(String p, double d) { return config.getDouble(p, d); }
    private List<String> sl(String p) { return config.getStringList(p); }

    // General Settings
    public boolean notifyUpdates() { return b("notify-updates"); }
    public boolean errorOnExcessArgs() { return b("error-on-excess-args"); }
    public String timeFormatMode() { return s("time-format.mode", "CUSTOM").toUpperCase(); }
    public int timeFormatGranularity() { return i("time-format.granularity", 2); }
    public String getDateFormat() { return s("date-format", "dd/MM/yy"); }
    public boolean forceUpdateGui() { return b("force-update-gui"); }
    public boolean skipEmptyLines() { return config.getBoolean("skip-empty-lines", true); }

    // Economy Settings
    public String economyDefaultProvider() { return s("economy.default-currency", "vault"); }
    public String economyNumberFormat() { return s("economy.number-format", "SHORT"); }
    public List<String> economyFormatShortSuffixes() {
        List<String> list = sl("economy.format-short-suffix");
        return list.isEmpty() ? List.of("", "k", "M", "B", "T", "Q") : list;
    }
    public boolean economyAllowFormatShortInCommand() { return b("economy.allow-format-short-in-command"); }
    public double minAuctionPrice() { return d("economy.min-auction-price", 5.0); }
    public double maxAuctionPrice() { return d("economy.max-auction-price", 10000000.0); }

    public Set<String> getEconomyProviders() {
        ConfigurationSection section = config.getConfigurationSection("economy.providers");
        return section == null ? Collections.emptySet() : section.getKeys(false);
    }

    public ConfigurationSection getProviderSection(String providerId) {
        return config.getConfigurationSection("economy.providers." + providerId);
    }

    // Fee & Tax Logic
    public boolean isListingFeeEnabled() { return config.getBoolean("economy.listing-fee.enabled", true); }
    public String getListingFeeType() { return s("economy.listing-fee.type", "PERCENTAGE").toUpperCase(); }
    public double getDefaultListingFee() { return d("economy.listing-fee.default-value", 10.0); }
    public double getMinListingFee() { return d("economy.listing-fee.min-fee", 1.0); }
    public double getMaxListingFee() { return d("economy.listing-fee.max-fee", 1000.0); }

    public boolean isSalesTaxEnabled() { return config.getBoolean("economy.sales-tax.enabled", true); }
    public String getSalesTaxType() { return s("economy.sales-tax.type", "PERCENTAGE").toUpperCase(); }
    public double getDefaultSalesTax() { return d("economy.sales-tax.default-value", 10.0); }
    public double getMinSalesTax() { return d("economy.sales-tax.min-fee", 1.0); }
    public double getMaxSalesTax() { return d("economy.sales-tax.max-fee", 1000.0); }

    public double getValueFromPermissions(Player player, String permissionNode, double defaultValue) {
        if (player == null) return defaultValue;

        return player.getEffectivePermissions().stream()
                .map(p -> p.getPermission().toLowerCase())
                .filter(p -> p.startsWith(permissionNode))
                .map(p -> p.substring(permissionNode.length()))
                .mapToDouble(val -> {
                    try {
                        return Double.parseDouble(val);
                    } catch (NumberFormatException e) {
                        return -1.0;
                    }
                })
                .filter(val -> val >= 0)
                .max()
                .orElse(defaultValue);
    }

    // Auctions
    public int getDefaultLimit() { return i("auctions.max-listings", 3); }
    public int getPlayerLimit(Player player) {
        if (player.isOp()) return Integer.MAX_VALUE;

        int configDefault = getDefaultLimit();
        return (int) getValueFromPermissions(player, "airauctions.bypass.limit.", configDefault);
    }

    public int getExpireTime(Player player) {
        int configDefault = i("auctions.expire-time", 86400);

        return (int) getValueFromPermissions(player, "airauctions.bypass.expire-time.", configDefault);
    }

    public String getLocale() { return s("lang", "en_US"); }
    public boolean confirmAuction() { return b("auctions.confirm-auction"); }
    public int confirmationExpireTime() { return i("auctions.confirmation-expire-time", 60); }
    public boolean confirmRemove() { return b("auctions.confirm-remove"); }
    public int removeExpireTime() { return i("auctions.remove-expire-time", 60); }
    public boolean broadcastToSelf() { return config.getBoolean("auctions.broadcast-to-self", false); }
    public int historyLimit() { return i("auctions.history-item-limit", 50); }
    public int expiredLimit() { return i("auctions.expired-item-limit", 50); }
    public int auctionCooldown() { return i("auctions.auction-cooldown", 5); }
    public int purgeTime() { return i("auctions.purge-time", -1); }
    public String amountType() { return s("auctions.amount-type", "OPTIONAL").toUpperCase(); }
    public boolean dropItemsWhenFull() { return b("auctions.drop-items-when-full"); }
    public boolean purchaseOwn() { return b("auctions.purchase-own"); }

    public Set<String> getSortingKeys() {
        ConfigurationSection section = config.getConfigurationSection("auctions.sorting");
        return section == null ? Collections.emptySet() : section.getKeys(false);
    }
    public String getSortingDisplayName(String key) { return s("auctions.sorting." + key, key); }
    public String getDefaultSortingKey() {
        return getSortingKeys().stream()
                .findFirst()
                .orElse("newest-date");
    }
    public String getNextSortingKey(String currentKey) {
        List<String> keys = new java.util.ArrayList<>(getSortingKeys());
        if (keys.isEmpty()) return "newest-date";

        int currentIndex = keys.indexOf(currentKey);
        if (currentIndex == -1 || currentIndex == keys.size() - 1) {
            return keys.getFirst();
        }
        return keys.get(currentIndex + 1);
    }
    public String getPreviousSortingKey(String currentKey) {
        List<String> keys = new ArrayList<>(getSortingKeys());
        if (keys.isEmpty()) return "newest-date";

        int currentIndex = keys.indexOf(currentKey);

        if (currentIndex <= 0) {
            return keys.getLast();
        }
        return keys.get(currentIndex - 1);
    }

    // Command Config
    public boolean isSubcommandEnabled(String key) { return config.getBoolean("commands.subcommands." + key + ".enabled", true); }
    public String getCommandName(String key, String defaultName) { return s("commands." + key + ".name", defaultName); }
    public List<String> getCommandAliases(String key) { return sl("commands." + key + ".aliases"); }
    public String getSubcommandName(String key, String defaultName) { return s("commands.subcommands." + key + ".name", defaultName); }
    public String getSubcommandUsage(String key, String label) {
        String usage = config.getString("commands.subcommands." + key + ".usage");
        String subLabel = getSubcommandName(key, key);
        return usage == null ? "/" + label + " " + subLabel + " [args]" : usage.replace("%label%", label).replace("%sublabel%", subLabel);
    }
    public String getSubcommandUsageOthers(String key, String label) {
        String usage = config.getString("commands.subcommands." + key + ".usage-others");
        String subLabel = getSubcommandName(key, key);
        return usage == null ? getSubcommandUsage(key, label) : usage.replace("%label%", label).replace("%sublabel%", subLabel);
    }
    public String getMainUsage(String key, String label) {
        String usage = config.getString("commands." + key + ".usage");
        return (usage != null) ? usage.replace("%label%", label) : null;
    }

    // Restrictions
    public int getSearchMaxChars() { return i("restrictions.max-query-length", 32); }
    public List<String> blockedGamemodes() { return sl("restrictions.blocked-gamemodes"); }
    public List<String> disabledWorlds() { return sl("restrictions.disabled-worlds"); }
    public boolean allowDamagedItems() { return b("restrictions.allow-damaged-items"); }
    public boolean isItemFilterWhitelist() { return b("restrictions.item-blacklist.as-whitelist"); }
    public List<String> getFilterMaterials() { return sl("restrictions.item-blacklist.materials"); }
    public List<String> getFilterNames() { return sl("restrictions.item-blacklist.names"); }
    public List<String> getFilterLores() { return sl("restrictions.item-blacklist.lores"); }
    public List<String> getFilterEnchantments() { return sl("restrictions.item-blacklist.enchantments"); }
    public List<String> getFilterNbtKeys() { return sl("restrictions.item-blacklist.nbt-keys"); }
    public List<String> getFilterCustomModelData() { return sl("restrictions.item-blacklist.custom-model-data"); }
    public List<String> getFilterItemModels() { return sl("restrictions.item-blacklist.item-models"); }
    public List<String> getFilterSpecialItems() { return sl("restrictions.item-blacklist.special-items"); }
    public ConfigurationSection getFilterSpecificItems() { return config.getConfigurationSection("restrictions.item-blacklist.specific-items"); }
}