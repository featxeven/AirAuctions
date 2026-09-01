package com.ftxeven.airauctions.core.gui.render;

import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.Placeholders;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public final class ItemBuilder {

    private final MaterialResolver materials;
    private final Messenger messenger;
    private final Logger logger;
    private final FlagGate flags;

    public ItemBuilder(MaterialResolver materials, Messenger messenger, Logger logger, FlagGate flags) {
        this.materials = materials;
        this.messenger = messenger;
        this.logger = logger;
        this.flags = flags;
    }

    public ItemStack build(ItemConfig.Fields fields, Player viewer, Map<String, String> placeholders,
                           Function<String, String> flagResolver, boolean trimLore, String context, long startTick, @Nullable ItemStack baseItem) {
        ItemStack stack = resolveBaseStack(fields, viewer, placeholders, context, baseItem);
        if (fields.amount() != null) {
            stack.setAmount(Math.max(1, fields.amount()));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            applyMeta(meta, fields, viewer, placeholders, flagResolver, trimLore, context, startTick);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack resolveBaseStack(ItemConfig.Fields fields, Player viewer, Map<String, String> placeholders, String context, @Nullable ItemStack baseItem) {
        if (fields.material() == null) {
            return baseItem != null ? baseItem.clone() : new ItemStack(Material.STONE);
        }
        String material = Placeholders.apply(viewer, fields.material(), placeholders);
        ItemStack resolved = materials.resolve(material, context);
        return resolved != null ? resolved : new ItemStack(Material.STONE);
    }

    private void applyMeta(ItemMeta meta, ItemConfig.Fields fields, Player viewer, Map<String, String> placeholders, Function<String, String> flagResolver, boolean trimLore, String context, long startTick) {
        if (fields.displayName() != null) {
            meta.displayName(messenger.renderLine(viewer, fields.displayName(), placeholders, startTick));
        }
        if (fields.lore() != null) {
            List<String> gated = gateLore(fields.lore(), flagResolver);
            List<String> expanded = expandMultilineValues(gated, viewer, placeholders);
            List<String> lore = trimLore ? trimLoreLines(expanded) : expanded;
            meta.lore(messenger.renderLines(viewer, lore, placeholders, startTick));
        }

        // flags
        if (Boolean.TRUE.equals(fields.hideTooltip())) {
            meta.setHideTooltip(true);
        }
        if (Boolean.TRUE.equals(fields.unbreakable())) {
            meta.setUnbreakable(true);
        }
        if (Boolean.TRUE.equals(fields.glow())) {
            meta.setEnchantmentGlintOverride(true);
        }

        // model / tooltip
        if (fields.customModelData() != null) {
            Integer customModelData = materials.resolveCustomModelData(fields.customModelData(), context);
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
        }
        if (fields.itemModel() != null) {
            NamespacedKey itemModel = materials.resolveItemModel(fields.itemModel(), context);
            if (itemModel != null) {
                meta.setItemModel(itemModel);
            } else {
                logger.warning("Invalid item-model '" + fields.itemModel() + "' in " + context);
            }
        }
        if (fields.tooltipStyle() != null) {
            applyKey(fields.tooltipStyle(), "tooltip-style", context, meta::setTooltipStyle);
        }

        // item flags / enchants
        if (fields.itemFlags() != null) {
            for (String flag : fields.itemFlags()) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    logger.warning("Unknown item-flag '" + flag + "' in " + context);
                }
            }
        }
        if (fields.enchants() != null) {
            applyEnchants(meta, fields.enchants(), context);
        }

        // meta-type-specific
        if (fields.damage() != null && meta instanceof Damageable damageable) {
            damageable.setDamage(fields.damage());
        }
        if (fields.leatherColor() != null && meta instanceof LeatherArmorMeta leather) {
            applyColor(fields.leatherColor(), context, leather::setColor);
        }
        if (fields.potionColor() != null && meta instanceof PotionMeta potion) {
            applyColor(fields.potionColor(), context, potion::setColor);
        }
    }

    private List<String> expandMultilineValues(List<String> lines, Player viewer, Map<String, String> placeholders) {
        List<String> expanded = new ArrayList<>(lines.size());
        for (String line : lines) {
            String substituted = Placeholders.apply(viewer, line, placeholders);
            for (String part : substituted.split("\n", -1)) {
                if (!part.isEmpty()) {
                    expanded.add(part);
                }
            }
        }
        return expanded;
    }

    private List<String> gateLore(List<String> lines, Function<String, String> flagResolver) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            flags.apply(line, flagResolver).ifPresent(result::add);
        }
        return result;
    }

    private void applyKey(String raw, String fieldName, String context, Consumer<NamespacedKey> setter) {
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        if (key != null) {
            setter.accept(key);
        } else {
            logger.warning("Invalid " + fieldName + " '" + raw + "' in " + context);
        }
    }

    private void applyColor(String hex, String context, Consumer<Color> setter) {
        Color color = parseColor(hex, context);
        if (color != null) {
            setter.accept(color);
        }
    }

    private void applyEnchants(ItemMeta meta, List<String> entries, String context) {
        for (String entry : entries) {
            int sep = entry.indexOf(':');
            String id = (sep < 0 ? entry : entry.substring(0, sep)).trim().toLowerCase(Locale.ROOT);
            int level = 1;
            if (sep >= 0) {
                try {
                    level = Integer.parseInt(entry.substring(sep + 1).trim());
                } catch (NumberFormatException e) {
                    logger.warning("Invalid enchant level in '" + entry + "' in " + context + ", using 1");
                }
            }
            Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(id));
            if (enchant == null) {
                logger.warning("Unknown enchantment '" + id + "' in " + context);
                continue;
            }
            meta.addEnchant(enchant, level, true);
        }
    }

    private Color parseColor(String hex, String context) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (Exception e) {
            logger.warning("Invalid hex color '" + hex + "' in " + context);
            return null;
        }
    }

    private List<String> trimLoreLines(List<String> lore) {
        List<String> withoutEmpty = new ArrayList<>(lore.size());
        for (String line : lore) {
            if (!line.isEmpty()) {
                withoutEmpty.add(line);
            }
        }

        int start = 0;
        int end = withoutEmpty.size();
        while (start < end && withoutEmpty.get(start).isBlank()) start++;
        while (end > start && withoutEmpty.get(end - 1).isBlank()) end--;
        return withoutEmpty.subList(start, end);
    }
}