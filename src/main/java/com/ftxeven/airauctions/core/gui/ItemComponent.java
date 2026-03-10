package com.ftxeven.airauctions.core.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.PlayerAuctionProfile.SkinData;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ItemComponent {
    private static final boolean HAS_HIDE_TOOLTIP;
    private static final boolean HAS_ITEM_MODEL;
    private static final boolean HAS_TOOLTIP_STYLE;

    static {
        boolean tooltip = false, model = false, style = false;
        try { ItemMeta.class.getMethod("setHideTooltip", boolean.class); tooltip = true; } catch (Throwable ignored) {}
        try { ItemMeta.class.getMethod("setItemModel", NamespacedKey.class); model = true; } catch (Throwable ignored) {}
        try { ItemMeta.class.getMethod("setTooltipStyle", NamespacedKey.class); style = true; } catch (Throwable ignored) {}
        HAS_HIDE_TOOLTIP = tooltip;
        HAS_ITEM_MODEL = model;
        HAS_TOOLTIP_STYLE = style;
    }

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemComponent(String materialSelector, AirAuctions plugin) {
        String lower = materialSelector.toLowerCase();
        ItemStack hookedItem = null;

        if (lower.startsWith("nexo:")) {
            hookedItem = plugin.getHookManager().getItem(materialSelector.substring(5), "nexo");
        } else if (lower.startsWith("itemsadder:")) {
            hookedItem = plugin.getHookManager().getItem(materialSelector.substring(11), "itemsadder");
        } else if (lower.startsWith("craftengine:")) {
            hookedItem = plugin.getHookManager().getItem(materialSelector.substring(12), "craftengine");
        }

        if (hookedItem != null) {
            this.item = hookedItem.clone();
        } else {
            Material mat = Material.matchMaterial(materialSelector.toUpperCase());
            if (materialSelector.startsWith("head-")) mat = Material.PLAYER_HEAD;
            this.item = new ItemStack(mat != null ? mat : Material.STONE);
        }
        this.meta = item.getItemMeta();
    }

    public ItemComponent(ItemStack existing) {
        this.item = existing.clone();
        this.meta = item.getItemMeta();
    }

    public ItemComponent amount(int amount) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return this;
    }

    public ItemComponent name(Component name) { if (meta != null) meta.displayName(name); return this; }
    public ItemComponent lore(List<Component> lore) { if (meta != null) meta.lore(lore); return this; }
    public ItemComponent customModelData(Integer data) { if (meta != null) meta.setCustomModelData(data); return this; }
    public ItemComponent damage(Integer dmg) { if (meta instanceof Damageable d && dmg != null) d.setDamage(dmg); return this; }

    public ItemComponent enchants(Map<String, Integer> enchants) {
        if (meta != null && enchants != null) {
            enchants.forEach((k, v) -> {
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
                if (ench != null) meta.addEnchant(ench, v, true);
            });
        }
        return this;
    }

    public ItemComponent glow(boolean glow) {
        if (meta != null && glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemComponent flags(ItemFlag... flags) { if (meta != null && flags.length > 0) meta.addItemFlags(flags); return this; }

    public void skullOwner(String owner, Player viewer, SkinData skinData) {
        if (!(meta instanceof SkullMeta sm) || owner == null || owner.isEmpty()) return;

        if (skinData != null && skinData.hasData()) {
            String profileName = (owner.contains("%") || owner.length() > 16) ? "AuctionHead" : owner;
            UUID consistentUuid = UUID.nameUUIDFromBytes(skinData.value().getBytes(StandardCharsets.UTF_8));

            PlayerProfile profile = Bukkit.createProfile(consistentUuid, profileName);
            profile.setProperty(new ProfileProperty("textures", skinData.value(), skinData.signature()));
            sm.setPlayerProfile(profile);
            return;
        }

        if (owner.length() > 36 && !owner.contains("-")) {
            applyCustomHead(sm, owner);
            return;
        }

        if (owner.equalsIgnoreCase("%player%") || (viewer != null && owner.equalsIgnoreCase(viewer.getName()))) {
            sm.setPlayerProfile(viewer.getPlayerProfile());
        } else {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        }
    }

    private void applyCustomHead(SkullMeta sm, String texture) {
        String encoded = texture.length() > 100 ? texture :
                Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + texture + "\"}}}").getBytes(StandardCharsets.UTF_8));

        UUID consistentUuid = UUID.nameUUIDFromBytes(encoded.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(consistentUuid, "AuctionHead");
        profile.setProperty(new ProfileProperty("textures", encoded));
        sm.setPlayerProfile(profile);
    }

    public ItemComponent hideTooltip(boolean hide) {
        if (HAS_HIDE_TOOLTIP && meta != null) meta.setHideTooltip(hide);
        return this;
    }

    public ItemComponent tooltipStyle(String style) {
        if (HAS_TOOLTIP_STYLE && meta != null && style != null) {
            NamespacedKey key = NamespacedKey.fromString(style);
            if (key != null) meta.setTooltipStyle(key);
        }
        return this;
    }

    public void itemModel(String model) {
        if (HAS_ITEM_MODEL && meta != null && model != null) {
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) meta.setItemModel(key);
        }
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}