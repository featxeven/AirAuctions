package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.core.gui.config.GuiConfig;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.nav.GuiContext;
import com.ftxeven.airauctions.core.gui.nav.ScreenKey;
import com.ftxeven.airauctions.core.gui.render.ItemResolver;
import com.ftxeven.airauctions.util.Cooldowns;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class GuiSession {

    public static final String ATTR_PAGE = "core:page";
    public static final String ATTR_TOTAL_PAGES = "core:total-pages";
    public static final String ATTR_TARGET = "core:target";
    public static final String ATTR_NAV_BACK = "core:nav-back";
    private static final int MAX_ANCESTOR_CHAIN = 6;

    private final UUID viewer;
    private final GuiConfig definition;
    private Inventory inventory;
    private final Map<String, String> placeholders;
    private Function<String, String> flagResolver;
    private final Map<Integer, ItemConfig> slotIndex;
    private final Map<String, Object> attributes = new HashMap<>();
    private final long openTick;
    private final List<String> originChain;

    private final Cooldowns<String> cooldowns = new Cooldowns<>();
    private long lastAnyClickAt = 0;

    private final Map<Integer, DynamicSlot> dynamicSlots = new HashMap<>();

    private volatile boolean active = true;
    private volatile boolean awaitingInput = false;
    private @Nullable ScheduledTask refreshTask;
    private @Nullable ScheduledTask animationTask;

    public GuiSession(UUID viewer, GuiConfig definition, Map<String, String> placeholders, Function<String, String> flagResolver,
                      long openTick, List<String> originChain) {
        this.viewer = viewer;
        this.definition = definition;
        this.placeholders = placeholders;
        this.flagResolver = flagResolver;
        this.openTick = openTick;
        this.originChain = List.copyOf(originChain);
        this.slotIndex = buildSlotIndex(definition);
    }

    public void attachInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void dynamicSlot(int slot, String key, Map<String, String> placeholders, ItemResolver.ResolvedFields resolved) {
        dynamicSlots.put(slot, new DynamicSlot(key, placeholders, resolved));
    }

    public @Nullable DynamicSlot dynamicSlot(int slot) {
        return dynamicSlots.get(slot);
    }

    public void clearDynamicSlots() {
        dynamicSlots.clear();
    }

    public record DynamicSlot(String key, Map<String, String> placeholders, ItemResolver.ResolvedFields resolved) {}

    private static Map<Integer, ItemConfig> buildSlotIndex(GuiConfig definition) {
        Map<Integer, ItemConfig> index = new HashMap<>();
        for (ItemConfig item : definition.items().values()) {
            for (int slot : item.slots()) {
                index.put(slot, item);
            }
        }
        return Map.copyOf(index);
    }

    public UUID viewer() { return viewer; }

    public GuiConfig definition() { return definition; }

    public Inventory inventory() { return inventory; }

    public Map<String, String> placeholders() { return placeholders; }

    public Function<String, String> flagResolver() { return flagResolver; }

    public void flagResolver(Function<String, String> flagResolver) { this.flagResolver = flagResolver; }

    public @Nullable ItemConfig itemAt(int slot) { return slotIndex.get(slot); }

    public @Nullable ScheduledTask refreshTask() { return refreshTask; }

    public void refreshTask(@Nullable ScheduledTask task) { this.refreshTask = task; }

    public @Nullable ScheduledTask animationTask() { return animationTask; }

    public void animationTask(@Nullable ScheduledTask task) { this.animationTask = task; }

    public long openTick() { return openTick; }

    public List<String> originChain() { return originChain; }

    public List<String> forwardChain() {
        return forwardChain(definition.id());
    }

    public List<String> forwardChain(String tail) {
        List<String> extended = new ArrayList<>(originChain.size() + 1);
        extended.addAll(originChain);
        extended.add(tail);
        int overflow = extended.size() - MAX_ANCESTOR_CHAIN;
        return overflow > 0 ? List.copyOf(extended.subList(overflow, extended.size())) : List.copyOf(extended);
    }

    public boolean active() { return active; }

    public void deactivate() {
        active = false;
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    // true while this session's inventory is closed to make room for a chat/dialog/sign input prompt
    public boolean awaitingInput() { return awaitingInput; }

    public void awaitingInput(boolean awaitingInput) { this.awaitingInput = awaitingInput; }

    // Generic attribute bag

    public void attribute(String key, Object value) { attributes.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T attribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    // Every currently-set String-valued attribute
    public Map<String, String> stringAttributes() {
        Map<String, String> strings = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (value instanceof String text) {
                strings.put(key, text);
            }
        });
        return strings;
    }

    // Pagination

    public int page() {
        Integer page = attribute(ATTR_PAGE, Integer.class);
        return page != null ? Math.max(1, page) : 1;
    }

    public void page(int page) {
        attribute(ATTR_PAGE, Math.max(1, page));
    }

    public int totalPages() {
        Integer totalPages = attribute(ATTR_TOTAL_PAGES, Integer.class);
        return totalPages != null ? Math.max(1, totalPages) : 1;
    }

    public void totalPages(int totalPages) {
        attribute(ATTR_TOTAL_PAGES, Math.max(1, totalPages));
    }

    // Screen identity / navigation

    // whose data this screen is showing, when that's meaningful
    public @Nullable UUID target() {
        return attribute(ATTR_TARGET, UUID.class);
    }

    public @Nullable GuiContext navBack() {
        return attribute(ATTR_NAV_BACK, GuiContext.class);
    }

    public ScreenKey screenKey() {
        return new ScreenKey(definition.id(), target());
    }

    public boolean passesGlobalFloor(long nowMillis) {
        if (nowMillis - lastAnyClickAt < 50) {
            return false;
        }
        lastAnyClickAt = nowMillis;
        return true;
    }

    public double checkAndStartCooldown(String itemKey, double cooldown, long nowMillis) {
        return cooldowns.checkAndStart(itemKey, cooldown, nowMillis);
    }

    void inheritCooldowns(GuiSession previous) {
        this.cooldowns.mergeFrom(previous.cooldowns);
        this.lastAnyClickAt = previous.lastAnyClickAt;
    }

    void inheritRuntimeState(GuiSession previous) {
        this.attributes.putAll(previous.attributes);
        inheritCooldowns(previous);
    }
}