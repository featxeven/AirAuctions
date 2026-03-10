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
        if (plugin.config().errorOnExcessArgs() && args.length > 1) {
            String usage = plugin.config().getSubcommandUsage("history", label);
            MessageUtil.send(player, plugin.lang().get("errors.too-many-arguments"), Map.of("usage", usage));
            return;
        }

        plugin.core().gui().open("player_history", player, Map.of(
                "page", "0"
        ));
    }
}