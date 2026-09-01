package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.command.CommandDispatch;
import com.ftxeven.airauctions.core.command.DynamicCommand;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.model.PlayerData;
import com.ftxeven.airauctions.permission.PermissionTiers;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.player.PlayerService;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SubSlots implements SubCommand {

    private static final String KEY = "slots";

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final TabCompleteEngine tabComplete;

    public SubSlots(ConfigManager configs, Messenger messenger, ServiceManager services, TabCompleteEngine tabComplete) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.tabComplete = tabComplete;
    }

    @Override
    public String name() {
        return config().name();
    }

    @Override
    public List<String> aliases() {
        return config().aliases();
    }

    @Override
    public boolean enabled() {
        return config().enabled();
    }

    @Override
    public String permission() {
        return Permissions.command(KEY);
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 3;
    }

    @Override
    public String usage() {
        return config().usage();
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        DynamicCommand config = config();

        String action = resolveAction(config, args[0]);
        if (action == null) {
            sendIncorrectUsage(sender, config, label, subLabel);
            return;
        }

        Integer amount = parseAmount(args[1]);
        if (amount == null) {
            messenger.send(sender, configs.lang().get("listings.slots.errors.invalid-amount"));
            return;
        }

        if (args.length < 3 && !(sender instanceof Player)) {
            sendIncorrectUsage(sender, config, label, subLabel);
            return;
        }

        String targetName = args.length >= 3 ? args[2] : ((Player) sender).getName();
        Optional<PlayerData> target = services.players().findByName(targetName);
        if (target.isEmpty()) {
            messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", targetName));
            return;
        }

        apply(sender, action, amount, target.get());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return tabComplete.complete(sender, config(), args);
    }

    // Execution

    private void apply(CommandSender sender, String action, int amount, PlayerData target) {
        int delta = action.equals("give") ? amount : -amount;
        PlayerService.SlotAdjustment result = services.players().adjustExtraSlots(target.uuid(), delta);
        int total = services.validator().maxActiveListings(target.uuid());

        String admin = CommandDispatch.senderName(sender, configs.lang().get("general.console-name").getFirst());
        Map<String, String> placeholders = Map.of(
                "player", target.name(),
                "admin", admin,
                "amount", String.valueOf(result.applied()),
                "extra", String.valueOf(result.total()),
                "total", PermissionTiers.display(total, configs.lang()));

        messenger.send(sender, configs.lang().get("listings.slots." + action + ".success-admin"), placeholders);
        notifyTarget(sender, action, target, placeholders);
    }

    private void notifyTarget(CommandSender sender, String action, PlayerData target, Map<String, String> placeholders) {
        if (!CommandDispatch.targetFeedbackAllowed(sender, target.uuid(), configs.main().general().consoleFeedback())) {
            return;
        }
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            messenger.send(online, configs.lang().get("listings.slots." + action + ".success-player"), placeholders);
        }
    }

    // Helpers

    private String resolveAction(DynamicCommand config, String typed) {
        for (Map.Entry<String, String> entry : config.actions().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(typed)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Integer parseAmount(String raw) {
        try {
            int amount = Integer.parseInt(raw);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendIncorrectUsage(CommandSender sender, DynamicCommand config, String label, String subLabel) {
        String usage = CommandDispatch.format(config.usage(), label, subLabel);
        messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage));
    }

    private DynamicCommand config() {
        return configs.commands().findSubcommandOrDisabled(KEY);
    }
}