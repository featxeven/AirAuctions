package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.entity.Player;
import java.util.Map;

public final class HistorySubCommand {

    private final AirAuctions plugin;

    public HistorySubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String label, String[] args) {
        if (args.length == 1) {
            plugin.core().gui().open("player_history", player, Map.of("page", "0"));
            return;
        }

        if (args.length == 2) {
            if (!player.hasPermission("airauctions.command.history.others")) {
                MessageUtil.send(player, plugin.lang().get("errors.no-permission"), Map.of("permission", "airauctions.command.history.others"));
                return;
            }

            String targetName = args[1];
            plugin.core().gui().open("target_history", player, Map.of(
                    "target", targetName,
                    "page", "0"
            ));
            return;
        }

        if (plugin.config().errorOnExcessArgs()) {
            String usage = player.hasPermission("airauctions.command.history.others")
                    ? plugin.config().getSubcommandUsageOthers("history", label)
                    : plugin.config().getSubcommandUsage("history", label);

            MessageUtil.send(player, plugin.lang().get("errors.too-many-arguments"), Map.of("usage", usage));
        }
    }
}