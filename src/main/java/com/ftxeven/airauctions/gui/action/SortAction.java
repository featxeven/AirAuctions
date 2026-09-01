package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.action.CyclerAction;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * [sort] by:active|unclaimed|history to:next|previous|first|last|N
 */
public final class SortAction extends CyclerAction {

    private final ConfigManager configs;
    private final ListingGuiManager guis;

    public SortAction(ConfigManager configs, ListingGuiManager guis) {
        super("sort");
        this.configs = configs;
        this.guis = guis;
    }

    @Override
    protected @Nullable String attributeFor(String dimension) {
        String key = dimension.toLowerCase(Locale.ROOT);
        return BaseGui.SORT_DIMENSIONS.contains(key) ? BaseGui.sortAttribute(key) : null;
    }

    @Override
    protected @Nullable List<String> optionsFor(GuiSession session, String dimension) {
        String key = dimension.toLowerCase(Locale.ROOT);

        String live = session.attribute(BaseGui.ATTR_SORT_DIMENSION, String.class);
        if (!key.equals(live)) {
            return null; // not the dimension this GUI is actually sorting by right now
        }

        LayoutConfig.Cycler sorts = guis.layout(session.definition()).sorts();
        if (sorts.isExcluded(key)) {
            return null;
        }

        return List.copyOf(configs.main().listings().sort().options(key).keySet());
    }
}