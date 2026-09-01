package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.core.gui.config.*;
import com.ftxeven.airauctions.core.gui.input.InputType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class GuiRegistry {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path guisFolder;
    private final ItemConfigReader itemReader;
    private final Set<String> reservedPaths;

    private final Set<String> layoutReplaceKeys;

    private volatile SharedConfig shared;
    private volatile Map<String, GuiConfig> definitions = Map.of();

    public GuiRegistry(JavaPlugin plugin, Set<String> reservedPaths, Set<String> layoutReplaceKeys) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.guisFolder = plugin.getDataFolder().toPath().resolve("guis");
        this.itemReader = new ItemConfigReader(logger);
        this.reservedPaths = Set.copyOf(reservedPaths);
        this.layoutReplaceKeys = Set.copyOf(layoutReplaceKeys);
    }

    public boolean load() {
        extractBundledDefaults();
        boolean ok = loadFromDisk();
        logger.info("Loaded " + definitions.size() + " GUIs" + (ok ? "" : " (with errors, see warnings above)"));
        return ok;
    }

    public boolean reload() {
        extractBundledDefaults();
        return loadFromDisk();
    }

    // Resource extraction

    private void extractBundledDefaults() {
        File jarFile;
        try {
            jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            logger.warning("Could not locate the plugin jar to extract bundled GUIs: " + e.getMessage());
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("guis/") || !name.endsWith(".yml") || isReserved(name.substring("guis/".length()))) {
                    continue;
                }
                if (!new File(plugin.getDataFolder(), name).exists()) {
                    plugin.saveResource(name, false);
                }
            }
        } catch (IOException e) {
            logger.warning("Could not read the plugin jar to extract bundled GUIs: " + e.getMessage());
        }
    }

    // Disk loading

    private boolean loadFromDisk() {
        try {
            Files.createDirectories(guisFolder);
        } catch (IOException e) {
            logger.severe("Could not create guis/ folder: " + e.getMessage());
            return false;
        }

        boolean ok = true;

        File sharedFile = guisFolder.resolve("shared.yml").toFile();
        SharedConfig newShared;
        try {
            newShared = readShared(sharedFile);
        } catch (IOException e) {
            logger.severe("I/O error reading guis/shared.yml: " + e.getMessage());
            newShared = hardcodedShared();
            ok = false;
        } catch (InvalidConfigurationException e) {
            logger.severe("Malformed YAML in guis/shared.yml: " + e.getMessage());
            newShared = hardcodedShared();
            ok = false;
        }

        AliasExpander aliasExpander = new AliasExpander(newShared.aliases(), logger);

        List<Path> ymlFiles;
        try (Stream<Path> walk = Files.walk(guisFolder)) {
            ymlFiles = walk
                    .filter(p -> p.toString().endsWith(".yml"))
                    .filter(p -> !isReserved(guisFolder.relativize(p)))
                    .toList();
        } catch (IOException e) {
            logger.severe("Could not walk guis/ folder: " + e.getMessage());
            return false;
        }

        Path sharedPath = guisFolder.resolve("shared.yml");
        Map<String, GuiConfig> newDefinitions = new LinkedHashMap<>();
        for (Path path : ymlFiles) {
            if (path.equals(sharedPath)) {
                continue;
            }
            String id = guisFolder.relativize(path).toString()
                    .replace(File.separatorChar, '/')
                    .replaceAll("\\.yml$", "");

            try {
                newDefinitions.put(id, readDefinition(path.toFile(), id, newShared, aliasExpander));
            } catch (IOException e) {
                logger.warning("I/O error reading GUI '" + id + "' (" + path + "): " + e.getMessage());
                ok = false;
            } catch (InvalidConfigurationException e) {
                logger.warning("Malformed YAML in GUI '" + id + "' (" + path + "): " + e.getMessage());
                ok = false;
            }
        }

        validateContextReferences(newDefinitions);

        shared = newShared;
        definitions = Collections.unmodifiableMap(newDefinitions);
        return ok;
    }

    public SharedConfig shared() {
        return shared;
    }

    public Optional<GuiConfig> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Map<String, GuiConfig> all() {
        return definitions;
    }

    // shared.yml

    private SharedConfig readShared(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);

        Map<String, String> aliases = new LinkedHashMap<>();
        ConfigurationSection aliasSec = yaml.getConfigurationSection("aliases");
        if (aliasSec != null) {
            for (String key : aliasSec.getKeys(false)) {
                aliases.put(key, aliasSec.getString(key, ""));
            }
        }
        AliasExpander expander = new AliasExpander(aliases, logger);

        GuiSettings defaults = readSettings(yaml.getConfigurationSection("defaults"), null, expander, "shared.yml defaults");

        Map<String, ItemConfig.Template> templates = new LinkedHashMap<>();
        ConfigurationSection templatesSec = yaml.getConfigurationSection("templates");
        if (templatesSec != null) {
            for (String key : templatesSec.getKeys(false)) {
                templates.put(key, itemReader.readTemplate(templatesSec.getConfigurationSection(key), expander, "template '" + key + "'"));
            }
        }

        return new SharedConfig(defaults, aliases, templates);
    }

    private SharedConfig hardcodedShared() {
        return new SharedConfig(defaultSettings(), Map.of(), Map.of());
    }

    // per-GUI files

    private GuiConfig readDefinition(File file, String id, SharedConfig shared, AliasExpander aliasExpander) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);

        ConfigurationSection settingsSec = yaml.getConfigurationSection("settings");
        ConfigurationSection itemsSec = yaml.getConfigurationSection("items");
        ConfigurationSection layoutSec = yaml.getConfigurationSection("layout");

        GuiSettings settings = readSettings(settingsSec, shared.defaults(), aliasExpander, "GUI '" + id + "' settings");
        Map<String, ItemConfig> items = readItems(itemsSec, id, shared, aliasExpander, settings.rows());
        Map<String, GuiConfig.ContextOverride> contexts = readContexts(yaml.getConfigurationSection("contexts"), id, shared, aliasExpander,
                settingsSec, itemsSec, layoutSec);

        return new GuiConfig(id, settings, items, layoutSec, contexts);
    }

    private Map<String, ItemConfig> readItems(@Nullable ConfigurationSection itemsSec, String guiId, SharedConfig shared, AliasExpander aliasExpander, int rows) {
        Map<String, ItemConfig> items = new LinkedHashMap<>();
        Map<Integer, String> owner = new HashMap<>();

        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ItemConfig item = itemReader.read(itemsSec.getConfigurationSection(key), key, guiId, shared, aliasExpander, rows);
                if (item == null) {
                    continue;
                }
                for (int slot : item.slots()) {
                    String previousKey = owner.put(slot, key);
                    if (previousKey != null) {
                        ItemConfig previous = items.get(previousKey);
                        Set<Integer> remaining = new LinkedHashSet<>(previous.slots());
                        remaining.remove(slot);
                        items.put(previousKey, new ItemConfig(previous.key(), remaining, previous.template()));
                    }
                }
                items.put(key, item);
            }
        }
        return items;
    }

    private Map<String, GuiConfig.ContextOverride> readContexts(@Nullable ConfigurationSection sec, String id, SharedConfig shared, AliasExpander aliasExpander,
                                                                @Nullable ConfigurationSection baseSettings, @Nullable ConfigurationSection baseItems,
                                                                @Nullable ConfigurationSection baseLayout) {
        if (sec == null) {
            return Map.of();
        }

        Map<String, GuiConfig.ContextOverride> contexts = new LinkedHashMap<>();
        for (String pathKey : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(pathKey);
            if (entry == null) {
                continue;
            }

            ConfigurationSection mergedSettings = SectionMerge.merge(baseSettings, entry.getConfigurationSection("settings"));
            GuiSettings settings = readSettings(mergedSettings, shared.defaults(), aliasExpander,
                    "GUI '" + id + "', contexts.'" + pathKey + "' settings");

            ConfigurationSection mergedItems = SectionMerge.merge(baseItems, entry.getConfigurationSection("items"), SectionMerge.KeyOrder.OVERRIDE_LAST);
            Map<String, ItemConfig> items = readItems(mergedItems, id, shared, aliasExpander, settings.rows());

            ConfigurationSection layoutOverride = entry.getConfigurationSection("layout");
            ConfigurationSection mergedLayout = SectionMerge.merge(baseLayout, layoutOverride);
            mergedLayout = replaceFullReplaceKeys(baseLayout, layoutOverride, mergedLayout);

            contexts.put(pathKey, new GuiConfig.ContextOverride(settings, items, mergedLayout));
        }
        return contexts;
    }

    private @Nullable ConfigurationSection replaceFullReplaceKeys(@Nullable ConfigurationSection base,
                                                                  @Nullable ConfigurationSection override,
                                                                  @Nullable ConfigurationSection merged) {
        if (merged == null || base == null || override == null) {
            return merged;
        }
        for (String key : layoutReplaceKeys) {
            ConfigurationSection baseSection = base.getConfigurationSection(key);
            ConfigurationSection overrideSection = override.getConfigurationSection(key);
            if (baseSection != null && overrideSection != null) {
                SectionMerge.replace(merged, key, overrideSection);
            }
        }
        return merged;
    }

    private void validateContextReferences(Map<String, GuiConfig> definitions) {
        for (GuiConfig config : definitions.values()) {
            for (String pathKey : config.contexts().keySet()) {
                for (String sourceGuiId : pathKey.split("\\|")) {
                    if (!definitions.containsKey(sourceGuiId)) {
                        logger.warning("GUI '" + config.id() + "' declares a context for unknown source GUI '"
                                + sourceGuiId + "' (from context key '" + pathKey + "')");
                    }
                }
            }
        }
    }

    private GuiSettings readSettings(ConfigurationSection sec, GuiSettings fallback, AliasExpander expander, String context) {
        if (sec == null) {
            return fallback != null ? fallback : defaultSettings();
        }

        return new GuiSettings(
                sec.isSet("title") ? expander.expand(sec.getString("title", ""), context + " title") : orDefault(fallback, GuiSettings::title, ""),
                sec.getBoolean("enabled", orDefault(fallback, GuiSettings::enabled, true)),
                clampRows(sec.getInt("rows", orDefault(fallback, GuiSettings::rows, 6)), context),
                sec.getBoolean("trim-lore", orDefault(fallback, GuiSettings::trimLore, true)),
                sec.getBoolean("force-reopen", orDefault(fallback, GuiSettings::forceReopen, false)),
                sec.getInt("refresh-interval", orDefault(fallback, GuiSettings::refreshInterval, -1)),
                enumOr(sec, "input-type", InputType.class, orDefault(fallback, GuiSettings::inputType, InputType.DIALOG), context),
                sec.isSet("open-actions") ? expander.expandAll(sec.getStringList("open-actions"), context + " open-actions") : orDefault(fallback, GuiSettings::openActions, List.of()),
                sec.isSet("close-actions") ? expander.expandAll(sec.getStringList("close-actions"), context + " close-actions") : orDefault(fallback, GuiSettings::closeActions, List.of())
        );
    }

    private GuiSettings defaultSettings() {
        return new GuiSettings("", true, 6, true, false, -1, InputType.DIALOG, List.of(), List.of());
    }

    private <T> T orDefault(GuiSettings fallback, Function<GuiSettings, T> getter, T hardDefault) {
        return fallback != null ? getter.apply(fallback) : hardDefault;
    }

    private <T extends Enum<T>> T enumOr(ConfigurationSection sec, String path, Class<T> type, T fallback, String context) {
        if (!sec.isSet(path)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, sec.getString(path, "").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid value '" + sec.getString(path) + "' at '" + path + "' in " + context + ", using " + fallback);
            return fallback;
        }
    }

    private int clampRows(int rows, String context) {
        int clamped = Math.clamp(rows, 1, 6);
        if (clamped != rows) {
            logger.warning("Invalid 'rows: " + rows + "' in " + context + " (must be 1-6), using " + clamped);
        }
        return clamped;
    }

    private boolean isReserved(Path relativeToGuisFolder) {
        for (Path segment : relativeToGuisFolder) {
            if (reservedPaths.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isReserved(String relativeToGuisFolder) {
        for (String segment : relativeToGuisFolder.split("/")) {
            if (reservedPaths.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}