package com.ftxeven.airauctions.core.gui.render;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class MaterialResolver {

    private final HookResolver hooks;
    private final HeadResolver heads;
    private final Logger logger;

    public MaterialResolver(HookResolver hooks, HeadResolver heads, Logger logger) {
        this.hooks = hooks;
        this.heads = heads;
        this.logger = logger;
    }

    public @Nullable ItemStack resolve(String value, String context) {
        if (value.regionMatches(true, 0, "head-", 0, 5)) {
            return resolveHead(value.substring(5));
        }

        ItemStack hookItem = resolveHookItem(value);
        if (hookItem != null) {
            return hookItem;
        }

        Material material = Material.matchMaterial(value);
        if (material == null) {
            logger.warning("Unknown material '" + value + "' in " + context);
            return null;
        }
        return new ItemStack(material);
    }

    public @Nullable Integer resolveCustomModelData(String value, String context) {
        ItemStack hookItem = resolveHookItem(value);
        if (hookItem != null) {
            ItemMeta meta = hookItem.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                return meta.getCustomModelData();
            }
            logger.warning("Hook item '" + value + "' in " + context + " has no custom-model-data to reuse, treating '" + value + "' as a literal number instead");
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warning("Invalid custom-model-data '" + value + "' in " + context);
            return null;
        }
    }

    public @Nullable NamespacedKey resolveItemModel(String value, String context) {
        ItemStack hookItem = resolveHookItem(value);
        if (hookItem != null) {
            ItemMeta meta = hookItem.getItemMeta();
            NamespacedKey model = meta != null ? meta.getItemModel() : null;
            if (model != null) {
                return model;
            }
            logger.warning("Hook item '" + value + "' in " + context + " has no item-model to reuse, treating '" + value + "' as a literal key instead");
        }
        return NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
    }

    private ItemStack resolveHead(String rest) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) {
            return skull;
        }

        UUID uuid = tryParseUuid(rest);
        if (uuid != null) {
            applyCachedHead(meta, heads.byUuid(uuid), uuid, null);
        } else if (rest.length() > 16) {
            applyTextureProperty(meta, rest);
        } else {
            applyCachedHead(meta, heads.byName(rest), null, rest);
        }

        skull.setItemMeta(meta);
        return skull;
    }

    private @Nullable UUID tryParseUuid(String value) {
        if (value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private @Nullable ItemStack resolveHookItem(String value) {
        return value.indexOf(':') > 0 ? hooks.resolve(value) : null;
    }

    private void applyTextureProperty(SkullMeta meta, String base64) {
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
    }

    private void applyCachedHead(SkullMeta meta, Optional<CachedHead> cached, @Nullable UUID fallbackUuid, @Nullable String fallbackName) {
        if (cached.isPresent() && cached.get().hasTexture()) {
            CachedHead head = cached.get();
            PlayerProfile profile = Bukkit.createProfile(head.uuid(), head.name());
            profile.setProperty(new ProfileProperty("textures", head.textureValue(), head.textureSignature()));
            meta.setPlayerProfile(profile);
            return;
        }

        OfflinePlayer offline = fallbackUuid != null ? Bukkit.getOfflinePlayer(fallbackUuid) : Bukkit.getOfflinePlayer(fallbackName);
        meta.setOwningPlayer(offline);
    }

    @FunctionalInterface
    public interface HookResolver {
        HookResolver NONE = key -> null;

        @Nullable ItemStack resolve(String key);
    }

    public interface HeadResolver {
        HeadResolver NONE = new HeadResolver() {
            @Override public Optional<CachedHead> byUuid(UUID uuid) { return Optional.empty(); }
            @Override public Optional<CachedHead> byName(String name) { return Optional.empty(); }
        };

        Optional<CachedHead> byUuid(UUID uuid);
        Optional<CachedHead> byName(String name);
    }

    public record CachedHead(UUID uuid, String name, String textureValue, String textureSignature) {
        public boolean hasTexture() {
            return textureValue != null && !textureValue.isEmpty() && textureSignature != null && !textureSignature.isEmpty();
        }
    }
}