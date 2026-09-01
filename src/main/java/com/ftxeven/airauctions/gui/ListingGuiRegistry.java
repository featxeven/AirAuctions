package com.ftxeven.airauctions.gui;

import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.config.AliasExpander;
import com.ftxeven.airauctions.core.gui.config.GuiConfig;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.gui.config.LayoutConfigReader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ListingGuiRegistry {

    private final Logger logger;
    private final GuiManager guis;
    private final LayoutConfigReader layoutReader;
    private final Map<ConfigurationSection, LayoutConfig> cache = new ConcurrentHashMap<>();

    public ListingGuiRegistry(JavaPlugin plugin, GuiManager guis) {
        this.logger = plugin.getLogger();
        this.guis = guis;
        this.layoutReader = new LayoutConfigReader(logger);
    }

    public void load() {
        AliasExpander expander = new AliasExpander(guis.shared().aliases(), logger);
        for (String id : guis.ids()) {
            guis.definition(id).ifPresent(config -> {
                cacheSection(config.layout(), config.id(), expander);
                for (GuiConfig.ContextOverride override : config.contexts().values()) {
                    cacheSection(override.layout(), config.id(), expander);
                }
            });
        }
    }

    public void reload() {
        cache.clear();
        load();
    }

    public LayoutConfig layout(GuiConfig config) {
        ConfigurationSection section = config.layout();
        if (section == null) {
            return LayoutConfig.EMPTY;
        }
        return cache.computeIfAbsent(section, s ->
                layoutReader.read(s, guis.shared(), new AliasExpander(guis.shared().aliases(), logger), "GUI '" + config.id() + "' layout"));
    }

    private void cacheSection(@Nullable ConfigurationSection section, String guiId, AliasExpander expander) {
        if (section != null) {
            cache.computeIfAbsent(section, s -> layoutReader.read(s, guis.shared(), expander, "GUI '" + guiId + "' layout"));
        }
    }
}