package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class OpenSubCommand {

    private final AirAuctions plugin;

    public OpenSubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String label, String[] args) {
        boolean isConsole = !(sender instanceof Player);

        if (!isConsole && !sender.hasPermission("airauctions.command.open")) {
            MessageUtil.send((Player) sender, plugin.lang().get("errors.no-permission"), Map.of());
            return;
        }

        if ((isConsole && args.length < 5) || (!isConsole && args.length < 4)) {
            sendUsage(sender, label, isConsole);
            return;
        }

        String guiId = args[1];
        String filter = args[2];
        String sort = args[3];
        Player target;

        if (args.length >= 5) {
            if (!isConsole && !sender.hasPermission("airauctions.command.open.others")) {
                MessageUtil.send((Player) sender, plugin.lang().get("errors.no-permission"),
                        Map.of("permission", "airauctions.command.open.others"));
                return;
            }
            target = Bukkit.getPlayerExact(args[4]);
        } else {
            target = (Player) sender;
        }

        if (target == null) {
            if (isConsole) {
                sender.sendMessage("Player not found");
            } else {
                MessageUtil.send((Player) sender, plugin.lang().get("errors.player-not-found"), Map.of());
            }
            return;
        }

        plugin.core().gui().open(guiId, target, Map.of(
                "filter", filter,
                "sort", sort,
                "page", "0"
        ));

        if (isConsole || target != sender) {
            if (isConsole) {
                sender.sendMessage("Opened GUI '" + guiId + "' for " + target.getName());
            } else {
                MessageUtil.send((Player) sender, plugin.lang().get("auctions.open.success-others"),
                        Map.of("player", target.getName(), "gui", guiId));
            }
        }
    }

    private void sendUsage(CommandSender sender, String label, boolean isConsole) {
        if (isConsole) {
            sender.sendMessage("Usage: /" + label + " open <gui> <filter> <sort> <player>");
        } else {
            String usage = sender.hasPermission("airauctions.command.open.others")
                    ? plugin.config().getSubcommandUsageOthers("open", label)
                    : plugin.config().getSubcommandUsage("open", label);
            MessageUtil.send((Player) sender, plugin.lang().get("errors.incorrect-usage"), Map.of("usage", usage));
        }
    }
}