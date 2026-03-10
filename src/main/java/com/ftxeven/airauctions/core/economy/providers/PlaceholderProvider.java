package com.ftxeven.airauctions.core.economy.providers;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.economy.EconomyProvider;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class PlaceholderProvider implements EconomyProvider {

    private final AirAuctions plugin;
    private final String id;
    private final String displayFormat;
    private final boolean decimals;
    private final String balancePlaceholder;
    private final String giveCmd;
    private final String takeCmd;
    private final boolean offlineSupport;

    public PlaceholderProvider(AirAuctions plugin, String id, ConfigurationSection section) {
        this.plugin = plugin;
        this.id = id;
        this.displayFormat = section.getString("display", "%amount%");
        this.decimals = section.getBoolean("allow-decimals", false);

        ConfigurationSection settings = section.getConfigurationSection("settings");
        Objects.requireNonNull(settings, "Settings section missing for provider: " + id);

        this.balancePlaceholder = settings.getString("balance-placeholder", "");
        this.giveCmd = settings.getString("give-command", "");
        this.takeCmd = settings.getString("take-command", "");
        this.offlineSupport = settings.getBoolean("offline-support", false);
    }

    @Override public String getId() { return id; }
    @Override public boolean hasDecimals() { return decimals; }

    @Override
    public String getDisplay(String amount) {
        return displayFormat.replace("%amount%", amount);
    }

    @Override
    public double getBalance(Player player) {
        if (balancePlaceholder.isEmpty()) return 0;
        String raw = PlaceholderAPI.setPlaceholders(player, balancePlaceholder);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public void withdraw(Player player, double amount) {
        execute(takeCmd, player, amount);
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        if (player.isOnline()) {
            execute(giveCmd, (Player) player, amount);
        } else if (offlineSupport) {
            String cmd = giveCmd
                    .replace("%player%", Objects.requireNonNull(player.getName()))
                    .replace("%amount%", formatPlain(amount));

            plugin.scheduler().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        }
    }

    private void execute(String command, Player player, double amount) {
        if (command == null || command.isEmpty()) return;

        String finalCmd = command
                .replace("%player%", player.getName())
                .replace("%amount%", formatPlain(amount));

        plugin.scheduler().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
    }

    private String formatPlain(double amount) {
        return decimals ? String.format("%.2f", amount) : String.valueOf((long) amount);
    }
}