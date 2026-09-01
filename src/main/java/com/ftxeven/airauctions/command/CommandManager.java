package com.ftxeven.airauctions.command;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.command.admin.AdminCommand;
import com.ftxeven.airauctions.command.player.PlayerCommand;
import com.ftxeven.airauctions.core.command.DurationUnits;
import com.ftxeven.airauctions.core.command.DynamicCommandRegistry;
import com.ftxeven.airauctions.core.command.RootCommand;
import com.ftxeven.airauctions.core.command.Shortcuts;
import com.ftxeven.airauctions.core.command.Shortcuts.Shortcut;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.command.tabcomplete.TabSourceRegistry;
import com.ftxeven.airauctions.core.condition.ConditionEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

public final class CommandManager {

    private final AirAuctions plugin;
    private final DynamicCommandRegistry registry;

    public CommandManager(AirAuctions plugin) {
        this.plugin = plugin;
        this.registry = new DynamicCommandRegistry(plugin);
    }

    public void registerAll() {
        registerAdminCommand();
        registerPlayerCommand();
    }

    private void registerAdminCommand() {
        PluginCommand command = plugin.getCommand("airauctions");

        AdminCommand executor = new AdminCommand(plugin);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerPlayerCommand() {
        RootCommand main = plugin.configs().commands().main();
        DurationUnits durationUnits = new DurationUnits(() -> plugin.configs().commands().durationUnits());
        PlayerCommand executor = new PlayerCommand(plugin, buildTabCompleteEngine(durationUnits), durationUnits);

        registry.register(main.name(), main.aliases(), executor);
        registerShortcuts(executor);

        Bukkit.getPluginManager().registerEvents(executor, plugin);
    }

    private void registerShortcuts(PlayerCommand auctionHouse) {
        for (String key : plugin.configs().commands().shortcuts().keySet()) {
            registry.register(key, shortcut(key).aliases(), auctionHouse,
                    typedArgs -> Shortcuts.merge(Shortcuts.virtualArgs(shortcut(key)), typedArgs));
        }
    }

    private Shortcut shortcut(String key) {
        return plugin.configs().commands().shortcuts().get(key);
    }

    private TabCompleteEngine buildTabCompleteEngine(DurationUnits durationUnits) {
        TabSourceRegistry sources = TabSources.build(plugin.services(), plugin.configs(), plugin.guis(), durationUnits);
        ConditionEvaluator conditions = new ConditionEvaluator(plugin.getLogger()::warning);
        return new TabCompleteEngine(sources, conditions);
    }
}