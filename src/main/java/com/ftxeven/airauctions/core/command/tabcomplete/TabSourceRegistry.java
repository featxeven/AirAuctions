package com.ftxeven.airauctions.core.command.tabcomplete;

import com.ftxeven.airauctions.core.command.DurationUnits;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TabSourceRegistry {

    private final Map<String, TabSource> sources = new HashMap<>();

    public TabSourceRegistry register(String token, TabSource source) {
        sources.put(token.toUpperCase(Locale.ROOT), source);
        return this;
    }

    public Optional<TabSource> find(String token) {
        return Optional.ofNullable(sources.get(token.toUpperCase(Locale.ROOT)));
    }

    public static TabSourceRegistry withBuiltins(DurationUnits durationUnits) {
        return new TabSourceRegistry()
                .register("ONLINE_PLAYERS", (context, param) -> {
                    int cap = cap(param, Integer.MAX_VALUE);
                    List<String> names = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        names.add(player.getName());
                    }
                    return filterAndCap(names, partial(context), cap);
                })
                .register("ACTIONS", (context, param) -> List.copyOf(context.actions().values()))
                .register("DURATION_UNITS", (context, param) -> durationUnits.keys(param));
    }

    public static int cap(String param, int fallback) {
        if (param == null || param.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // the value currently being typed
    public static String partial(TabSource.Context context) {
        String[] args = context.args();
        return args.length > 0 ? args[args.length - 1] : "";
    }

    // filters candidates against what's typed BEFORE capping
    public static List<String> filterAndCap(List<String> candidates, String partial, int cap) {
        String lower = partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (matches.size() >= cap) {
                break;
            }
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}