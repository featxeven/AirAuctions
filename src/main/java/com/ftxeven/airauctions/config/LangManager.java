package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class LangManager {

    private final AirAuctions plugin;
    private FileConfiguration lang;

    public LangManager(AirAuctions plugin) {
        this.plugin = plugin;
        extractAllLanguages();
        load();
    }

    private void load() {
        String locale = plugin.config().getLocale();

        File folder = new File(plugin.getDataFolder(), "lang/messages");
        File file = new File(folder, locale + ".yml");
        File fallbackFile = new File(folder, "en_US.yml");

        if (file.exists()) {
            lang = YamlConfiguration.loadConfiguration(file);
        } else if (fallbackFile.exists()) {
            plugin.getLogger().warning(locale + ".yml not found. Using en_US.yml as fallback.");
            lang = YamlConfiguration.loadConfiguration(fallbackFile);
        } else {
            try (InputStream in = plugin.getResource("lang/messages/en_US.yml")) {
                if (in != null) {
                    lang = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
                } else {
                    lang = new YamlConfiguration();
                }
            } catch (Exception e) {
                lang = new YamlConfiguration();
            }
        }
    }

    public void reload() {
        load();
        MessageUtil.init(plugin);
    }

    public String get(String key) {
        return lang.getString(key, "<red>Missing lang key: " + key);
    }

    public String get(String key, String fallback) {
        return lang.getString(key, fallback);
    }

    private void extractAllLanguages() {
        File folder = new File(plugin.getDataFolder(), "lang/messages");
        if (!folder.exists()) folder.mkdirs();

        try {
            File jarPath = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            try (JarFile jar = new JarFile(jarPath)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.startsWith("lang/messages/") && name.endsWith(".yml")) {
                        File target = new File(folder, name.substring(name.lastIndexOf("/") + 1));
                        if (!target.exists()) {
                            try (InputStream in = plugin.getResource(name)) {
                                if (in != null) Files.copy(in, target.toPath());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to auto-extract language files: " + e.getMessage());
        }
    }
}