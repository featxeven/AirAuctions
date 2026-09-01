package com.ftxeven.airauctions.gui.config;

import com.ftxeven.airauctions.core.gui.config.AliasExpander;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.config.ItemConfigReader;
import com.ftxeven.airauctions.core.gui.config.SharedConfig;
import com.ftxeven.airauctions.core.gui.config.SlotParser;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class LayoutConfigReader {

    private static final Set<String> TEMPLATE_KEYS = withStructuralKeys(ItemConfig.FIELD_KEYS);

    private static Set<String> withStructuralKeys(Set<String> fieldKeys) {
        Set<String> combined = new LinkedHashSet<>(fieldKeys);
        combined.add("template");
        combined.add("priority");
        return Set.copyOf(combined);
    }

    private final Logger logger;
    private final ItemConfigReader itemReader;

    public LayoutConfigReader(Logger logger) {
        this.logger = logger;
        this.itemReader = new ItemConfigReader(logger);
    }

    public LayoutConfig read(@Nullable ConfigurationSection sec, SharedConfig shared, AliasExpander expander, String context) {
        if (sec == null) {
            return LayoutConfig.EMPTY;
        }

        return new LayoutConfig(
                readCycler(sec.getConfigurationSection("filters"), expander, context + " filters"),
                readCycler(sec.getConfigurationSection("sorts"), expander, context + " sorts"),
                sec.isSet("listing-slots") ? SlotParser.parse(sec.get("listing-slots"), context + " listing-slots", logger) : null,
                readListingRender(sec.getConfigurationSection("listing"), shared, expander, context + " listing"),
                sec.isSet("bidder-slots") ? SlotParser.parse(sec.get("bidder-slots"), context + " bidder-slots", logger) : null,
                sec.isConfigurationSection("bidder") ? itemReader.readTemplateRef(sec.getConfigurationSection("bidder"), shared, expander, context + " bidder") : null,
                sec.isSet("shulker-slots") ? SlotParser.parse(sec.get("shulker-slots"), context + " shulker-slots", logger) : null,
                readAvailableSlots(sec.getConfigurationSection("available-slots"), shared, expander, context + " available-slots")
        );
    }

    private @Nullable LayoutConfig.Cycler readCycler(@Nullable ConfigurationSection sec, AliasExpander expander, String context) {
        if (sec == null) {
            return null;
        }

        Map<String, LayoutConfig.Cycler.Format> format = new LinkedHashMap<>();
        ConfigurationSection formatSec = sec.getConfigurationSection("format");
        if (formatSec != null) {
            for (String key : formatSec.getKeys(false)) {
                ConfigurationSection entry = formatSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                format.put(key, new LayoutConfig.Cycler.Format(
                        expander.expand(entry.getString("selected", ""), context + " format." + key + " selected"),
                        expander.expand(entry.getString("unselected", ""), context + " format." + key + " unselected")
                ));
            }
        }

        return new LayoutConfig.Cycler(sec.getStringList("excluded"), format);
    }

    private @Nullable LayoutConfig.ListingRender readListingRender(@Nullable ConfigurationSection sec, SharedConfig shared, AliasExpander expander, String context) {
        if (sec == null) {
            return null;
        }
        if (isDirectTemplate(sec)) {
            return new LayoutConfig.ListingRender(itemReader.readTemplateRef(sec, shared, expander, context), Map.of());
        }

        Map<String, ItemConfig.Template> variants = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry != null) {
                variants.put(key, itemReader.readTemplateRef(entry, shared, expander, context + " '" + key + "'"));
            }
        }
        return variants.isEmpty() ? null : new LayoutConfig.ListingRender(null, variants);
    }

    private boolean isDirectTemplate(ConfigurationSection sec) {
        for (String key : sec.getKeys(false)) {
            if (TEMPLATE_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable LayoutConfig.AvailableSlots readAvailableSlots(@Nullable ConfigurationSection sec, SharedConfig shared, AliasExpander expander, String context) {
        if (sec == null) {
            return null;
        }
        return new LayoutConfig.AvailableSlots(sec.getBoolean("enabled", false), itemReader.readTemplateRef(sec, shared, expander, context));
    }
}