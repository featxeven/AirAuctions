package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.action.CyclerAction;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.gui.render.FilterOptions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [filter] by:category|type|economy to:next|previous|first|last|N
 */
public final class FilterAction extends CyclerAction {

    private final ConfigManager configs;
    private final ListingGuiManager guis;

    public FilterAction(ConfigManager configs, ListingGuiManager guis) {
        super("filter");
        this.configs = configs;
        this.guis = guis;
    }

    @Override
    protected @Nullable String attributeFor(String dimension) {
        String key = dimension.toLowerCase(Locale.ROOT);
        return BaseGui.FILTER_DIMENSIONS.contains(key) ? BaseGui.filterAttribute(key) : null;
    }

    @Override
    protected @Nullable List<String> optionsFor(GuiSession session, String dimension) {
        String key = dimension.toLowerCase(Locale.ROOT);

        LayoutConfig.Cycler filters = guis.layout(session.definition()).filters();
        if (filters.isExcluded(key)) {
            return null; // this GUI's layout doesn't offer this filter dimension
        }

        Map<String, String> options = switch (key) {
            case "category" -> FilterOptions.category(configs);
            case "type" -> FilterOptions.type(configs);
            case "economy" -> FilterOptions.economy(configs, true);
            default -> null;
        };
        return options != null ? List.copyOf(options.keySet()) : null;
    }
}