package com.ftxeven.airauctions.core.gui.util;

import com.ftxeven.airauctions.core.gui.GuiDefinition;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.gui.ItemPriority;
import com.ftxeven.airauctions.core.gui.PageableHolder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.Map;

public final class GuiItemFinder {

    public static GuiItem find(GuiDefinition definition, int slot, Player viewer, Map<String, String> context, PageableHolder holder) {
        GuiItem btn = findInLayer(definition, definition.config().getConfigurationSection("buttons"), slot, viewer, context, holder);
        if (btn != null) return btn;

        return findInLayer(definition, definition.config().getConfigurationSection("items"), slot, viewer, context, holder);
    }

    private static GuiItem findInLayer(GuiDefinition definition, ConfigurationSection section, int slot, Player viewer, Map<String, String> context, PageableHolder holder) {
        if (section == null) return null;

        for (String key : section.getKeys(false)) {
            GuiItem item = definition.items().get(key);

            if (item == null || !item.slots().contains(slot) || GuiSlotMapper.isButtonHidden(item.key(), holder, definition)) {
                continue;
            }

            if (item.priorities().isEmpty()) return item;
            for (ItemPriority p : item.priorities().values()) {
                if (p.matches(viewer, context)) return item;
            }
        }
        return null;
    }
}