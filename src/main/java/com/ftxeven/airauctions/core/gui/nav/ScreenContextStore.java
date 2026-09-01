package com.ftxeven.airauctions.core.gui.nav;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenContextStore {

    private final Map<UUID, Map<ScreenKey, ScreenState>> live = new ConcurrentHashMap<>();
    private final Map<UUID, Map<ScreenKey, LockedContext>> locked = new ConcurrentHashMap<>();

    public ScreenState resolve(UUID player, ScreenKey screen) {
        ScreenState liveState = liveFor(player, screen);
        LockedContext lockedState = lockedFor(player, screen);

        Map<String, String> attributes = new LinkedHashMap<>(liveState.attributes());
        attributes.putAll(lockedState.attributes());
        int page = lockedState.page() != null ? lockedState.page() : liveState.page();
        return new ScreenState(page, liveState.totalPages(), attributes);
    }

    public LockedContext locked(UUID player, ScreenKey screen) {
        return lockedFor(player, screen);
    }

    public void lock(UUID player, ScreenKey screen, @Nullable Integer page, Map<String, String> attributes) {
        Map<ScreenKey, LockedContext> byScreen = locked.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
        LockedContext existing = byScreen.getOrDefault(screen, LockedContext.EMPTY);

        Map<String, String> merged = new LinkedHashMap<>(existing.attributes());
        attributes.forEach((key, value) -> {
            if (value != null) {
                merged.put(key, value);
            }
        });

        byScreen.put(screen, new LockedContext(page != null ? page : existing.page(), merged));
    }

    public void recordLive(UUID player, ScreenKey screen, ScreenState state) {
        live.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(screen, state);
        clampLockedPage(player, screen, state.page());
    }

    public void forget(UUID player) {
        live.remove(player);
        locked.remove(player);
    }

    private ScreenState liveFor(UUID player, ScreenKey screen) {
        Map<ScreenKey, ScreenState> byScreen = live.get(player);
        ScreenState state = byScreen != null ? byScreen.get(screen) : null;
        return state != null ? state : ScreenState.DEFAULT;
    }

    private LockedContext lockedFor(UUID player, ScreenKey screen) {
        Map<ScreenKey, LockedContext> byScreen = locked.get(player);
        LockedContext state = byScreen != null ? byScreen.get(screen) : null;
        return state != null ? state : LockedContext.EMPTY;
    }

    private void clampLockedPage(UUID player, ScreenKey screen, int correctedPage) {
        Map<ScreenKey, LockedContext> byScreen = locked.get(player);
        LockedContext existing = byScreen != null ? byScreen.get(screen) : null;
        if (existing != null && existing.page() != null && existing.page() != correctedPage) {
            byScreen.put(screen, new LockedContext(correctedPage, existing.attributes()));
        }
    }
}