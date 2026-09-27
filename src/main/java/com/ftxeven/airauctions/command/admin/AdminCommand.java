package com.ftxeven.airauctions.command.admin;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.command.CommandDispatcher;
import com.ftxeven.airauctions.core.command.CommandRegistry;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.List;

public final class AdminCommand implements BasicCommand {

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
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        dispatcher.dispatch(registry, commandSourceStack.getSender(), "airauctions", args, () -> sendUsage(commandSourceStack.getSender()));
    }

    @Override
    public List<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        return dispatcher.tabComplete(registry, commandSourceStack.getSender(), args);
    }

    @Override
    public String permission() {
        return "airauctions.admin";
    }

    private void sendUsage(org.bukkit.command.CommandSender sender) {
        plugin.messenger().send(sender, plugin.configs().lang().get("general.commands.usage"));
    }
}