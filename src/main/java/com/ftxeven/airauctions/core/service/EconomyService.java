package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public final class EconomyService {

    private final AirAuctions plugin;
    private final FormatService formats;

    public EconomyService(AirAuctions plugin) {
        this.plugin = plugin;
        this.formats = new FormatService(plugin);
    }

    public boolean canAfford(Player player, String currencyId, double amount) {
        return plugin.economy().getProvider(currencyId).has(player, amount);
    }

    public double getBalance(Player player, String currencyId) {
        return plugin.economy().getProvider(currencyId).getBalance(player);
    }

    public double processTransaction(Player buyer, UUID sellerUuid, double amount, String currencyId) {
        EconomyProvider provider = plugin.economy().getProvider(currencyId);

        provider.withdraw(buyer, amount);

        double taxAmount = calculateSalesTax(sellerUuid, amount, provider);
        double finalProfit = amount - taxAmount;

        provider.deposit(Bukkit.getOfflinePlayer(sellerUuid), finalProfit);

        return finalProfit;
    }

    public double calculateSalesTax(UUID sellerUuid, double amount, EconomyProvider provider) {
        if (!plugin.config().isSalesTaxEnabled()) return 0.0;

        Player seller = Bukkit.getPlayer(sellerUuid);
        double value = plugin.config().getValueFromPermissions(seller, "airauctions.tax.", plugin.config().getDefaultSalesTax());
        String type = plugin.config().getSalesTaxType();

        double tax = type.equalsIgnoreCase("FIXED") ? value : (amount * (value / 100.0));

        if (!type.equalsIgnoreCase("FIXED")) {
            tax = Math.max(tax, plugin.config().getMinSalesTax());
            double max = plugin.config().getMaxSalesTax();
            if (max != -1) tax = Math.min(tax, max);
        }

        return formats.round(tax, provider.hasDecimals());
    }

    public double calculateListingFee(Player player, double price, String currencyId) {
        if (!plugin.config().isListingFeeEnabled()) return 0.0;

        double value = plugin.config().getValueFromPermissions(player, "airauctions.fee.", plugin.config().getDefaultListingFee());
        String type = plugin.config().getListingFeeType();
        EconomyProvider provider = plugin.economy().getProvider(currencyId);

        double fee = type.equalsIgnoreCase("FIXED") ? value : (price * (value / 100.0));

        if (!type.equalsIgnoreCase("FIXED")) {
            fee = Math.max(fee, plugin.config().getMinListingFee());
            double max = plugin.config().getMaxListingFee();
            if (max != -1) fee = Math.min(fee, max);
        }

        return formats.round(fee, provider.hasDecimals());
    }

    public FormatService formats() {
        return formats;
    }
}