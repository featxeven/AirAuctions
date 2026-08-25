package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.config.LangConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public final class ItemDisplay {

    private ItemDisplay() {
    }

    public static String name(ItemStack item, LangConfig lang) {
        Component displayName = customDisplayName(item);
        return displayName != null ? MiniText.mini().serialize(displayName) : lang.item(item.getType().getKey().getKey());
    }

    // Writes %key% (with tooltip) and %key_no_tooltip%
    public static void formatInto(Map<String, String> placeholders, String key, ItemStack item, LangConfig lang) {
        String formatted = name(item, lang);
        placeholders.put(key, withTooltip(item, formatted));
        placeholders.put(key + "_no_tooltip", formatted);
    }

    public static String plainName(ItemStack item, LangConfig lang) {
        Component displayName = customDisplayName(item);
        return displayName != null ? MiniText.plain(displayName) : lang.item(item.getType().getKey().getKey());
    }

    private static String withTooltip(ItemStack item, String formatted) {
        try {
            Component styled = MiniText.mini().deserialize(formatted)
                    .hoverEvent(item.asHoverEvent());
            return MiniText.mini().serialize(styled);
        } catch (IllegalArgumentException e) {
            return formatted;
        }
    }

    private static Component customDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.displayName() : null;
    }
}