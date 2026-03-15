package com.ftxeven.airauctions.command;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.command.subcommand.*;
import com.ftxeven.airauctions.core.manager.main.CategoryManager;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final AirAuctions plugin;
    private final SellSubCommand sellSub;
    private final ListingsSubCommand listingsSub;
    private final PlayerSubCommand playerSub;
    private final SearchSubCommand searchSub;
    private final ExpiredSubCommand expiredSub;
    private final HistorySubCommand historySub;
    private final OpenSubCommand openSub;
    private final RemoveSubCommand removeSub;

    public MainCommand(AirAuctions plugin) {
        this.plugin = plugin;
        this.sellSub = new SellSubCommand(plugin);
        this.listingsSub = new ListingsSubCommand(plugin);
        this.playerSub = new PlayerSubCommand(plugin);
        this.searchSub = new SearchSubCommand(plugin);
        this.expiredSub = new ExpiredSubCommand(plugin);
        this.historySub = new HistorySubCommand(plugin);
        this.openSub = new OpenSubCommand(plugin);
        this.removeSub = new RemoveSubCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && isTrigger(args[0], "open")) {
                openSub.execute(sender, label, args);
                return true;
            }
            sender.sendMessage("Usage: /" + label + " open <gui> <filter> <sort> <player>");
            return true;
        }

        if (args.length > 0) {
            String subArg = args[0].toLowerCase();

            if (isTrigger(subArg, "sell")) {
                if (checkPerm(player, "sell")) sellSub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "listings")) {
                if (checkPerm(player, "listings")) listingsSub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "expired")) {
                if (checkPerm(player, "expired")) expiredSub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "history")) {
                if (checkPerm(player, "history")) historySub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "view-player")) {
                if (checkPerm(player, "view-player")) playerSub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "search")) {
                if (checkPerm(player, "search")) searchSub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "open")) {
                if (checkPerm(player, "open")) openSub.execute(player, label, args);
                return true;
            }
            if (isTrigger(subArg, "remove")) {
                if (checkPerm(player, "remove")) removeSub.execute(player, label, args);
                return true;
            }

            if (plugin.config().errorOnExcessArgs()) {
                String usage = plugin.config().getMainUsage("main", label);
                MessageUtil.send(player, plugin.lang().get("errors.incorrect-usage"),
                        Map.of("usage", usage != null ? usage : "/" + label));
                return true;
            }
        }

        if (!player.hasPermission("airauctions.command.browse")) {
            MessageUtil.send(player, plugin.lang().get("errors.no-permission"),
                    Map.of("permission", "airauctions.command.browse"));
            return true;
        }

        var catManager = plugin.core().gui().get("categories", CategoryManager.class);
        if (catManager != null && catManager.isEnabled()) {
            plugin.core().gui().open("categories", player, Map.of());
        } else {
            plugin.core().gui().open("auction_house", player, Map.of(
                    "filter", plugin.filters().getDefaultFilter(),
                    "page", "0"
            ));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        boolean isPlayer = sender instanceof Player;

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (!isPlayer) {
                suggestions.add(plugin.config().getSubcommandName("open", "open"));
            } else {
                addIfAllowed(sender, suggestions, "sell", "sell");
                addIfAllowed(sender, suggestions, "listings", "listings");
                addIfAllowed(sender, suggestions, "expired", "expired");
                addIfAllowed(sender, suggestions, "history", "history");
                addIfAllowed(sender, suggestions, "view-player", "player");
                addIfAllowed(sender, suggestions, "search", "search");
                addIfAllowed(sender, suggestions, "open", "open");
                addIfAllowed(sender, suggestions, "remove", "remove");
            }

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        String subArg = args[0].toLowerCase();

        if (isPlayer) {
            String key = findKeyByTrigger(subArg);
            if (key != null && !sender.hasPermission("airauctions.command." + key)) {
                return List.of();
            }
        } else {
            if (!isTrigger(subArg, "open")) return List.of();
        }

        return handleDeepTabComplete(sender, subArg, args);
    }

    private List<String> handleDeepTabComplete(CommandSender sender, String subArg, String[] args) {
        boolean isConsole = !(sender instanceof Player);
        String openTrigger = plugin.config().getSubcommandName("open", "open").toLowerCase();
        String playerTrigger = plugin.config().getSubcommandName("view-player", "player").toLowerCase();
        String historyTrigger = plugin.config().getSubcommandName("history", "history").toLowerCase();

        if (args.length == 2) {
            if (!isConsole && (subArg.equals(playerTrigger) || (subArg.equals(historyTrigger) && sender.hasPermission("airauctions.command.history.others")))) {
                return getPlayerSuggestions(args[1]);
            }
            if (subArg.equals(openTrigger)) {
                return Stream.of("auction_house", "player_expired", "player_listings", "player_history", "categories")
                        .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
            }
        }

        if (args.length == 3 && subArg.equals(openTrigger)) {
            return plugin.filters().getOrderedCategoryKeys().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }

        if (args.length == 4 && subArg.equals(openTrigger)) {
            return Stream.of("newest-date", "oldest-date", "highest-price", "lowest-price", "alphabetical", "amount")
                    .filter(s -> s.startsWith(args[3].toLowerCase())).toList();
        }

        if (args.length == 5 && subArg.equals(openTrigger)) {
            if (isConsole || sender.hasPermission("airauctions.command.open.others")) {
                return getPlayerSuggestions(args[4]);
            }
        }

        return List.of();
    }

    private boolean isTrigger(String input, String key) {
        return input.equalsIgnoreCase(plugin.config().getSubcommandName(key, key))
                && plugin.config().isSubcommandEnabled(key);
    }

    private boolean checkPerm(Player player, String key) {
        String permission = "airauctions.command." + key;
        if (player.hasPermission(permission)) return true;

        MessageUtil.send(player, plugin.lang().get("errors.no-permission"),
                Map.of("permission", permission));
        return false;
    }

    private void addIfAllowed(CommandSender sender, List<String> list, String key, String def) {
        if (plugin.config().isSubcommandEnabled(key) && sender.hasPermission("airauctions.command." + key)) {
            list.add(plugin.config().getSubcommandName(key, def));
        }
    }

    private @Nullable String findKeyByTrigger(String subArg) {
        return Stream.of(
                "sell",
                        "listings",
                        "expired",
                        "history",
                        "view-player",
                        "search",
                        "open",
                        "remove")
                .filter(k -> subArg.equalsIgnoreCase(plugin.config().getSubcommandName(k, k)))
                .findFirst().orElse(null);
    }

    private List<String> getPlayerSuggestions(String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                .limit(20).toList();
    }
}