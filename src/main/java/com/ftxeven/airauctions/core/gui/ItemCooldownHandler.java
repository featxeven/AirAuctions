package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.TimeUtil;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemCooldownHandler {
    private final AirAuctions plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public ItemCooldownHandler(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldown() <= 0) return false;
        long expiry = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(item.key(), 0L);
        return System.currentTimeMillis() < expiry;
    }

    public void applyCooldown(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldown() <= 0) return;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(item.key(), System.currentTimeMillis() + (long)(item.cooldown() * 1000));
    }

    public void sendCooldownMessage(Player player, GuiDefinition.GuiItem item) {
        if (item.cooldownMessage() == null) return;

        long expiry = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(item.key(), 0L);
        long remaining = Math.max(1, (expiry - System.currentTimeMillis()) / 1000);

        String formattedTime = TimeUtil.formatSeconds(plugin, remaining);

        player.sendMessage(MessageUtil.mini(player, item.cooldownMessage(), Map.of("cooldown", formattedTime)));
    }

    public void clear() { cooldowns.clear(); }
}