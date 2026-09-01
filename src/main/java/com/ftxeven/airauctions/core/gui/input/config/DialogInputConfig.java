package com.ftxeven.airauctions.core.gui.input.config;

import com.ftxeven.airauctions.core.gui.input.CancelBehavior;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DialogInputConfig(
        CancelBehavior onCancel,
        Map<String, Context> contexts
) {
    public static final DialogInputConfig EMPTY = new DialogInputConfig(CancelBehavior.BACK, Map.of());

    public DialogInputConfig {
        contexts = Map.copyOf(contexts);
    }

    public Optional<Context> context(String key) {
        return Optional.ofNullable(contexts.get(key));
    }

    public record Context(
            CancelBehavior onCancel,
            String title,
            String externalTitle,
            int width,
            boolean canClose,
            List<Entry> body,
            @Nullable Field input,
            Buttons buttons
    ) {
        public Context {
            body = List.copyOf(body);
        }
    }

    public sealed interface Entry {
        record Text(String text) implements Entry {}

        record Item(
                Material material,
                String description,
                int descriptionWidth,
                boolean showDecorations,
                boolean showTooltip,
                int width,
                int height
        ) implements Entry {}
    }

    public record Field(String label, boolean labelVisible, String initialValue, int maxLength, int width) {}

    public record Buttons(Button submit, Button cancel) {}

    public record Button(String text, int width) {}
}