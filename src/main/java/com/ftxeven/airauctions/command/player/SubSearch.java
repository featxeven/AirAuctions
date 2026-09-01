package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.command.DynamicCommand;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.OpenOptions;
import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.impl.SearchGui;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.ValidationResult;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SubSearch implements SubCommand {

    private static final String KEY = "search";

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final GuiManager guis;
    private final TabCompleteEngine tabComplete;

    public SubSearch(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis, TabCompleteEngine tabComplete) {
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
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        String normalized = services.search().normalize(String.join(" ", args));
        ValidationResult validation = services.search().validate(normalized);
        if (validation instanceof ValidationResult.QueryTooLong tooLong) {
            messenger.send(player, configs.lang().get("errors.limits.search-too-long"), Map.of(
                    "length", String.valueOf(tooLong.length()),
                    "limit", String.valueOf(tooLong.limit())
            ));
            return;
        }

        Map<String, Object> attributes = new HashMap<>();
        if (normalized != null) {
            attributes.put(BaseGui.ATTR_SEARCH_QUERY, normalized);
        }

        guis.open(player, SearchGui.ID, new HashMap<>(), new OpenOptions(FlagGate.NO_FLAGS, attributes, OpenOptions.Kind.ENTRY, List.of()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return tabComplete.complete(sender, config(), args);
    }

    private DynamicCommand config() {
        return configs.commands().findSubcommandOrDisabled(KEY);
    }
}