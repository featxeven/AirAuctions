package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.command.DynamicCommand;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.OpenOptions;
import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.model.PlayerData;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SubOpen implements SubCommand {

    private static final String KEY = "open";

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final GuiManager guis;
    private final TabCompleteEngine tabComplete;

    public SubOpen(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis, TabCompleteEngine tabComplete) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.guis = guis;
        this.tabComplete = tabComplete;
    }

    @Override
    public String name() { return config().name(); }

    @Override
    public List<String> aliases() { return config().aliases(); }

    @Override
    public boolean enabled() { return config().enabled(); }

    @Override
    public String usage() { return config().usage(); }

    @Override
    public String permission() { return Permissions.command(KEY); }

    @Override
    public boolean playerOnly() { return false; }

    @Override
    public int minArgs() { return 2; }

    @Override
    public int maxArgs() { return 7; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String guiId = args[0];
        if (!guis.ids().contains(guiId)) {
            messenger.send(sender, configs.lang().get("errors.access.gui-not-found"), Map.of("gui", guiId));
            return;
        }

        Player viewer = Bukkit.getPlayerExact(args[1]);
        if (viewer == null) {
            messenger.send(sender, configs.lang().get("errors.access.player-not-found"), Map.of("player", args[1]));
            return;
        }

        // filter/sort attribute keys are project-specific (auction dimensions) - still BaseGui's.
        Map<String, Object> attributes = new HashMap<>();
        putIfPresent(attributes, BaseGui.ATTR_FILTER_CATEGORY, args, 2);
        putIfPresent(attributes, BaseGui.ATTR_FILTER_TYPE, args, 3);
        putIfPresent(attributes, BaseGui.ATTR_FILTER_ECONOMY, args, 4);

        if (args.length > 5) {
            String sort = args[5];
            attributes.put(BaseGui.sortAttribute("active"), sort);
            attributes.put(BaseGui.sortAttribute("unclaimed"), sort);
            attributes.put(BaseGui.sortAttribute("history"), sort);
        }

        Map<String, String> placeholders = new HashMap<>();

        if (args.length > 6) {
            Optional<PlayerData> target = services.players().findByName(args[6]);
            if (target.isEmpty()) {
                messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", args[6]));
                return;
            }
            attributes.put(GuiSession.ATTR_TARGET, target.get().uuid());
            placeholders.put("seller", target.get().name());
        }

        guis.open(viewer, guiId, placeholders, new OpenOptions(FlagGate.NO_FLAGS, attributes, OpenOptions.Kind.ENTRY, List.of()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return tabComplete.complete(sender, config(), args);
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String[] args, int index) {
        if (index < args.length) {
            attributes.put(key, args[index]);
        }
    }

    private DynamicCommand config() {
        return configs.commands().findSubcommandOrDisabled(KEY);
    }
}