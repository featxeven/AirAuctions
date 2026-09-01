package com.ftxeven.airauctions.service.listing.match;

import com.ftxeven.airauctions.core.hook.HookRegistry;
import com.ftxeven.airauctions.model.MatchRules;
import com.ftxeven.airauctions.util.MiniText;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemMatcher {

    private final HookRegistry hooks;

    public ItemMatcher(HookRegistry hooks) {
        this.hooks = hooks;
    }

    public boolean matches(ItemStack item, MatchRules rules) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return matchesMaterial(item, rules.materials())
                || matchesName(meta, rules.names())
                || matchesLore(meta, rules.lores())
                || matchesEnchantments(item, rules.enchantments())
                || matchesNbtKeys(meta, rules.nbtKeys())
                || matchesCustomModelData(item, meta, rules.customModelData())
                || matchesItemModel(meta, rules.itemModels())
                || matchesPluginItem(item, rules.pluginItems());
    }

    // Rule checks

    private boolean matchesMaterial(ItemStack item, List<String> materials) {
        if (materials.isEmpty()) {
            return false;
        }
        String type = item.getType().name();
        return materials.stream().anyMatch(type::equalsIgnoreCase);
    }

    private boolean matchesName(ItemMeta meta, List<String> names) {
        if (names.isEmpty() || meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return containsAny(MiniText.plain(meta.displayName()), names);
    }

    private boolean matchesLore(ItemMeta meta, List<String> lores) {
        if (lores.isEmpty() || meta == null || !meta.hasLore()) {
            return false;
        }
        for (Component line : meta.lore()) {
            if (containsAny(MiniText.plain(line), lores)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEnchantments(ItemStack item, List<String> enchantments) {
        if (enchantments.isEmpty()) {
            return false;
        }
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            String key = entry.getKey().getKey().getKey();
            int level = entry.getValue();
            for (String rule : enchantments) {
                int sep = rule.indexOf(':');
                if (sep < 0) {
                    if (key.equalsIgnoreCase(rule)) return true;
                } else if (key.equalsIgnoreCase(rule.substring(0, sep)) && level == parseLevel(rule.substring(sep + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int parseLevel(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean matchesNbtKeys(ItemMeta meta, List<String> nbtKeys) {
        if (nbtKeys.isEmpty() || meta == null) {
            return false;
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            String bare = key.getKey();
            String full = key.getNamespace() + ":" + bare;
            for (String rule : nbtKeys) {
                if (rule.equalsIgnoreCase(full) || (!rule.contains(":") && rule.equalsIgnoreCase(bare))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesCustomModelData(ItemStack item, ItemMeta meta, List<String> values) {
        if (values.isEmpty() || meta == null || !meta.hasCustomModelData()) {
            return false;
        }
        String data = String.valueOf(meta.getCustomModelData());
        String material = item.getType().name();
        for (String rule : values) {
            int sep = rule.indexOf(':');
            if (sep < 0) {
                if (data.equals(rule)) return true;
            } else if (material.equalsIgnoreCase(rule.substring(0, sep)) && data.equals(rule.substring(sep + 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesItemModel(ItemMeta meta, List<String> itemModels) {
        if (itemModels.isEmpty() || meta == null || meta.getItemModel() == null) {
            return false;
        }
        String model = meta.getItemModel().asString();
        return itemModels.stream().anyMatch(model::equalsIgnoreCase);
    }

    private boolean matchesPluginItem(ItemStack item, List<String> pluginItems) {
        if (pluginItems.isEmpty()) {
            return false;
        }
        String id = hooks.identify(item);
        return id != null && pluginItems.stream().anyMatch(id::equalsIgnoreCase);
    }

    // Text helpers

    private boolean containsAny(String haystack, List<String> lowercasedNeedles) {
        String lower = haystack.toLowerCase(Locale.ROOT);
        return lowercasedNeedles.stream().anyMatch(lower::contains);
    }
}