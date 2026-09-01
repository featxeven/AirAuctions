package com.ftxeven.airauctions.command;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.command.CommandDispatch;
import com.ftxeven.airauctions.core.command.CommandRegistry;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CommandDispatcher {

    private final Messenger messenger;
    private final ConfigManager configs;

    public CommandDispatcher(Messenger messenger, ConfigManager configs) {
        this.messenger = messenger;
        this.configs = configs;
    }

    public void dispatch(CommandRegistry registry, CommandSender sender, String label, String[] args, Runnable onNoMatch) {
        dispatch(registry, sender, label, args, false, onNoMatch);
    }

    public void dispatch(CommandRegistry registry, CommandSender sender, String label, String[] args, boolean viaShortcut, Runnable onNoMatch) {
        if (args.length == 0) {
            onNoMatch.run();
            return;
        }

        Optional<SubCommand> matched = registry.match(args[0]);
        if (matched.isEmpty()) {
            onNoMatch.run();
            return;
        }

        run(matched.get(), sender, label, viaShortcut ? null : args[0], tail(args));
    }

    private void run(SubCommand subCommand, CommandSender sender, String label, String subLabel, String[] args) {
        String permission = subCommand.permission();
        if (permission != null && !sender.hasPermission(permission)) {
            messenger.send(sender, configs.lang().get("errors.access.no-permission"), Map.of("permission", permission));
            return;
        }

        if (subCommand.playerOnly() && !(sender instanceof Player)) {
            messenger.send(sender, configs.lang().get("errors.access.player-only"));
            return;
        }

        if (args.length < subCommand.minArgs(sender)) {
            sendUsageError(sender, "errors.access.incorrect-usage", subCommand, label, subLabel);
            return;
        }

        int maxArgs = subCommand.maxArgs(sender);
        if (maxArgs >= 0 && args.length > maxArgs && configs.main().general().strictArgs()) {
            sendUsageError(sender, "errors.access.too-many-arguments", subCommand, label, subLabel);
            return;
        }

        subCommand.execute(sender, label, subLabel, args);
    }

    private void sendUsageError(CommandSender sender, String langKey, SubCommand subCommand, String label, String subLabel) {
        String usage = CommandDispatch.format(subCommand.usage(sender), label, subLabel);
        messenger.send(sender, configs.lang().get(langKey), Map.of("usage", usage));
    }

    public List<String> tabComplete(CommandRegistry registry, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return matchingNames(registry, sender, args[0]);
        }

        return registry.match(args[0])
                .map(subCommand -> subCommand.tabComplete(sender, tail(args)))
                .orElse(List.of());
    }

    private List<String> matchingNames(CommandRegistry registry, CommandSender sender, String prefix) {
        List<String> candidates = new ArrayList<>();
        for (SubCommand subCommand : registry.all()) {
            if (!subCommand.enabled() || (subCommand.permission() != null && !sender.hasPermission(subCommand.permission()))) {
                continue;
            }
            candidates.add(subCommand.name());
            candidates.addAll(subCommand.aliases());
        }
        return filterPrefix(candidates, prefix);
    }

    public static List<String> filterPrefix(List<String> candidates, String typed) {
        String lowerTyped = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowerTyped)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static String[] tail(String[] args) {
        return args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    }
}