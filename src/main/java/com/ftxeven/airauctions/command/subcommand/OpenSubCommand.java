package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class OpenSubCommand {

    private final AirAuctions plugin;

    public OpenSubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player sender, String label, String[] args) {
        if (!sender.hasPermission("airauctions.command.open")) {
            MessageUtil.send(sender, plugin.lang().get("errors.no-permission"), Map.of());
            return;
        }

        if (args.length < 4) {
            String usage = sender.hasPermission("airauctions.command.open.others")
                    ? plugin.config().getSubcommandUsageOthers("open", label)
                    : plugin.config().getSubcommandUsage("open", label);

            MessageUtil.send(sender, plugin.lang().get("errors.incorrect-usage"), Map.of("usage", usage));
            return;
        }

        String guiId = args[1];
        String filter = args[2];
        String sort = args[3];

        Player target = sender;

        if (args.length >= 5) {
            if (!sender.hasPermission("airauctions.command.open.others")) {
                MessageUtil.send(sender, plugin.lang().get("errors.no-permission"),
                        Map.of("permission", "airauctions.command.open.others"));
                return;
            }

            target = Bukkit.getPlayerExact(args[4]);

            if (target == null) {
                MessageUtil.send(sender, plugin.lang().get("errors.player-not-found"),
                        Map.of("player", args[4]));
                return;
            }
        }

        plugin.core().gui().open(guiId, target, Map.of(
                "filter", filter,
                "sort", sort,
                "page", "0"
        ));

        if (target != sender) {
            MessageUtil.send(sender, plugin.lang().get("auctions.open.success-others"),
                    Map.of("player", target.getName(), "gui", guiId));
        }
    }
}