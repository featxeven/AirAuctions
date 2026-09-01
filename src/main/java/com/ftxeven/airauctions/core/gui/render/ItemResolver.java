package com.ftxeven.airauctions.core.gui.render;

import com.ftxeven.airauctions.core.animation.AnimationManager;
import com.ftxeven.airauctions.core.condition.ConditionEvaluator;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.util.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ItemResolver {

    private final ConditionEvaluator conditions;
    private final ItemBuilder builder;
    private final AnimationManager animations; // only its shared tick clock is reused here

    public ItemResolver(ConditionEvaluator conditions, ItemBuilder builder, AnimationManager animations) {
        this.conditions = conditions;
        this.builder = builder;
        this.animations = animations;
    }

    // Stage 1: render condition/priority-tier matching and animation frame selection
    public @Nullable ResolvedFields resolveFields(ItemConfig.Template template, Player viewer, Map<String, String> placeholders, Function<String, String> flagResolver, long openTick, @Nullable ItemStack baseItem) {
        Function<String, String> resolver = Placeholders.resolver(viewer, placeholders);

        ItemConfig.Fields effective = template.fields();
        for (ItemConfig.PriorityTier tier : template.priority()) {
            if (conditions.evaluate(tier.conditions(), resolver)) {
                effective = effective.overlay(tier.fields());
                break;
            }
        }

        int interval = -1;
        if (effective.animation() != null && !effective.animation().frames().isEmpty()) {
            interval = effective.animation().interval();
            effective = effective.overlay(currentFrame(effective.animation(), openTick));
        }
        interval = mergeInterval(interval, embeddedTagInterval(effective));

        if (effective.material() == null && baseItem == null) {
            return null;
        }
        return new ResolvedFields(effective, flagResolver, interval < 0 ? null : interval);
    }

    // Stage 2: turn already-resolved fields into an actual ItemStack
    public ResolvedItem build(ResolvedFields resolved, String key, Player viewer, Map<String, String> placeholders, boolean trimLore, String guiId, long openTick, @Nullable ItemStack baseItem) {
        String context = "GUI '" + guiId + "', item '" + key + "'";
        ItemStack stack = builder.build(resolved.fields(), viewer, placeholders, resolved.flagResolver(), trimLore, context, openTick, baseItem);
        return new ResolvedItem(stack, resolved.actions(), resolved.animationInterval());
    }

    private ItemConfig.Fields currentFrame(ItemConfig.Fields.Animation animation, long openTick) {
        long step = Math.max(0, animations.currentTick() - openTick) / animation.interval();
        List<ItemConfig.Fields> frames = animation.frames();
        int index = animation.loop()
                ? (int) (step % frames.size())
                : (int) Math.min(step, frames.size() - 1);
        return frames.get(index);
    }

    private int embeddedTagInterval(ItemConfig.Fields fields) {
        int smallest = -1;
        if (fields.displayName() != null) {
            smallest = mergeInterval(smallest, animations.minInterval(fields.displayName()));
        }
        if (fields.lore() != null) {
            for (String line : fields.lore()) {
                smallest = mergeInterval(smallest, animations.minInterval(line));
            }
        }
        return smallest;
    }

    private static int mergeInterval(int current, int candidate) {
        if (candidate < 0) {
            return current;
        }
        return current < 0 ? candidate : Math.min(current, candidate);
    }

    public record ResolvedFields(ItemConfig.Fields fields, Function<String, String> flagResolver, @Nullable Integer animationInterval) {
        public Map<ItemConfig.ClickType, List<String>> actions() {
            return fields.actions() != null ? fields.actions() : Map.of();
        }

        public double cooldown() {
            Double cooldown = fields.cooldown();
            return cooldown != null ? cooldown : 0;
        }

        public @Nullable String cooldownMessage() {
            return fields.cooldownMessage();
        }
    }

    public record ResolvedItem(ItemStack stack, Map<ItemConfig.ClickType, List<String>> actions, @Nullable Integer animationInterval) {
        public boolean animated() {
            return animationInterval != null;
        }
    }
}