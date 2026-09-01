package com.ftxeven.airauctions.core.gui.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Logger;

public final class ItemConfigReader {

    private final Logger logger;

    public ItemConfigReader(Logger logger) {
        this.logger = logger;
    }

    public @Nullable ItemConfig read(ConfigurationSection sec, String key, String guiId, SharedConfig shared, AliasExpander expander, int rows) {
        if (sec == null) {
            return null;
        }
        String context = "GUI '" + guiId + "', item '" + key + "'";

        Set<Integer> slots = SlotParser.parse(sec.get("slots"), context, logger);
        slots = validateSlots(slots, rows * 9, context);
        if (slots.isEmpty()) {
            logger.warning("Item '" + key + "' in GUI '" + guiId + "' has no valid slots, it will never render");
        }

        return new ItemConfig(key, slots, resolveWithTemplate(sec, shared, expander, context));
    }

    private Set<Integer> validateSlots(Set<Integer> slots, int inventorySize, String context) {
        Set<Integer> valid = new LinkedHashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= inventorySize) {
                logger.warning("Slot " + slot + " in " + context + " is out of bounds for a "
                        + (inventorySize / 9) + "-row inventory (0-" + (inventorySize - 1) + "), skipping");
            } else {
                valid.add(slot);
            }
        }
        return valid;
    }

    private @Nullable ItemConfig.Template resolveTemplate(@Nullable String templateName, String context, SharedConfig shared) {
        if (templateName == null) {
            return null;
        }
        ItemConfig.Template template = shared.templates().get(templateName);
        if (template == null) {
            logger.warning("Unknown template '" + templateName + "' referenced by " + context);
        }
        return template;
    }

    public ItemConfig.Template readTemplate(ConfigurationSection sec, AliasExpander expander, String context) {
        ItemConfig.Fields fields = readFields(sec, expander, context);
        List<ItemConfig.PriorityTier> priority = readPriority(sec, expander, context);
        return new ItemConfig.Template(fields, priority);
    }

    public ItemConfig.Template readTemplateRef(ConfigurationSection sec, SharedConfig shared, AliasExpander expander, String context) {
        return resolveWithTemplate(sec, shared, expander, context);
    }

    private ItemConfig.Template resolveWithTemplate(ConfigurationSection sec, SharedConfig shared, AliasExpander expander, String context) {
        ItemConfig.Fields direct = readFields(sec, expander, context);
        ItemConfig.Template template = resolveTemplate(sec.getString("template", null), context, shared);

        ItemConfig.Fields fields = template != null ? template.fields().overlay(direct) : direct;
        List<ItemConfig.PriorityTier> priority = sec.isList("priority")
                ? readPriority(sec, expander, context)
                : (template != null ? template.priority() : List.of());

        return new ItemConfig.Template(fields, priority);
    }

    // raw keys read here (material/display-name/lore/...) must match ItemConfig.FIELD_KEYS
    public ItemConfig.Fields readFields(ConfigurationSection sec, AliasExpander expander, String context) {
        if (sec == null) {
            return ItemConfig.Fields.EMPTY;
        }

        ConfigurationSection actionsSec = sec.getConfigurationSection("actions");
        Map<ItemConfig.ClickType, List<String>> actions = actionsSec != null
                ? parseClickActions(readActionsBlock(actionsSec, expander, context))
                : null;

        ItemConfig.Fields.Animation animation = sec.isConfigurationSection("animation")
                ? readAnimation(sec.getConfigurationSection("animation"), expander, context)
                : null;

        return new ItemConfig.Fields(
                sec.isSet("material") ? expander.expand(sec.getString("material", ""), context + " material") : null,
                sec.isSet("display-name") ? expander.expand(sec.getString("display-name", ""), context + " display-name") : null,
                sec.isSet("lore") ? expander.expandAll(sec.getStringList("lore"), context + " lore") : null,
                sec.isSet("amount") ? sec.getInt("amount") : null,
                sec.isSet("glow") ? sec.getBoolean("glow") : null,
                sec.isSet("hide-tooltip") ? sec.getBoolean("hide-tooltip") : null,
                sec.isSet("unbreakable") ? sec.getBoolean("unbreakable") : null,
                sec.isSet("custom-model-data") ? String.valueOf(sec.get("custom-model-data")) : null,
                sec.isSet("item-model") ? sec.getString("item-model") : null,
                sec.isSet("tooltip-style") ? sec.getString("tooltip-style") : null,
                sec.isSet("item-flags") ? sec.getStringList("item-flags") : null,
                sec.isSet("enchants") ? sec.getStringList("enchants") : null,
                sec.isSet("damage") ? sec.getInt("damage") : null,
                sec.isSet("leather-color") ? sec.getString("leather-color") : null,
                sec.isSet("potion-color") ? sec.getString("potion-color") : null,
                sec.isSet("cooldown") ? sec.getDouble("cooldown") : null,
                sec.isSet("cooldown-message") ? expander.expand(sec.getString("cooldown-message", ""), context + " cooldown-message") : null,
                actions,
                animation
        );
    }

    private List<ItemConfig.PriorityTier> readPriority(ConfigurationSection itemSec, AliasExpander expander, String context) {
        if (!itemSec.isList("priority")) {
            return List.of();
        }
        return readNumberedEntries(itemSec.getMapList("priority"), (entry, index) -> {
            List<String> conditions = entry.getStringList("conditions");
            if (conditions.isEmpty()) {
                logger.warning("Priority tier #" + index + " in " + context + " has no conditions, skipping");
                return null;
            }
            return new ItemConfig.PriorityTier(conditions, readFields(entry, expander, context + " priority tier #" + index));
        });
    }

    private ItemConfig.Fields.Animation readAnimation(ConfigurationSection sec, AliasExpander expander, String context) {
        int interval = Math.max(1, sec.getInt("interval", 10));
        boolean loop = sec.getBoolean("loop", true);

        List<ItemConfig.Fields> frames = readNumberedEntries(sec.getMapList("frames"), (entry, index) ->
                readFields(entry, expander, context + " animation frame #" + index));

        if (frames.isEmpty()) {
            logger.warning("Animation in " + context + " has no frames, it will be ignored");
        }
        return new ItemConfig.Fields.Animation(interval, loop, frames);
    }

    // shared shape behind both priority tiers and animation frames
    private <T> List<T> readNumberedEntries(List<Map<?, ?>> raw, BiFunction<ConfigurationSection, Integer, T> parser) {
        List<T> results = new ArrayList<>(raw.size());
        int index = 0;
        for (Map<?, ?> entryRaw : raw) {
            index++;
            T parsed = parser.apply(mapToSection(entryRaw), index);
            if (parsed != null) {
                results.add(parsed);
            }
        }
        return results;
    }

    private Map<String, List<String>> readActionsBlock(ConfigurationSection sec, AliasExpander expander, String context) {
        Map<String, List<String>> actions = new LinkedHashMap<>();
        for (String clickKey : sec.getKeys(false)) {
            actions.put(clickKey, expander.expandAll(sec.getStringList(clickKey), context + " actions." + clickKey));
        }
        return actions;
    }

    private Map<ItemConfig.ClickType, List<String>> parseClickActions(Map<String, List<String>> raw) {
        Map<ItemConfig.ClickType, List<String>> result = new EnumMap<>(ItemConfig.ClickType.class);
        putIfPresent(result, ItemConfig.ClickType.LEFT, raw.get("left"));
        putIfPresent(result, ItemConfig.ClickType.RIGHT, raw.get("right"));

        List<String> shift = raw.get("shift");
        putIfPresent(result, ItemConfig.ClickType.LEFT_SHIFT, raw.getOrDefault("left-shift", shift));
        putIfPresent(result, ItemConfig.ClickType.RIGHT_SHIFT, raw.getOrDefault("right-shift", shift));

        putIfPresent(result, ItemConfig.ClickType.DROP, raw.get("drop"));
        putIfPresent(result, ItemConfig.ClickType.CONTROL_DROP, raw.get("control-drop"));
        putIfPresent(result, ItemConfig.ClickType.NUMBER, raw.get("number"));
        putIfPresent(result, ItemConfig.ClickType.OFFHAND, raw.get("offhand"));

        List<String> any = raw.get("any");
        if (any != null) {
            for (ItemConfig.ClickType type : ItemConfig.ClickType.values()) {
                result.putIfAbsent(type, any);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    private void putIfPresent(Map<ItemConfig.ClickType, List<String>> target, ItemConfig.ClickType type, List<String> lines) {
        if (lines != null) {
            target.put(type, List.copyOf(lines));
        }
    }

    // YAML list entries (priority tiers, animation frames) come back as raw Map, not ConfigurationSection
    private YamlConfiguration mapToSection(Map<?, ?> raw) {
        YamlConfiguration section = new YamlConfiguration();
        applyMap(section, raw);
        return section;
    }

    private void applyMap(ConfigurationSection target, Map<?, ?> raw) {
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                applyMap(target.createSection(key), nested);
            } else {
                target.set(key, value);
            }
        }
    }
}