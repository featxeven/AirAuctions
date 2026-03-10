package com.ftxeven.airauctions.core.economy.providers;

import com.ftxeven.airauctions.core.economy.EconomyProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public final class VaultProvider implements EconomyProvider {

    private final Economy vaultEco;
    private final String displayFormat;
    private final boolean decimals;
    private final Logger logger;

    public VaultProvider(Economy vaultEco, String display, boolean decimals, Logger logger) {
        this.vaultEco = vaultEco;
        this.displayFormat = display;
        this.decimals = decimals;
        this.logger = logger;
    }

    @Override public String getId() { return "vault"; }
    @Override public boolean hasDecimals() { return decimals; }

    @Override
    public String getDisplay(String amount) { return displayFormat.replace("%amount%", amount); }

    @Override
    public double getBalance(Player player) {
        return vaultEco.getBalance(player);
    }

    @Override
    public boolean has(Player player, double amount) {
        return vaultEco.has(player, amount);
    }

    @Override
    public void withdraw(Player player, double amount) {
        EconomyResponse response = vaultEco.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            logger.warning("Vault withdrawal failed for " + player.getName() + ": " + response.errorMessage);
        }
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        EconomyResponse response = vaultEco.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            logger.warning("Vault deposit failed for " + player.getName() + ": " + response.errorMessage);
        }
    }

    private String format(double amount) {
        return decimals ? String.format("%.2f", amount) : String.valueOf((long) amount);
    }
}