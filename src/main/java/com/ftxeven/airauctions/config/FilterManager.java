package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.AirAuctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public final class FilterManager {

    private final AirAuctions plugin;
    private final List<FilterCategory> categories = new ArrayList<>();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public FilterManager(AirAuctions plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    public void load() {
        categories.clear();

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();

        File file = new File(dataDir, "filter.yml");
        if (!file.exists()) {
            plugin.saveResource("data/filter.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) continue;

            String internalKey = key.toLowerCase();
            String displayName = section.getString("display-name", key);
            FilterCategory category = new FilterCategory(internalKey, displayName);

            ConfigurationSection rules = section.getConfigurationSection("match-rules");
            if (rules != null) {
                category.loadFromConfig(rules);
            }

            categories.add(category);
        }
    }

    public String getCategory(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "all";

        for (FilterCategory category : categories) {
            if (category.matches(item)) {
                return category.internalKey();
            }
        }
        return "all";
    }

    public String getDisplayName(String categoryKey) {
        if (categoryKey.equalsIgnoreCase("all")) return "All";
        return categories.stream()
                .filter(c -> c.internalKey().equalsIgnoreCase(categoryKey))
                .map(FilterCategory::displayName)
                .findFirst()
                .orElse(categoryKey);
    }

    public List<String> getOrderedCategoryKeys() {
        return categories.stream().map(FilterCategory::internalKey).toList();
    }

    public String getDefaultFilter() {
        return categories.isEmpty() ? "all" : categories.getFirst().internalKey();
    }

    private final class FilterCategory {
        private final String internalKey;
        private final String displayName;

        private final Set<Material> materials = EnumSet.noneOf(Material.class);
        private final List<String> modelData = new ArrayList<>();
        private final List<String> itemModels = new ArrayList<>();
        private final List<String> names = new ArrayList<>();
        private final Set<String> nexoIds = new HashSet<>();
        private final Set<String> itemsAdderIds = new HashSet<>();

        public FilterCategory(String internalKey, String displayName) {
            this.internalKey = internalKey;
            this.displayName = displayName;
        }

        public String internalKey() { return internalKey; }
        public String displayName() { return displayName; }

        public void loadFromConfig(ConfigurationSection section) {
            section.getStringList("materials").forEach(m -> {
                Material mat = Material.matchMaterial(m.toUpperCase());
                if (mat != null) materials.add(mat);
            });

            modelData.addAll(section.getStringList("custom-model-data"));
            itemModels.addAll(section.getStringList("item-models"));
            names.addAll(section.getStringList("names"));

            section.getStringList("special-items").forEach(s -> {
                String lower = s.toLowerCase();
                if (lower.startsWith("nexo:")) {
                    nexoIds.add(lower.substring(5));
                } else if (lower.startsWith("itemsadder:")) {
                    itemsAdderIds.add(lower.substring(11));
                }
            });
        }

        public boolean matches(ItemStack item) {
            if (item == null) return false;

            if (materials.contains(item.getType())) return true;

            var hm = plugin.getHookManager();

            if (!nexoIds.isEmpty() && hm.match(item, nexoIds, "nexo") != null) return true;
            if (!itemsAdderIds.isEmpty() && hm.match(item, itemsAdderIds, "itemsadder") != null) return true;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;

            if (meta.hasCustomModelData()) {
                String cmd = String.valueOf(meta.getCustomModelData());
                if (modelData.contains(cmd) || modelData.contains(item.getType().name() + ":" + cmd)) return true;
            }

            try {
                NamespacedKey modelKey = meta.getItemModel();
                if (modelKey != null && itemModels.contains(modelKey.toString())) return true;
            } catch (NoSuchMethodError ignored) {}

            if (!names.isEmpty()) {
                Component nameComp = meta.displayName();
                String plainName = nameComp != null ? plain.serialize(nameComp).toLowerCase() : "";
                if (!plainName.isEmpty()) {
                    for (String search : names) {
                        if (plainName.contains(search.toLowerCase())) return true;
                    }
                }
            }

            return false;
        }
    }
}