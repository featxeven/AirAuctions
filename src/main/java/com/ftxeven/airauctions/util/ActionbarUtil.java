package com.ftxeven.airauctions.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class ActionbarUtil {

    private ActionbarUtil() {}

    private static SchedulerUtil scheduler;

    public static void init(SchedulerUtil schedulerUtil) {
        scheduler = schedulerUtil;
    }

    public static void send(Plugin plugin,
                            Player player,
                            String message,
                            Map<String, String> placeholders) {
        if (plugin == null || player == null || message == null || message.isBlank()) return;

        Component comp = MessageUtil.mini(player, message, placeholders);
        if (comp == null) return;

        if (scheduler != null) {
            scheduler.runEntityTask(player, () -> player.sendActionBar(comp));
        } else {
            player.sendActionBar(comp);
        }
    }
}