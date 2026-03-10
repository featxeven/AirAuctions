package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class PlayerSubCommand {

    private final AirAuctions plugin;

    public PlayerSubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String label, String[] args) {
        String usage = plugin.config().getSubcommandUsage("view-player", label);

        if (args.length < 2) {
            MessageUtil.send(player, plugin.lang().get("errors.incorrect-usage"), Map.of("usage", usage));
            return;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            MessageUtil.send(player, plugin.lang().get("errors.too-many-arguments"), Map.of("usage", usage));
            return;
        }

        String inputName = args[1];
        UUID targetUuid = plugin.database().records().uuidFromName(inputName);

        if (targetUuid == null) {
            MessageUtil.send(player, plugin.lang().get("errors.player-never-joined"), Map.of());
            return;
        }

        String realName = plugin.database().records().getNameFromUuid(targetUuid);

        if (realName == null || !realName.equalsIgnoreCase(inputName)) {
            MessageUtil.send(player, plugin.lang().get("errors.player-never-joined"), Map.of());
            return;
        }

        if (targetUuid.equals(player.getUniqueId())) {
            plugin.core().gui().open("player_listings", player, Map.of(
                    "page", "0"
            ));
            return;
        }

        plugin.core().gui().open("target_listings", player, Map.of(
                "target", realName,
                "page", "0"
        ));
    }
}