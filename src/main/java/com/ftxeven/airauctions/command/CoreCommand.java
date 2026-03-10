package com.ftxeven.airauctions.command;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CoreCommand implements TabExecutor {

    private final AirAuctions plugin;

    public CoreCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (sender instanceof Player player && !player.hasPermission("airauctions.admin")) {
            MessageUtil.send(player, plugin.lang().get("errors.no-permission"), Map.of("permission", "airauctions.admin"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "version" -> {
                String version = plugin.getDescription().getVersion();
                if (sender instanceof Player p) {
                    MessageUtil.send(p, plugin.lang().get("general.plugin-version"), Map.of("version", version));
                } else {
                    sender.sendMessage("AirAuctions version is " + version);
                }
            }
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.config().reload();
        plugin.lang().reload();
        plugin.filters().reload();
        plugin.itemTranslations().reload();
        plugin.core().reload();
        plugin.economy().loadProviders();

        if (sender instanceof Player p) {
            MessageUtil.send(p, plugin.lang().get("general.plugin-reloaded"), Map.of());
        } else {
            sender.sendMessage("Configuration, cache, and GUIs reloaded successfully.");
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        String usage = plugin.config().getMainUsage("main", label);
        if (usage == null) usage = "/" + label + " <reload|version>";

        if (sender instanceof Player p) {
            MessageUtil.send(p, plugin.lang().get("general.plugin-usage"), Map.of("usage", usage));
        } else {
            sender.sendMessage("Usage: " + usage);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {

        if (sender instanceof Player player && !player.hasPermission("airauctions.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Stream.of("reload", "version")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}