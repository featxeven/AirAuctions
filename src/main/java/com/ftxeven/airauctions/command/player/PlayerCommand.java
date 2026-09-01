package com.ftxeven.airauctions.command.player;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.command.CommandDispatcher;
import com.ftxeven.airauctions.core.command.CommandDispatch;
import com.ftxeven.airauctions.core.command.CommandRegistry;
import com.ftxeven.airauctions.core.command.DurationUnits;
import com.ftxeven.airauctions.core.command.RootCommand;
import com.ftxeven.airauctions.core.command.Shortcuts;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.gui.impl.ActiveGui;
import com.ftxeven.airauctions.gui.impl.ExpiredGui;
import com.ftxeven.airauctions.gui.impl.HistoryGui;
import com.ftxeven.airauctions.gui.impl.StorageGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PlayerCommand implements CommandExecutor, TabCompleter, Listener {

    private final AirAuctions plugin;
    private final CommandRegistry registry;
    private final CommandDispatcher dispatcher;

    public PlayerCommand(AirAuctions plugin, TabCompleteEngine tabComplete, DurationUnits durationUnits) {
        this.plugin = plugin;
        this.registry = new CommandRegistry()
                .register(new SubSlots(plugin.configs(), plugin.messenger(), plugin.services(), tabComplete))
                .register(new SubSell(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete))
                .register(new SubBid(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete, durationUnits))
                .register(new SubDelete(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete))
                .register(new SubOpen(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete))
                .register(new ViewSubcommand(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete, "listings", ActiveGui.SELF_ID, ActiveGui.TARGET_ID))
                .register(new ViewSubcommand(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete, "expired", ExpiredGui.SELF_ID, ExpiredGui.TARGET_ID))
                .register(new ViewSubcommand(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete, "storage", StorageGui.SELF_ID, StorageGui.TARGET_ID))
                .register(new ViewSubcommand(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete, "history", HistoryGui.SELF_ID, HistoryGui.TARGET_ID))
                .register(new SubSearch(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete))
                .register(new SubPlayer(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis(), tabComplete));
        this.dispatcher = new CommandDispatcher(plugin.messenger(), plugin.configs());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        dispatcher.dispatch(registry, sender, label, args, isShortcut(label), () -> onNoMatch(sender, label, args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return dispatcher.tabComplete(registry, sender, args);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (event.isHandled() || !event.isCommand() || !(event.getSender() instanceof Player player)) {
            return;
        }

        Optional<String[]> args = parseBuffer(event.getBuffer());
        if (args.isEmpty()) {
            return;
        }

        try {
            event.setCompletions(dispatcher.tabComplete(registry, player, args.get()));
            event.setHandled(true);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Async tab-complete for " + player.getName() + " failed, falling back to sync: " + e);
        }
    }

    private void onNoMatch(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openGui(player);
            } else {
                plugin.messenger().send(sender, plugin.configs().lang().get("errors.access.player-only"));
            }
            return;
        }

        String usage = CommandDispatch.format(plugin.configs().commands().main().usage(), label, null);
        plugin.messenger().send(sender, plugin.configs().lang().get("errors.access.incorrect-usage"), Map.of("usage", usage));
    }

    private void openGui(Player player) {
        GuiManager guis = plugin.guis();
        boolean categoriesEnabled = guis.definition("browsing/categories")
                .map(definition -> definition.settings().enabled())
                .orElse(false);
        guis.open(player, categoriesEnabled ? "browsing/categories" : "browsing/global", new HashMap<>());
    }

    private boolean isShortcut(String label) {
        return !Shortcuts.isRoot(plugin.configs().commands().main(), label);
    }

    private Optional<String[]> parseBuffer(String buffer) {
        String withoutSlash = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        int space = withoutSlash.indexOf(' ');
        if (space < 0) {
            return Optional.empty();
        }

        String label = withoutSlash.substring(0, space);
        String[] typedArgs = withoutSlash.substring(space + 1).split(" ", -1);

        RootCommand main = plugin.configs().commands().main();
        return Shortcuts.resolveArgs(main, plugin.configs().commands().shortcuts(), label, typedArgs);
    }
}