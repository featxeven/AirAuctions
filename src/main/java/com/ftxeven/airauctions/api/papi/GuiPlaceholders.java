package com.ftxeven.airauctions.api.papi;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.nav.GuiContext;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.render.FilterOptions;
import com.ftxeven.airauctions.service.player.PlayerService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

final class GuiPlaceholders {

    private static final String FILTER_PREFIX = "filter_";
    private static final String SORT_PREFIX = "sort_";
    private static final String ID_SUFFIX = "_id";

    private final GuiManager guis;
    private final ConfigManager configs;
    private final PlayerService players;

    GuiPlaceholders(GuiManager guis, ConfigManager configs, PlayerService players) {
        this.guis = guis;
        this.configs = configs;
        this.players = players;
    }

    @Nullable String resolve(Player viewer, String key) {
        GuiSession session = guis.session(viewer);
        if (session == null) {
            return "";
        }
        String lower = key.toLowerCase(Locale.ROOT);

        return switch (lower) {
            case "id" -> session.definition().id();
            case "previous_id" -> previousId(session);
            case "page" -> String.valueOf(session.page());
            case "pages" -> String.valueOf(session.totalPages());
            case "has_next_page" -> String.valueOf(session.page() < session.totalPages());
            case "has_previous_page" -> String.valueOf(session.page() > 1);
            case "open" -> "true";
            case "target" -> target(session);
            case "target_name" -> targetName(session);
            case "search" -> attribute(session, BaseGui.ATTR_SEARCH_QUERY);
            case "sort_dimension" -> attribute(session, BaseGui.ATTR_SORT_DIMENSION);
            default -> cycler(session, lower);
        };
    }

    private String previousId(GuiSession session) {
        GuiContext back = session.navBack();
        return back != null ? back.screen().guiId() : "";
    }

    private String target(GuiSession session) {
        UUID target = session.target();
        return target != null ? target.toString() : "";
    }

    private String targetName(GuiSession session) {
        UUID target = session.target();
        return target != null ? players.name(target) : "";
    }

    private String attribute(GuiSession session, String attributeKey) {
        String value = session.attribute(attributeKey, String.class);
        return value != null ? value : "";
    }

    // filter_<dimension>[_id] and sort_<dimension>[_id]
    private @Nullable String cycler(GuiSession session, String key) {
        if (key.startsWith(FILTER_PREFIX)) {
            return cyclerValue(session, key.substring(FILTER_PREFIX.length()),
                    GuiPlaceholders::filterAttribute, this::filterOptions);
        }
        if (key.startsWith(SORT_PREFIX)) {
            return cyclerValue(session, key.substring(SORT_PREFIX.length()),
                    GuiPlaceholders::sortAttribute, this::sortOptions);
        }
        return null;
    }

    private @Nullable String cyclerValue(GuiSession session, String rest, Function<String, String> attributeOf,
                                         Function<String, Map<String, String>> optionsOf) {
        boolean idOnly = rest.endsWith(ID_SUFFIX);
        String dimension = idOnly ? rest.substring(0, rest.length() - ID_SUFFIX.length()) : rest;

        String attributeKey = attributeOf.apply(dimension);
        if (attributeKey == null) {
            return null; // not a recognized dimension
        }
        String selected = session.attribute(attributeKey, String.class);
        if (selected == null) {
            return "";
        }
        if (idOnly) {
            return selected;
        }
        Map<String, String> options = optionsOf.apply(dimension);
        return options != null ? options.getOrDefault(selected, selected) : selected;
    }

    // Cycler dimensions

    private static @Nullable String filterAttribute(String dimension) {
        return switch (dimension) {
            case "category" -> BaseGui.ATTR_FILTER_CATEGORY;
            case "type" -> BaseGui.ATTR_FILTER_TYPE;
            case "economy" -> BaseGui.ATTR_FILTER_ECONOMY;
            default -> null;
        };
    }

    private @Nullable Map<String, String> filterOptions(String dimension) {
        return switch (dimension) {
            case "category" -> FilterOptions.category(configs);
            case "type" -> FilterOptions.type(configs);
            case "economy" -> FilterOptions.economy(configs, true);
            default -> null;
        };
    }

    private static @Nullable String sortAttribute(String dimension) {
        return switch (dimension) {
            case "active", "unclaimed", "history" -> BaseGui.sortAttribute(dimension);
            default -> null;
        };
    }

    private Map<String, String> sortOptions(String dimension) {
        return configs.main().listings().sort().options(dimension);
    }
}