package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.core.animation.AnimationTag;
import com.ftxeven.airauctions.core.message.ReferenceExpander;
import com.ftxeven.airauctions.util.Placeholders;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LangConfig {

    public static final String DEFAULT_LANG = "en_US";
    private static final Pattern ANIMATION = AnimationTag.PATTERN;

    private final JavaPlugin plugin;
    private final AnimationsConfig animations;

    private volatile String messagesFile;
    private volatile String itemsFile;
    private volatile Map<String, List<String>> messages;
    private volatile Map<String, String> items;

    public LangConfig(JavaPlugin plugin, AnimationsConfig animations) {
        this.plugin = plugin;
        this.animations = animations;
    }

    public void load(String lang, String itemsLang) {
        LoadedMessages loadedMessages = loadMessages(normalize(lang));
        LoadedItems loadedItems = loadItems(normalize(itemsLang));

        messagesFile = loadedMessages.fileName();
        messages = loadedMessages.messages();
        itemsFile = loadedItems.fileName();
        items = loadedItems.items();

        Placeholders.references(loadedMessages.references());
    }

    // Lookups

    public List<String> get(String key) {
        List<String> lines = messages.get(key);
        if (lines != null) {
            return lines;
        }
        plugin.getLogger().warning("Message key '" + key + "' does not exist in any loaded lang file (checked "
                + messagesFile + "), returning the key itself");
        return List.of(key);
    }

    public String item(String key) {
        String name = items.get(key);
        if (name != null) {
            return name;
        }
        plugin.getLogger().warning("Item key '" + key + "' does not exist in any loaded lang file (checked "
                + itemsFile + "), returning the key itself");
        return key;
    }

    // Loading

    private String normalize(String key) {
        return (key == null || key.isBlank()) ? DEFAULT_LANG : key.trim();
    }

    private LoadedMessages loadMessages(String key) {
        ensureDefaultExtracted("messages");
        File dir = new File(plugin.getDataFolder(), "lang/messages");
        ParsedMessages defaults = parseMessagesFile(new File(dir, DEFAULT_LANG + ".yml"), DEFAULT_LANG + ".yml");

        if (key.equals(DEFAULT_LANG)) {
            return new LoadedMessages(DEFAULT_LANG + ".yml", defaults.messages(), buildReferences(defaults.references(), DEFAULT_LANG + ".yml"));
        }

        File requested = new File(dir, key + ".yml");
        if (!requested.exists()) {
            plugin.getLogger().warning("Lang file 'lang/messages/" + key + ".yml' not found, using " + DEFAULT_LANG);
            return new LoadedMessages(DEFAULT_LANG + ".yml", defaults.messages(), buildReferences(defaults.references(), DEFAULT_LANG + ".yml"));
        }

        ParsedMessages custom = parseMessagesFile(requested, key + ".yml");
        Map<String, List<String>> mergedMessages = new LinkedHashMap<>(defaults.messages());
        mergedMessages.putAll(custom.messages());

        for (String messageKey : defaults.messages().keySet()) {
            if (!custom.messages().containsKey(messageKey)) {
                plugin.getLogger().warning("Missing message key '" + messageKey + "' in lang/messages/"
                        + key + ".yml, falling back to " + DEFAULT_LANG);
            }
        }

        // custom references override default ones, same as messages - a locale file no
        // longer needs to redeclare 'prefix' just to keep using it
        Map<String, String> mergedReferences = new LinkedHashMap<>(defaults.references());
        mergedReferences.putAll(custom.references());

        return new LoadedMessages(key + ".yml", Map.copyOf(mergedMessages), buildReferences(mergedReferences, key + ".yml"));
    }

    private LoadedItems loadItems(String key) {
        ensureDefaultExtracted("items");
        File dir = new File(plugin.getDataFolder(), "lang/items");
        Map<String, String> defaults = parseItemsFile(new File(dir, DEFAULT_LANG + ".yml"));

        if (key.equals(DEFAULT_LANG)) {
            return new LoadedItems(DEFAULT_LANG + ".yml", defaults);
        }

        File requested = new File(dir, key + ".yml");
        if (!requested.exists()) {
            plugin.getLogger().warning("Lang file 'lang/items/" + key + ".yml' not found, using " + DEFAULT_LANG);
            return new LoadedItems(DEFAULT_LANG + ".yml", defaults);
        }

        Map<String, String> custom = parseItemsFile(requested);
        Map<String, String> merged = new LinkedHashMap<>(defaults);
        merged.putAll(custom);

        int missing = 0;
        for (String itemKey : defaults.keySet()) {
            if (!custom.containsKey(itemKey)) {
                missing++;
            }
        }
        if (missing > 0) {
            plugin.getLogger().warning("Lang file 'lang/items/" + key + ".yml' is missing " + missing
                    + " item key(s) present in " + DEFAULT_LANG + ".yml, they will fall back to it");
        }

        return new LoadedItems(key + ".yml", Map.copyOf(merged));
    }

    private void ensureDefaultExtracted(String category) {
        File defaultFile = new File(plugin.getDataFolder(), "lang/" + category + "/" + DEFAULT_LANG + ".yml");
        if (!defaultFile.exists()) {
            plugin.saveResource("lang/" + category + "/" + DEFAULT_LANG + ".yml", false);
        }
    }

    // Parsing

    private Map<String, String> parseItemsFile(File file) {
        ConfigurationSection yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : yaml.getKeys(false)) {
            map.put(key, yaml.getString(key, key));
        }
        return Map.copyOf(map);
    }

    private ParsedMessages parseMessagesFile(File file, String label) {
        ConfigurationSection yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> references = readRawReferences(yaml.getConfigurationSection("references"));

        Map<String, List<String>> flat = new LinkedHashMap<>();
        flatten(yaml, "", flat, label);

        for (Map.Entry<String, List<String>> entry : flat.entrySet()) {
            validateAnimationTags(entry.getValue(), entry.getKey(), label);
        }

        return new ParsedMessages(Map.copyOf(flat), references);
    }

    // turns every nested section into a dotted key ("errors.access.no-permission") -> lines
    private void flatten(ConfigurationSection sec, String prefix, Map<String, List<String>> out, String label) {
        for (String key : sec.getKeys(false)) {
            if (prefix.isEmpty() && key.equals("references")) {
                continue;
            }

            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection nested = sec.getConfigurationSection(key);
            if (nested != null) {
                flatten(nested, path, out, label);
            } else if (sec.isList(key)) {
                out.put(path, List.copyOf(sec.getStringList(key)));
            } else if (sec.isString(key)) {
                out.put(path, List.of(sec.getString(key)));
            } else {
                plugin.getLogger().warning("Value at '" + path + "' in " + label + " is not a string or list, skipping");
            }
        }
    }

    private Map<String, String> readRawReferences(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, String> raw = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            raw.put(key, sec.getString(key, ""));
        }
        return raw;
    }

    private ReferenceExpander buildReferences(Map<String, String> raw, String label) {
        return ReferenceExpander.resolve(raw, (key, message) -> plugin.getLogger().warning(message + " in " + label));
    }

    private void validateAnimationTags(List<String> lines, String messageKey, String label) {
        for (String line : lines) {
            Matcher matcher = ANIMATION.matcher(line);
            while (matcher.find()) {
                String key = matcher.group(1);
                if (!animations.has(key)) {
                    plugin.getLogger().warning("Unknown animation '<anim:" + key + ">' used in '" + messageKey + "' in " + label);
                }
            }
        }
    }

    // Internal types

    private record ParsedMessages(Map<String, List<String>> messages, Map<String, String> references) {}

    private record LoadedMessages(String fileName, Map<String, List<String>> messages, ReferenceExpander references) {}

    private record LoadedItems(String fileName, Map<String, String> items) {}
}