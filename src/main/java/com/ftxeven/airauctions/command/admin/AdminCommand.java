package com.ftxeven.airauctions.command.admin;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.command.CommandDispatcher;
import com.ftxeven.airauctions.core.command.CommandRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final CommandRegistry registry;
    private final CommandDispatcher dispatcher;
    private final AirAuctions plugin;

    public AdminCommand(AirAuctions plugin) {
        this.plugin = plugin;

        this.registry = new CommandRegistry()
                .register(new SubReload(plugin, plugin.messenger(), plugin.configs()))
                .register(new SubVersion(plugin, plugin.messenger(), plugin.configs()))
                .register(new SubSimulate(plugin.messenger(), plugin.configs(), plugin.services(), plugin.getLogger()))
                .register(new SubOpen(plugin.configs(), plugin.messenger(), plugin.services(), plugin.guis()));
        this.dispatcher = new CommandDispatcher(plugin.messenger(), plugin.configs());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        dispatcher.dispatch(registry, sender, label, args, () -> sendUsage(sender));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return dispatcher.tabComplete(registry, sender, args);
    }

    private void sendUsage(CommandSender sender) {
        plugin.messenger().send(sender, plugin.configs().lang().get("general.commands.usage"));
    }
}