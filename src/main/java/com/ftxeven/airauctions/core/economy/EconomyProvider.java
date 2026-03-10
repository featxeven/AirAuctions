package com.ftxeven.airauctions.core.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface EconomyProvider {
    String getId();
    String getDisplay(String amount);
    boolean hasDecimals();

    double getBalance(Player player);
    boolean has(Player player, double amount);
    void withdraw(Player player, double amount);
    void deposit(OfflinePlayer player, double amount);
}