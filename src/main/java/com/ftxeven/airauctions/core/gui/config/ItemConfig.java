package com.ftxeven.airauctions.core.gui.config;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ItemConfig(
        String key,
        Set<Integer> slots,
        Template template
) {
    public ItemConfig {
        slots = Collections.unmodifiableSet(new LinkedHashSet<>(slots));
    }

    // Kept in sync with ItemConfigReader.readFields() - anything that
    // needs to tell "this is one of a template's fields" apart from something else
    public static final Set<String> FIELD_KEYS = Set.of(
            "material", "display-name", "lore", "amount", "glow",
            "hide-tooltip", "unbreakable", "custom-model-data", "item-model", "tooltip-style",
            "item-flags", "enchants", "damage", "leather-color", "potion-color",
            "cooldown", "cooldown-message", "actions", "animation"
    );

    public record Template(Fields fields, List<PriorityTier> priority) {
        public Template {
            priority = List.copyOf(priority);
        }
    }

    public record PriorityTier(List<String> conditions, Fields fields) {
        public PriorityTier {
            conditions = List.copyOf(conditions);
        }
    }

    public record Fields(
            @Nullable String material,
            @Nullable String displayName,
            @Nullable List<String> lore,
            @Nullable Integer amount,
            @Nullable Boolean glow,
            @Nullable Boolean hideTooltip,
            @Nullable Boolean unbreakable,
            @Nullable String customModelData,
            @Nullable String itemModel,
            @Nullable String tooltipStyle,
            @Nullable List<String> itemFlags,
            @Nullable List<String> enchants,
            @Nullable Integer damage,
            @Nullable String leatherColor,
            @Nullable String potionColor,
            @Nullable Double cooldown,
            @Nullable String cooldownMessage,
            @Nullable Map<ClickType, List<String>> actions,
            @Nullable Animation animation
    ) {
        public static final Fields EMPTY = new Fields(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        public Fields overlay(Fields override) {
            return new Fields(
                    firstNonNull(override.material, material),
                    firstNonNull(override.displayName, displayName),
                    firstNonNull(override.lore, lore),
                    firstNonNull(override.amount, amount),
                    firstNonNull(override.glow, glow),
                    firstNonNull(override.hideTooltip, hideTooltip),
                    firstNonNull(override.unbreakable, unbreakable),
                    firstNonNull(override.customModelData, customModelData),
                    firstNonNull(override.itemModel, itemModel),
                    firstNonNull(override.tooltipStyle, tooltipStyle),
                    firstNonNull(override.itemFlags, itemFlags),
                    firstNonNull(override.enchants, enchants),
                    firstNonNull(override.damage, damage),
                    firstNonNull(override.leatherColor, leatherColor),
                    firstNonNull(override.potionColor, potionColor),
                    firstNonNull(override.cooldown, cooldown),
                    firstNonNull(override.cooldownMessage, cooldownMessage),
                    firstNonNull(override.actions, actions),
                    firstNonNull(override.animation, animation)
            );
        }

        private static <T> T firstNonNull(T override, T base) {
            return override != null ? override : base;
        }

        public record Animation(int interval, boolean loop, List<Fields> frames) {
            public Animation {
                frames = List.copyOf(frames);
            }
        }
    }

    public enum ClickType {
        LEFT, RIGHT, LEFT_SHIFT, RIGHT_SHIFT, DROP, CONTROL_DROP, NUMBER, OFFHAND;

        public static @Nullable ClickType from(InventoryClickEvent event) {
            return switch (event.getClick()) {
                case LEFT -> LEFT;
                case RIGHT -> RIGHT;
                case SHIFT_LEFT -> LEFT_SHIFT;
                case SHIFT_RIGHT -> RIGHT_SHIFT;
                case DROP -> DROP;
                case CONTROL_DROP -> CONTROL_DROP;
                case NUMBER_KEY -> NUMBER;
                case SWAP_OFFHAND -> OFFHAND;
                default -> null;
            };
        }
    }
}