package com.ftxeven.airauctions.core.gui.nav;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record LockedContext(@Nullable Integer page, Map<String, String> attributes) {

    public static final LockedContext EMPTY = new LockedContext(null, Map.of());

    public LockedContext {
        attributes = Map.copyOf(attributes);
    }
}