package com.ftxeven.airauctions.core.economy.providers;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class ExperienceProvider implements EconomyProvider {

    private final AirAuctions plugin;
    private final String id;
    private final String displayFormat;

    public ExperienceProvider(AirAuctions plugin, String id, String displayFormat) {
        this.plugin = plugin;
        this.id = id;
        this.displayFormat = displayFormat;
    }

    @Override public String getId() { return id; }
    @Override public boolean hasDecimals() { return false; }

    @Override
    public String getDisplay(String amount) {
        return displayFormat.replace("%amount%", amount);
    }

    @Override
    public double getBalance(Player player) {
        return player.getTotalExperience();
    }

    @Override
    public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public void withdraw(Player player, double amount) {
        int currentExp = player.getTotalExperience();
        int toRemove = (int) amount;
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);
        player.giveExp(Math.max(0, currentExp - toRemove));
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        String cmd = "experience add " + player.getName() + " " + (int) amount + " points";

        plugin.scheduler().runTask(() ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        );
    }
}