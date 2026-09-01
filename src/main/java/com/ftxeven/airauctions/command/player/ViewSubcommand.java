package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.command.CommandDispatch;
import com.ftxeven.airauctions.core.command.DynamicCommand;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.OpenOptions;
import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.model.PlayerData;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ViewSubcommand implements SubCommand {

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final GuiManager guis;
    private final TabCompleteEngine tabComplete;

    private final String key;
    private final String selfId;
    private final String targetId;
    private final String othersPermission;
    private final DynamicCommand disabled;

    public ViewSubcommand(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis,
                          TabCompleteEngine tabComplete, String key, String selfId, String targetId) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.guis = guis;
        this.tabComplete = tabComplete;
        this.key = key;
        this.selfId = selfId;
        this.targetId = targetId;
        this.othersPermission = Permissions.commandOthers(key);
        this.disabled = new DynamicCommand(false, key, List.of(), "", "", Map.of(), Map.of());
    }

    @Override
    public String name() { return config().name(); }

    @Override
    public List<String> aliases() { return config().aliases(); }

    @Override
    public boolean enabled() { return config().enabled(); }

    @Override
    public String permission() { return Permissions.command(key); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int maxArgs(CommandSender sender) {
        return sender.hasPermission(othersPermission) ? 1 : 0;
    }

    @Override
    public String usage() { return config().usage(); }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), sender.hasPermission(othersPermission));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase(player.getName())) {
            guis.open(player, selfId, new HashMap<>(), OpenOptions.DEFAULT);
            return;
        }

        if (!sender.hasPermission(othersPermission)) {
            messenger.send(sender, configs.lang().get("errors.access.no-permission"), Map.of("permission", othersPermission));
            return;
        }

        String targetName = args[0];
        Optional<PlayerData> target = services.players().findByName(targetName);
        if (target.isEmpty()) {
            messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", targetName));
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("seller", target.get().name());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GuiSession.ATTR_TARGET, target.get().uuid());

        guis.open(player, targetId, placeholders, new OpenOptions(FlagGate.NO_FLAGS, attributes, OpenOptions.Kind.ENTRY, List.of()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return tabComplete.complete(sender, config(), args);
    }

    private DynamicCommand config() {
        DynamicCommand command = configs.commands().subcommands().get(key);
        return command != null ? command : disabled;
    }
}