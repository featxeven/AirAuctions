package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.BlacklistResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ItemBlacklistService {

    private final AirAuctions plugin;
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public ItemBlacklistService(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public BlacklistResult check(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return BlacklistResult.allowed();

        boolean matchFound = isMatch(item);
        boolean isWhitelist = plugin.config().isItemFilterWhitelist();

        if (isWhitelist && !matchFound) return BlacklistResult.blocked("not-in-whitelist");
        if (!isWhitelist && matchFound) return BlacklistResult.blocked("blacklisted");

        return BlacklistResult.allowed();
    }

    private boolean isMatch(ItemStack item) {
        Material type = item.getType();
        ItemMeta meta = item.getItemMeta();

        if (checkSpecialItems(item)) return true;

        if (plugin.config().getFilterMaterials().contains(type.name())) return true;
        if (meta == null) return false;

        Component nameComp = meta.displayName();
        String displayName = nameComp != null ? plain.serialize(nameComp).toLowerCase() : "";
        for (String filterName : plugin.config().getFilterNames()) {
            if (!displayName.isEmpty() && displayName.contains(filterName.toLowerCase())) return true;
        }

        List<Component> loreComps = meta.lore();
        if (loreComps != null && !loreComps.isEmpty()) {
            List<String> loreLines = loreComps.stream().map(c -> plain.serialize(c).toLowerCase()).toList();
            for (String filter : plugin.config().getFilterLores()) {
                if (filter.contains(":")) {
                    try {
                        String[] split = filter.split(":", 2);
                        int lineIdx = Integer.parseInt(split[0]);
                        if (loreLines.size() > lineIdx && loreLines.get(lineIdx).contains(split[1].toLowerCase())) return true;
                    } catch (NumberFormatException ignored) {}
                } else {
                    if (loreLines.stream().anyMatch(l -> l.contains(filter.toLowerCase()))) return true;
                }
            }
        }

        for (String entry : plugin.config().getFilterEnchantments()) {
            String[] split = entry.split(":");
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(split[0].toLowerCase()));
            if (ench != null && meta.hasEnchant(ench)) {
                if (split.length == 1 || meta.getEnchantLevel(ench) == Integer.parseInt(split[1])) return true;
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.getKeys().stream().anyMatch(k -> plugin.config().getFilterNbtKeys().contains(k.getKey()))) return true;

        if (meta.hasCustomModelData()) {
            String cmdStr = String.valueOf(meta.getCustomModelData());
            List<String> cmdFilters = plugin.config().getFilterCustomModelData();
            if (cmdFilters.contains(cmdStr) || cmdFilters.contains(type.name() + ":" + cmdStr)) return true;
        }

        try {
            NamespacedKey modelKey = meta.getItemModel();
            if (modelKey != null) {
                if (plugin.config().getFilterItemModels().contains(modelKey.toString())) return true;
            }
        } catch (NoSuchMethodError ignored) {}

        ConfigurationSection specific = plugin.config().getFilterSpecificItems();
        if (specific != null) {
            for (String key : specific.getKeys(false)) {
                ConfigurationSection sub = specific.getConfigurationSection(key);
                if (sub == null) continue;

                String matStr = sub.getString("material");
                if (matStr == null || !matStr.equalsIgnoreCase(type.name())) continue;

                String configName = sub.getString("name");
                if (configName != null && !displayName.contains(configName.toLowerCase())) continue;

                if (sub.contains("glow")) {
                    boolean shouldGlow = sub.getBoolean("glow");
                    boolean hasGlowEnchant = meta.hasEnchant(Enchantment.UNBREAKING);
                    boolean hasHiddenFlags = meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS);
                    boolean isGlowActive = hasGlowEnchant && hasHiddenFlags;

                    if (shouldGlow != isGlowActive) continue;
                }

                return true;
            }
        }

        return false;
    }

    private boolean checkSpecialItems(ItemStack item) {
        List<String> specialItems = plugin.config().getFilterSpecialItems();
        if (specialItems.isEmpty()) return false;

        Set<String> nexo = new HashSet<>();
        Set<String> itemsAdder = new HashSet<>();
        Set<String> craftEngine = new HashSet<>();

        for (String s : specialItems) {
            String lower = s.toLowerCase();
            if (lower.startsWith("nexo:")) nexo.add(lower.substring(5));
            else if (lower.startsWith("itemsadder:")) itemsAdder.add(lower.substring(11));
            else if (lower.startsWith("craftengine:")) craftEngine.add(lower.substring(12));
        }

        var hm = plugin.getHookManager();
        if (!nexo.isEmpty() && hm.match(item, nexo, "nexo") != null) return true;
        if (!itemsAdder.isEmpty() && hm.match(item, itemsAdder, "itemsadder") != null) return true;
        return !craftEngine.isEmpty() && hm.match(item, craftEngine, "craftengine") != null;
    }
}