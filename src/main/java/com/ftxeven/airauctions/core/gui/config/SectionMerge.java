package com.ftxeven.airauctions.core.gui.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

// Recursively merges an 'override' section onto a 'base' one
public final class SectionMerge {

    private SectionMerge() {
    }

    public enum KeyOrder {
        PRESERVE,
        OVERRIDE_LAST
    }

    public static @Nullable ConfigurationSection merge(@Nullable ConfigurationSection base, @Nullable ConfigurationSection override) {
        return merge(base, override, KeyOrder.PRESERVE);
    }

    public static @Nullable ConfigurationSection merge(@Nullable ConfigurationSection base, @Nullable ConfigurationSection override, KeyOrder order) {
        if (override == null) {
            return base;
        }
        if (base == null) {
            return override;
        }

        YamlConfiguration merged = new YamlConfiguration();
        if (order == KeyOrder.OVERRIDE_LAST) {
            for (String key : base.getKeys(false)) {
                if (!override.isSet(key)) {
                    copyEntry(merged, base, key);
                }
            }
            for (String key : override.getKeys(false)) {
                mergeEntry(merged, base, override, key);
            }
        } else {
            copyInto(merged, base);
            overlayInto(merged, override);
        }
        return merged;
    }

    // Replaces the section at 'key' in 'target' wholesale with a copy of 'source'
    public static void replace(ConfigurationSection target, String key, ConfigurationSection source) {
        target.set(key, null);
        copyInto(target.createSection(key), source);
    }

    private static void copyEntry(ConfigurationSection target, ConfigurationSection source, String key) {
        ConfigurationSection nested = source.getConfigurationSection(key);
        if (nested != null) {
            copyInto(target.createSection(key), nested);
        } else {
            target.set(key, source.get(key));
        }
    }

    private static void mergeEntry(ConfigurationSection target, ConfigurationSection base, ConfigurationSection override, String key) {
        ConfigurationSection baseNested = base.getConfigurationSection(key);
        ConfigurationSection overrideNested = override.getConfigurationSection(key);
        if (baseNested != null && overrideNested != null) {
            copyInto(target.createSection(key), merge(baseNested, overrideNested, KeyOrder.PRESERVE));
        } else if (overrideNested != null) {
            copyInto(target.createSection(key), overrideNested);
        } else {
            target.set(key, override.get(key));
        }
    }

    private static void copyInto(ConfigurationSection target, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection nested = source.getConfigurationSection(key);
            if (nested != null) {
                copyInto(target.createSection(key), nested);
            } else {
                target.set(key, source.get(key));
            }
        }
    }

    private static void overlayInto(ConfigurationSection target, ConfigurationSection override) {
        for (String key : override.getKeys(false)) {
            ConfigurationSection overrideNested = override.getConfigurationSection(key);
            ConfigurationSection targetNested = target.getConfigurationSection(key);
            if (overrideNested != null && targetNested != null) {
                overlayInto(targetNested, overrideNested);
            } else if (overrideNested != null) {
                copyInto(target.createSection(key), overrideNested);
            } else {
                target.set(key, override.get(key));
            }
        }
    }
}