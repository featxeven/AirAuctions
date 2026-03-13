package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class HistorySubCommand {

    private final AirAuctions plugin;

    public HistorySubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String label, String[] args) {
        if (args.length == 1) {
            plugin.core().gui().open("player_history", player, Map.of("page", "1"));
            return;
        }

        if (args.length == 2) {
            String inputName = args[1];

            if (inputName.equalsIgnoreCase(player.getName())) {
                plugin.core().gui().open("player_history", player, Map.of("page", "1"));
                return;
            }

            if (!player.hasPermission("airauctions.command.history.others")) {
                MessageUtil.send(player, plugin.lang().get("errors.no-permission"), Map.of("permission", "airauctions.command.history.others"));
                return;
            }

            UUID targetUuid = plugin.database().records().uuidFromName(inputName);
            if (targetUuid == null) {
                MessageUtil.send(player, plugin.lang().get("errors.player-never-joined"), Map.of());
                return;
            }

            String realName = plugin.database().records().getNameFromUuid(targetUuid);
            if (realName == null) {
                MessageUtil.send(player, plugin.lang().get("errors.player-never-joined"), Map.of());
                return;
            }

            plugin.core().gui().open("target_history", player, Map.of(
                    "target", realName,
                    "page", "1"
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