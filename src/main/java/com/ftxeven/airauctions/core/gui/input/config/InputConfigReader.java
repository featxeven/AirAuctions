package com.ftxeven.airauctions.core.gui.input.config;

import com.ftxeven.airauctions.core.gui.config.AliasExpander;
import com.ftxeven.airauctions.core.gui.input.CancelBehavior;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class InputConfigReader {

    private final Logger logger;

    public InputConfigReader(Logger logger) {
        this.logger = logger;
    }

    // chat.yml

    public ChatInputConfig readChat(ConfigurationSection sec, AliasExpander expander) {
        String cancelKey = sec.getString("cancel-key", "cancel");
        CancelBehavior onCancel = cancelBehavior(sec, "on-cancel", CancelBehavior.BACK, "chat.yml");

        Map<String, ChatInputConfig.Context> contexts = new LinkedHashMap<>();
        ConfigurationSection contextsSec = sec.getConfigurationSection("contexts");
        if (contextsSec != null) {
            for (String key : contextsSec.getKeys(false)) {
                ConfigurationSection entry = contextsSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                String context = "chat.yml, context '" + key + "'";
                contexts.put(key, new ChatInputConfig.Context(
                        entry.getString("cancel-key", cancelKey),
                        cancelBehavior(entry, "on-cancel", onCancel, context),
                        expander.expand(entry.getString("prompt", ""), context + " prompt")
                ));
            }
        }

        return new ChatInputConfig(cancelKey, onCancel, contexts);
    }

    // dialog.yml

    public DialogInputConfig readDialog(ConfigurationSection sec, AliasExpander expander) {
        CancelBehavior onCancel = cancelBehavior(sec, "on-cancel", CancelBehavior.BACK, "dialog.yml");

        Map<String, DialogInputConfig.Context> contexts = new LinkedHashMap<>();
        ConfigurationSection contextsSec = sec.getConfigurationSection("contexts");
        if (contextsSec != null) {
            for (String key : contextsSec.getKeys(false)) {
                ConfigurationSection entry = contextsSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                contexts.put(key, readDialogContext(entry, key, onCancel, expander));
            }
        }

        return new DialogInputConfig(onCancel, contexts);
    }

    private DialogInputConfig.Context readDialogContext(ConfigurationSection sec, String key, CancelBehavior fallbackOnCancel, AliasExpander expander) {
        String context = "dialog.yml, context '" + key + "'";

        return new DialogInputConfig.Context(
                cancelBehavior(sec, "on-cancel", fallbackOnCancel, context),
                expander.expand(sec.getString("title", ""), context + " title"),
                expander.expand(sec.getString("external-title", ""), context + " external-title"),
                sec.getInt("width", 200),
                sec.getBoolean("can-close", true),
                readDialogBody(sec.getConfigurationSection("body"), context, expander),
                readDialogField(sec.getConfigurationSection("input"), context, expander),
                readDialogButtons(sec.getConfigurationSection("buttons"), context, expander)
        );
    }

    private List<DialogInputConfig.Entry> readDialogBody(@Nullable ConfigurationSection bodySec, String context, AliasExpander expander) {
        if (bodySec == null) {
            return List.of();
        }

        List<Map<?, ?>> rawEntries = bodySec.getMapList("entries");
        List<DialogInputConfig.Entry> entries = new ArrayList<>(rawEntries.size());
        int index = 0;
        for (Map<?, ?> raw : rawEntries) {
            index++;
            DialogInputConfig.Entry entry = readDialogEntry(raw, context + " body entry #" + index, expander);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private @Nullable DialogInputConfig.Entry readDialogEntry(Map<?, ?> raw, String context, AliasExpander expander) {
        Object type = raw.get("type");
        if (!(type instanceof String typeName)) {
            logger.warning("Dialog body entry in " + context + " is missing 'type', skipping");
            return null;
        }

        return switch (typeName.toLowerCase(Locale.ROOT)) {
            case "text" -> new DialogInputConfig.Entry.Text(
                    expander.expand(getString(raw, "text", ""), context + " text"));
            case "item" -> readDialogItemEntry(raw, context, expander);
            default -> {
                logger.warning("Unknown dialog body entry type '" + typeName + "' in " + context + ", skipping");
                yield null;
            }
        };
    }

    private @Nullable DialogInputConfig.Entry.Item readDialogItemEntry(Map<?, ?> raw, String context, AliasExpander expander) {
        String materialName = getString(raw, "material", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            logger.warning("Invalid or missing 'material: " + materialName + "' for item body entry in " + context + ", skipping");
            return null;
        }

        return new DialogInputConfig.Entry.Item(
                material,
                expander.expand(getString(raw, "description", ""), context + " description"),
                getInt(raw, "description-width", 200),
                getBoolean(raw, "show-decorations", true),
                getBoolean(raw, "show-tooltip", true),
                getInt(raw, "width", 16),
                getInt(raw, "height", 16)
        );
    }

    private static String getString(Map<?, ?> raw, String key, String fallback) {
        Object value = raw.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    private static int getInt(Map<?, ?> raw, String key, int fallback) {
        Object value = raw.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean getBoolean(Map<?, ?> raw, String key, boolean fallback) {
        Object value = raw.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private @Nullable DialogInputConfig.Field readDialogField(@Nullable ConfigurationSection sec, String context, AliasExpander expander) {
        if (sec == null) {
            return null;
        }

        return new DialogInputConfig.Field(
                expander.expand(sec.getString("label", ""), context + " input label"),
                sec.getBoolean("label-visible", true),
                expander.expand(sec.getString("initial-value", ""), context + " input initial-value"),
                sec.getInt("max-length", -1),
                sec.getInt("width", 150)
        );
    }

    private DialogInputConfig.Buttons readDialogButtons(@Nullable ConfigurationSection sec, String context, AliasExpander expander) {
        if (sec == null) {
            logger.warning("Dialog context in " + context + " has no 'buttons' section, using default submit/cancel");
        }
        return new DialogInputConfig.Buttons(
                readDialogButton(sec, "submit", "Submit", context, expander),
                readDialogButton(sec, "cancel", "Cancel", context, expander)
        );
    }

    private DialogInputConfig.Button readDialogButton(@Nullable ConfigurationSection sec, String key, String fallbackText, String context, AliasExpander expander) {
        ConfigurationSection buttonSec = sec != null ? sec.getConfigurationSection(key) : null;
        if (buttonSec == null) {
            return new DialogInputConfig.Button(fallbackText, 100);
        }
        return new DialogInputConfig.Button(
                expander.expand(buttonSec.getString("text", fallbackText), context + " buttons." + key + " text"),
                buttonSec.getInt("width", 100)
        );
    }

    // sign.yml

    public SignInputConfig readSign(ConfigurationSection sec, AliasExpander expander) {
        Map<String, SignInputConfig.Context> contexts = new LinkedHashMap<>();
        ConfigurationSection contextsSec = sec.getConfigurationSection("contexts");
        if (contextsSec != null) {
            for (String key : contextsSec.getKeys(false)) {
                ConfigurationSection entry = contextsSec.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                contexts.put(key, readSignContext(entry, key, expander));
            }
        }

        return new SignInputConfig(contexts);
    }

    private SignInputConfig.Context readSignContext(ConfigurationSection sec, String key, AliasExpander expander) {
        String context = "sign.yml, context '" + key + "'";

        Map<Integer, String> lines = new LinkedHashMap<>();
        ConfigurationSection linesSec = sec.getConfigurationSection("lines");
        if (linesSec != null) {
            for (String lineKey : linesSec.getKeys(false)) {
                Integer line = parseLineNumber(lineKey, context);
                if (line != null) {
                    lines.put(line, expander.expand(linesSec.getString(lineKey, ""), context + " lines." + lineKey));
                }
            }
        }

        return new SignInputConfig.Context(lines);
    }

    private @Nullable Integer parseLineNumber(String raw, String context) {
        try {
            int line = Integer.parseInt(raw.trim());
            if (line < 1 || line > 3) {
                logger.warning("Sign line " + line + " in " + context + " is out of range (1-3, line 0 is reserved for player input), skipping");
                return null;
            }
            return line;
        } catch (NumberFormatException e) {
            logger.warning("Invalid sign line key '" + raw + "' in " + context + ", expected a number 1-3");
            return null;
        }
    }

    // shared

    private CancelBehavior cancelBehavior(ConfigurationSection sec, String path, CancelBehavior fallback, String context) {
        if (!sec.isSet(path)) {
            return fallback;
        }
        try {
            return CancelBehavior.valueOf(sec.getString(path, "").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid 'on-cancel: " + sec.getString(path) + "' in " + context + ", using " + fallback);
            return fallback;
        }
    }
}