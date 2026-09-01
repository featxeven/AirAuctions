package com.ftxeven.airauctions.core.gui.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GuiConfig(
        String id,
        GuiSettings settings,
        Map<String, ItemConfig> items,
        @Nullable ConfigurationSection layout,
        Map<String, ContextOverride> contexts
) {
    public GuiConfig {
        items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        contexts = Map.copyOf(contexts);
    }

    public record ContextOverride(GuiSettings settings, Map<String, ItemConfig> items, @Nullable ConfigurationSection layout) {
        public ContextOverride {
            items = Map.copyOf(items);
        }
    }

    // Resolves what this GUI should look like given the chain of screens the player navigated
    // through to get here, oldest first, ending with the screen just left
    public GuiConfig effective(List<String> ancestorChain) {
        ContextOverride best = null;
        int bestLength = -1;

        for (Map.Entry<String, ContextOverride> entry : contexts.entrySet()) {
            String[] path = parseContextPath(entry.getKey());
            if (path.length > bestLength && matchesTail(ancestorChain, path)) {
                best = entry.getValue();
                bestLength = path.length;
            }
        }

        return best == null ? this : new GuiConfig(id, best.settings(), best.items(), best.layout(), Map.of());
    }

    private static String[] parseContextPath(String key) {
        return key.split("\\|");
    }

    private static boolean matchesTail(List<String> chain, String[] path) {
        if (path.length > chain.size()) {
            return false;
        }
        int offset = chain.size() - path.length;
        for (int i = 0; i < path.length; i++) {
            if (!chain.get(offset + i).equals(path[i])) {
                return false;
            }
        }
        return true;
    }
}