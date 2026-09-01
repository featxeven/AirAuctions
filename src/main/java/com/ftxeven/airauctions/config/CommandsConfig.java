package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.core.command.DurationUnits.DurationUnit;
import com.ftxeven.airauctions.core.command.DynamicCommand;
import com.ftxeven.airauctions.core.command.RootCommand;
import com.ftxeven.airauctions.core.command.Shortcuts.Shortcut;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition.CopyPosition;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition.EntriesPosition;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition.TabEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Function;

public final class CommandsConfig extends BaseConfig {

    private volatile Map<String, Shortcut> shortcuts;
    private volatile Map<String, DurationUnit> durationUnits;
    private volatile RootCommand main;
    private volatile Map<String, DynamicCommand> subcommands;

    public CommandsConfig(JavaPlugin plugin) {
        super(plugin, "commands.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        Map<String, Shortcut> newShortcuts = readShortcuts(yaml.getConfigurationSection("shortcuts"));
        Map<String, DurationUnit> newDurationUnits = readDurationUnits(yaml.getConfigurationSection("duration-units"));
        RootCommand newMain = readMain(yaml.getConfigurationSection("main"));
        Map<String, DynamicCommand> newSubcommands = readSubcommands(yaml.getConfigurationSection("subcommands"));

        shortcuts = newShortcuts;
        durationUnits = newDurationUnits;
        main = newMain;
        subcommands = newSubcommands;
    }

    public Map<String, Shortcut> shortcuts() {
        return shortcuts;
    }

    public Map<String, DurationUnit> durationUnits() {
        return durationUnits;
    }

    public RootCommand main() {
        return main;
    }

    public Map<String, DynamicCommand> subcommands() {
        return subcommands;
    }

    public Optional<DynamicCommand> findSubcommand(String id) {
        return Optional.ofNullable(subcommands.get(id));
    }

    /** the subcommand config for id, or a disabled placeholder if it's missing */
    public DynamicCommand findSubcommandOrDisabled(String id) {
        return findSubcommand(id).orElseGet(() -> DynamicCommand.disabled(id));
    }

    // Section readers

    private Map<String, Shortcut> readShortcuts(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, Shortcut> shortcuts = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Shortcut shortcut = readShortcut(sec.getConfigurationSection(key), key);
            if (shortcut != null) {
                shortcuts.put(key, shortcut);
            }
        }
        return Collections.unmodifiableMap(shortcuts);
    }

    private Shortcut readShortcut(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);

        String runs = sec.getString("runs", "");
        if (runs.isBlank()) {
            plugin.getLogger().warning("Shortcut '" + key + "' in " + fileName() + " has no 'runs' value, skipping");
            return null;
        }

        return new Shortcut(runs, optionalStringList(sec, "aliases"));
    }

    private Map<String, DurationUnit> readDurationUnits(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, DurationUnit> units = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection unitSec = orEmpty(sec.getConfigurationSection(key));
            int ticks = getInt(unitSec, "ticks", 1);
            if (ticks < 1) {
                plugin.getLogger().warning("ticks must be at least 1 for duration unit '" + key + "' in " + fileName() + ", using 1");
                ticks = 1;
            }
            units.put(key, new DurationUnit(ticks));
        }
        return Collections.unmodifiableMap(units);
    }

    private RootCommand readMain(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new RootCommand(
                getString(sec, "name", "auctionhouse"),
                optionalStringList(sec, "aliases"),
                getString(sec, "usage", "/%label% [subcommand] [...]")
        );
    }

    private Map<String, DynamicCommand> readSubcommands(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, DynamicCommand> subcommands = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            subcommands.put(key, readSubcommand(sec.getConfigurationSection(key), key));
        }
        return Collections.unmodifiableMap(subcommands);
    }

    private DynamicCommand readSubcommand(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);
        return new DynamicCommand(
                getBoolean(sec, "enabled", true),
                getString(sec, "name", key),
                optionalStringList(sec, "aliases"),
                getString(sec, "usage", ""),
                sec.getString("usage-others", ""),
                readLabelMap(sec.getConfigurationSection("actions")),
                readTabComplete(sec.getConfigurationSection("tab-complete"), key)
        );
    }

    private Map<Integer, TabPosition> readTabComplete(ConfigurationSection sec, String subLabel) {
        if (sec == null) {
            return Map.of();
        }
        Map<Integer, TabPosition> positions = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            int position;
            try {
                position = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Tab-complete key '" + key + "' for '" + subLabel + "' in " + fileName() + " is not a valid position number, skipping");
                continue;
            }
            TabPosition parsed = readPosition(sec, key, subLabel);
            if (parsed != null) {
                positions.put(position, parsed);
            }
        }
        validateCopyPositions(positions, subLabel);
        return Collections.unmodifiableMap(positions);
    }

    // a copy-arg pointing at itself or at another copy-arg position can't be resolved
    private void validateCopyPositions(Map<Integer, TabPosition> positions, String subLabel) {
        for (Map.Entry<Integer, TabPosition> entry : positions.entrySet()) {
            if (!(entry.getValue() instanceof CopyPosition copy)) {
                continue;
            }
            if (copy.copyArg() == entry.getKey()) {
                plugin.getLogger().warning("Tab-complete position " + entry.getKey() + " for '" + subLabel + "' in " + fileName()
                        + " has copy-arg pointing to itself, ignoring");
                entry.setValue(new EntriesPosition(List.of()));
            } else if (positions.get(copy.copyArg()) instanceof CopyPosition) {
                plugin.getLogger().warning("Tab-complete position " + entry.getKey() + " for '" + subLabel + "' in " + fileName()
                        + " has copy-arg pointing to another copy-arg position, ignoring");
                entry.setValue(new EntriesPosition(List.of()));
            }
        }
    }

    private TabPosition readPosition(ConfigurationSection tabSec, String key, String subLabel) {
        if (tabSec.isConfigurationSection(key)) {
            ConfigurationSection entrySec = tabSec.getConfigurationSection(key);
            if (entrySec.isSet("copy-arg")) {
                return readCopyPosition(entrySec, key, subLabel);
            }
            return new EntriesPosition(readEntries(entrySec, subLabel, key));
        }
        if (tabSec.isList(key)) {
            return new EntriesPosition(readEntries(tabSec.getList(key), subLabel, key));
        }
        plugin.getLogger().warning("Tab-complete position '" + key + "' for '" + subLabel + "' in " + fileName() + " is not a valid map or list, skipping");
        return null;
    }

    private TabPosition readCopyPosition(ConfigurationSection sec, String key, String subLabel) {
        int copyArg = sec.getInt("copy-arg", -1);
        if (copyArg <= 0) {
            plugin.getLogger().warning("Invalid copy-arg at tab-complete position '" + key + "' for '" + subLabel + "' in " + fileName() + ", skipping");
            return null;
        }
        return new CopyPosition(
                copyArg,
                sec.getStringList("exclude-sources"),
                readEntries(sec.get("append-sources"), subLabel, key)
        );
    }

    // a position's entries can be a single entry map, a list of entry maps, or a flat
    // list of literals (shorthand, collapsed to one sources-only entry)
    private List<TabEntry> readEntries(Object raw, String subLabel, String context) {
        if (raw instanceof ConfigurationSection cs) {
            TabEntry entry = readEntry(cs, subLabel, context);
            return entry != null ? List.of(entry) : List.of();
        }
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            if (list.get(0) instanceof Map || list.get(0) instanceof ConfigurationSection) {
                List<TabEntry> entries = new ArrayList<>();
                for (Object item : list) {
                    TabEntry entry = readEntry(item, subLabel, context);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                return List.copyOf(entries);
            }
            return List.of(new TabEntry(readSources(list), "", List.of(), false));
        }
        return List.of();
    }

    private TabEntry readEntry(Object raw, String subLabel, String context) {
        Function<String, Object> field = fieldAccessor(raw);
        if (field == null) {
            plugin.getLogger().warning("Tab-complete entry at position '" + context + "' for '" + subLabel + "' in " + fileName() + " is not a valid map, skipping");
            return null;
        }
        List<String> sources = readSources(field.apply("sources"));
        if (sources.isEmpty()) {
            plugin.getLogger().warning("Tab-complete entry at position '" + context + "' for '" + subLabel + "' in " + fileName() + " has no sources");
        }
        return new TabEntry(
                sources,
                asString(field.apply("requires"), ""),
                toStringList(field.apply("conditions")),
                Boolean.TRUE.equals(field.apply("suffix-mode"))
        );
    }

    // an entry is a ConfigurationSection when it sits directly under a position, but a
    // raw Map when it's an element inside a YAML list - Bukkit only auto-wraps the former
    private static Function<String, Object> fieldAccessor(Object raw) {
        if (raw instanceof ConfigurationSection cs) {
            return cs::get;
        }
        if (raw instanceof Map<?, ?> map) {
            return map::get;
        }
        return null;
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            values.add(String.valueOf(item));
        }
        return List.copyOf(values);
    }

    private static List<String> readSources(Object raw) {
        return mergeAngleBracketGroups(toStringList(raw));
    }

    private static List<String> mergeAngleBracketGroups(List<String> raw) {
        List<String> merged = new ArrayList<>();
        StringBuilder open = null;
        for (String token : raw) {
            if (open != null) {
                open.append(", ").append(token);
                if (token.indexOf('>') >= 0) {
                    merged.add(open.toString());
                    open = null;
                }
                continue;
            }
            if (token.indexOf('<') >= 0 && token.indexOf('>') < 0) {
                open = new StringBuilder(token);
            } else {
                merged.add(token);
            }
        }
        if (open != null) {
            merged.add(open.toString()); // unterminated '<' with no matching '>'
        }
        return merged;
    }

    private static String asString(Object raw, String fallback) {
        return raw != null ? String.valueOf(raw) : fallback;
    }
}