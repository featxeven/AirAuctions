package com.ftxeven.airauctions.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

public final class DynamicCommandRegistry {

    private final JavaPlugin plugin;
    private final CommandMap commandMap;

    public DynamicCommandRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.commandMap = resolveCommandMap();
    }

    public <T extends CommandExecutor & TabCompleter> void register(String name, List<String> aliases, T handler) {
        register(name, aliases, handler, UnaryOperator.identity());
    }

    public <T extends CommandExecutor & TabCompleter> void register(String name, List<String> aliases, T handler, UnaryOperator<String[]> argsTransform) {
        if (commandMap == null) {
            plugin.getLogger().severe("Could not register command '" + name + "' - the server's command map is unavailable");
            return;
        }

        Command command = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return handler.onCommand(sender, this, label, argsTransform.apply(args));
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String label, String[] args) {
                List<String> completions = handler.onTabComplete(sender, this, label, argsTransform.apply(args));
                return completions != null ? completions : List.of();
            }
        };
        command.setAliases(aliases);
        commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
    }

    private CommandMap resolveCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().severe("Could not access the server's command map: " + e.getMessage());
            return null;
        }
    }
}