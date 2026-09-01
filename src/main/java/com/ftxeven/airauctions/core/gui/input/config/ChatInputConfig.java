package com.ftxeven.airauctions.core.gui.input.config;

import com.ftxeven.airauctions.core.gui.input.CancelBehavior;

import java.util.Map;
import java.util.Optional;

public record ChatInputConfig(
        String cancelKey,
        CancelBehavior onCancel,
        Map<String, Context> contexts
) {
    public static final ChatInputConfig EMPTY = new ChatInputConfig("cancel", CancelBehavior.BACK, Map.of());

    public ChatInputConfig {
        contexts = Map.copyOf(contexts);
    }

    public Optional<Context> context(String key) {
        return Optional.ofNullable(contexts.get(key));
    }

    public record Context(String cancelKey, CancelBehavior onCancel, String prompt) {}
}