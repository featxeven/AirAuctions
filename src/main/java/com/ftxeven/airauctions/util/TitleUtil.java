package com.ftxeven.airauctions.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

public final class TitleUtil {

    private static SchedulerUtil scheduler;

    private TitleUtil() {}

    public static void init(SchedulerUtil schedulerUtil) {
        scheduler = schedulerUtil;
    }

    public static void send(Player player,
                            String title, String subtitle,
                            int fadeIn, int stay, int fadeOut,
                            Map<String, String> placeholders) {
        if (player == null) return;

        Component titleComp = Component.empty();
        Component subtitleComp = Component.empty();

        if (title != null && !title.isBlank()) {
            Component c = MessageUtil.mini(player, title, placeholders);
            if (c != null) titleComp = c;
        }
        if (subtitle != null && !subtitle.isBlank()) {
            Component c = MessageUtil.mini(player, subtitle, placeholders);
            if (c != null) subtitleComp = c;
        }

        sendComponents(player, titleComp, subtitleComp, fadeIn, stay, fadeOut);
    }

    public static void sendComponents(Player player,
                                      Component titleComp,
                                      Component subtitleComp,
                                      int fadeIn, int stay, int fadeOut) {
        if (player == null) return;

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
        Title titleObj = Title.title(
                titleComp == null ? Component.empty() : titleComp,
                subtitleComp == null ? Component.empty() : subtitleComp,
                times
        );

        if (scheduler != null) {
            scheduler.runEntityTask(player, () -> player.showTitle(titleObj));
        } else {
            player.showTitle(titleObj);
        }
    }
}