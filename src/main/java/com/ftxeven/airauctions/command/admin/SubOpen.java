package com.ftxeven.airauctions.command.admin;

import com.ftxeven.airauctions.command.CommandDispatcher;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.config.ConfigManager;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SubOpen implements SubCommand {

    static final String USAGE = "/airauctions open <gui-id> <player> [category] [type] [economy] [sort] [target]";

    private static final int MAX_SUGGESTIONS = 20;

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final GuiManager guis;

    public SubOpen(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.guis = guis;
    }

    @Override
    public String name() {
        return "open";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public int maxArgs() {
        return 7;
    }

    @Override
    public String usage() {
        return USAGE;
    }

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
        List<String> candidates = switch (args.length) {
            case 1 -> List.copyOf(guis.ids());
            case 2, 7 -> onlinePlayerNames();
            case 3 -> List.copyOf(configs.filter().categories().keySet());
            case 4 -> List.copyOf(configs.main().listings().filterTypes().keySet());
            case 5 -> enabledEconomyKeys();
            case 6 -> allSortKeys();
            default -> List.of();
        };

        List<String> matches = CommandDispatcher.filterPrefix(candidates, args[args.length - 1]);
        return matches.size() > MAX_SUGGESTIONS ? matches.subList(0, MAX_SUGGESTIONS) : matches;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> enabledEconomyKeys() {
        List<String> keys = new ArrayList<>();
        configs.expansions().economy().providers().forEach((key, provider) -> {
            if (provider.enabled()) {
                keys.add(key);
            }
        });
        return keys;
    }

    private List<String> allSortKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(configs.main().listings().sort().active().keySet());
        keys.addAll(configs.main().listings().sort().unclaimed().keySet());
        keys.addAll(configs.main().listings().sort().history().keySet());
        return List.copyOf(keys);
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String[] args, int index) {
        if (index < args.length) {
            attributes.put(key, args[index]);
        }
    }
}