package com.ftxeven.airauctions.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class SoundUtil {

    private SoundUtil() {}

    private static final Map<String, Key> KEY_CACHE = new ConcurrentHashMap<>();
    private static SchedulerUtil scheduler;

    public static void init(SchedulerUtil schedulerUtil) {
        scheduler = schedulerUtil;
    }

    public static void play(Player player, String name, float volume, float pitch) {
        if (player == null || name == null || name.isBlank()) return;

        try {
            Key key = KEY_CACHE.computeIfAbsent(name, Key::key);
            Sound sound = Sound.sound(key, Sound.Source.PLAYER, volume, pitch);

            if (Bukkit.isPrimaryThread()) {
                player.playSound(sound, player);
            } else if (scheduler != null) {
                scheduler.runEntityTask(player, () -> player.playSound(sound, player));
            } else {
                player.playSound(sound, player);
            }
        } catch (Exception ignored) {}
    }
}