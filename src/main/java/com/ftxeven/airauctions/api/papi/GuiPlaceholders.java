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

import java.util.Map;
import java.util.UUID;

final class GuiPlaceholders {

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

        return switch (key) {
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
            case "filter_category" -> filterName(session, BaseGui.ATTR_FILTER_CATEGORY, FilterOptions.category(configs));
            case "filter_category_id" -> attribute(session, BaseGui.ATTR_FILTER_CATEGORY);
            case "filter_type" -> filterName(session, BaseGui.ATTR_FILTER_TYPE, FilterOptions.type(configs));
            case "filter_type_id" -> attribute(session, BaseGui.ATTR_FILTER_TYPE);
            case "filter_economy" -> filterName(session, BaseGui.ATTR_FILTER_ECONOMY, FilterOptions.economy(configs, true));
            case "filter_economy_id" -> attribute(session, BaseGui.ATTR_FILTER_ECONOMY);
            case "sort_active" -> sortName(session, "active");
            case "sort_active_id" -> attribute(session, BaseGui.sortAttribute("active"));
            case "sort_unclaimed" -> sortName(session, "unclaimed");
            case "sort_unclaimed_id" -> attribute(session, BaseGui.sortAttribute("unclaimed"));
            case "sort_history" -> sortName(session, "history");
            case "sort_history_id" -> attribute(session, BaseGui.sortAttribute("history"));
            default -> null;
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

    private String filterName(GuiSession session, String attributeKey, Map<String, String> options) {
        String id = session.attribute(attributeKey, String.class);
        return id != null ? options.getOrDefault(id, id) : "";
    }

    private String sortName(GuiSession session, String dimension) {
        String id = session.attribute(BaseGui.sortAttribute(dimension), String.class);
        if (id == null) {
            return "";
        }
        return configs.main().listings().sort().options(dimension).getOrDefault(id, id);
    }
}