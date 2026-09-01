package com.ftxeven.airauctions.core.gui.config;

import java.util.Map;

public record SharedConfig(
        GuiSettings defaults,
        Map<String, String> aliases,
        Map<String, ItemConfig.Template> templates
) {
    public SharedConfig {
        aliases = Map.copyOf(aliases);
        templates = Map.copyOf(templates);
    }
}