package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;

public final class SearchSubCommand {

    private final AirAuctions plugin;

    public SearchSubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String label, String[] args) {
        if (args.length < 2) {
            String usage = plugin.config().getSubcommandUsage("search", label);
            MessageUtil.send(player, plugin.lang().get("errors.incorrect-usage"), Map.of("usage", usage));
            return;
        }

        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            queryBuilder.append(args[i]);
            if (i < args.length - 1) queryBuilder.append(" ");
        }

        String query = queryBuilder.toString();

        int maxChars = plugin.config().getSearchMaxChars();
        if (query.length() > maxChars) {
            MessageUtil.send(player, plugin.lang().get("errors.search-query-too-long"),
                    Map.of("limit", String.valueOf(maxChars), "length", String.valueOf(query.length())));
            return;
        }

        plugin.core().gui().open("auction_search", player, Map.of(
                "query", query,
                "page", "1"
        ));
    }
}