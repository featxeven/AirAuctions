package com.ftxeven.airauctions.core.gui.input.config;

import java.util.Map;
import java.util.Optional;

public record SignInputConfig(
        Map<String, Context> contexts
) {
    public static final SignInputConfig EMPTY = new SignInputConfig(Map.of());

    public SignInputConfig {
        contexts = Map.copyOf(contexts);
    }

    public Optional<Context> context(String key) {
        return Optional.ofNullable(contexts.get(key));
    }

    // line 0 is always reserved for the player's own input
    public record Context(Map<Integer, String> lines) {
        public Context {
            lines = Map.copyOf(lines);
        }
    }
}