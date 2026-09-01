package com.ftxeven.airauctions.core.command;

import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition;

import java.util.List;
import java.util.Map;

public record DynamicCommand(
        boolean enabled,
        String name,
        List<String> aliases,
        String usage,
        String usageOthers,
        Map<String, String> actions,
        Map<Integer, TabPosition> tabComplete
) {
    public DynamicCommand {
        aliases = List.copyOf(aliases);
    }

    public static DynamicCommand disabled(String key) {
        return new DynamicCommand(false, key, List.of(), "", "", Map.of(), Map.of());
    }
}