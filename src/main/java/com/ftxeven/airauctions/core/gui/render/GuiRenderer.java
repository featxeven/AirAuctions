package com.ftxeven.airauctions.core.gui.render;

import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiRenderer {

    private final ItemResolver items;
    private final Map<String, DynamicRenderer> dynamicRenderers = new ConcurrentHashMap<>();

    public GuiRenderer(ItemResolver items) {
        this.items = items;
    }

    public GuiRenderer dynamicRenderer(String guiId, DynamicRenderer renderer) {
        dynamicRenderers.put(guiId, renderer);
        return this;
    }

    public void prepare(Player viewer, GuiSession session) {
        DynamicRenderer dynamic = dynamicRenderers.get(session.definition().id());
        if (dynamic != null) {
            dynamic.prepare(viewer, session);
        }
        clampPage(session);
        writePaginationPlaceholders(session);
    }

    private void clampPage(GuiSession session) {
        if (session.page() > session.totalPages()) {
            session.page(session.totalPages());
        }
    }

    private void writePaginationPlaceholders(GuiSession session) {
        Map<String, String> placeholders = session.placeholders();
        int page = session.page();
        int totalPages = session.totalPages();
        placeholders.put("page", String.valueOf(page));
        placeholders.put("pages", String.valueOf(totalPages));
        placeholders.put("has_previous_page", String.valueOf(page > 1));
        placeholders.put("has_next_page", String.valueOf(page < totalPages));
    }

    public void draw(Player viewer, GuiSession session) {
        Inventory inventory = session.inventory();
        inventory.clear();
        session.clearDynamicSlots();
        session.clearClaims();

        DynamicRenderer dynamic = dynamicRenderers.get(session.definition().id());

        List<ItemConfig> animated = new ArrayList<>();
        int minAnimationInterval = Integer.MAX_VALUE;

        for (ItemConfig item : session.definition().items().values()) {
            ItemResolver.ResolvedItem resolved = drawStaticItem(viewer, session, item);
            if (resolved != null && resolved.animated()) {
                animated.add(item);
                minAnimationInterval = Math.min(minAnimationInterval, resolved.animationInterval());
            }
        }

        if (dynamic != null) {
            dynamic.render(viewer, session);
        }

        rescheduleAnimationTicker(viewer, session, animated, minAnimationInterval);
    }

    public void render(Player viewer, GuiSession session) {
        prepare(viewer, session);
        draw(viewer, session);
    }

    // Static, config-declared items

    private @Nullable ItemResolver.ResolvedItem drawStaticItem(Player viewer, GuiSession session, ItemConfig item) {
        ItemResolver.ResolvedFields fields = items.resolveFields(item.template(), viewer, session.placeholders(),
                session.flagResolver(), session.openTick(), null);
        if (fields == null) {
            return null;
        }

        ItemResolver.ResolvedItem resolved = items.build(fields, item.key(), viewer, session.placeholders(),
                session.definition().settings().trimLore(), session.definition().id(), session.openTick(), null);

        Inventory inventory = session.inventory();
        for (int slot : item.slots()) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, resolved.stack());
            }
        }
        return resolved;
    }

    public Iterator<Integer> drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries, Iterator<Integer> slots) {
        for (RenderEntry entry : entries) {
            if (!slots.hasNext()) {
                break;
            }
            drawEntry(viewer, session, entry, slots.next());
        }
        return slots;
    }

    public void drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries, Set<Integer> slots) {
        drawEntries(viewer, session, entries, slots.iterator());
    }

    private void drawEntry(Player viewer, GuiSession session, RenderEntry entry, int slot) {
        ItemResolver.ResolvedFields resolvedFields = items.resolveFields(entry.template(), viewer, entry.placeholders(),
                entry.flags(), session.openTick(), entry.baseItem());
        if (resolvedFields == null) {
            return;
        }

        String key = "slot-" + slot;
        ItemResolver.ResolvedItem resolvedItem = items.build(resolvedFields, key, viewer, entry.placeholders(),
                session.definition().settings().trimLore(), session.definition().id(), session.openTick(), entry.baseItem());

        session.inventory().setItem(slot, resolvedItem.stack());
        session.dynamicSlot(slot, key, entry.placeholders(), resolvedFields, entry.entryId());
    }

    private void rescheduleAnimationTicker(Player viewer, GuiSession session, List<ItemConfig> animated, int minInterval) {
        if (session.animationTask() != null) {
            session.animationTask().cancel();
            session.animationTask(null);
        }
        if (animated.isEmpty()) {
            return;
        }

        int refreshInterval = session.definition().settings().refreshInterval();
        if (refreshInterval >= 0 && refreshInterval <= minInterval) {
            return;
        }

        session.animationTask(Scheduler.runEntityTimer(viewer, () -> {
            if (!session.active() || session.awaitingInput()) {
                return;
            }
            for (ItemConfig item : animated) {
                drawStaticItem(viewer, session, item);
            }
        }, minInterval, minInterval).orElse(null));
    }

    @FunctionalInterface
    public interface DynamicRenderer {
        void render(Player viewer, GuiSession session);
        default void prepare(Player viewer, GuiSession session) {}
    }
}