package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.AirAuctions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.bukkit.Material;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public final class ItemTranslationManager {
    private final AirAuctions plugin;
    private final Map<String, String> translations = new HashMap<>();

    public ItemTranslationManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadTranslations();
    }

    private void ensureDefaultItemsFile() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File file = new File(langDir, "items.json");
        if (!file.exists()) {
            plugin.saveResource("lang/items.json", false);
        }
    }

    private void loadTranslations() {
        translations.clear();
        ensureDefaultItemsFile();

        File file = new File(plugin.getDataFolder(), "lang/items.json");
        try (Reader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                translations.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (FileNotFoundException e) {
            plugin.getLogger().severe("items.json not found in lang/ folder!");
        } catch (JsonSyntaxException e) {
            plugin.getLogger().severe("Invalid JSON syntax in items.json: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read items.json: " + e.getMessage());
        }
    }

    public void reload() {
        loadTranslations();
    }

    public String translate(Material material) {
        if (material == null) return "Unknown";

        String base = material.name().toLowerCase();
        String itemKey = "item.minecraft." + base;
        String blockKey = "block.minecraft." + base;

        String value = translations.get(itemKey);
        if (value != null) return value;

        value = translations.get(blockKey);
        if (value != null) return value;

        return base.replace("_", " ");
    }
}