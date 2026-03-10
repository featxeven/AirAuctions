package com.ftxeven.airauctions.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class BossbarUtil {

    private BossbarUtil() {}

    private static SchedulerUtil scheduler;

    public static void init(SchedulerUtil schedulerUtil) {
        scheduler = schedulerUtil;
    }

    public static void send(Plugin plugin,
                            Player player,
                            String message,
                            Map<String, String> placeholders,
                            int duration,
                            Color color,
                            Overlay overlay,
                            float progress,
                            boolean countdown) {
        if (plugin == null || player == null || message == null || message.isBlank()) return;

        Component comp = MessageUtil.mini(player, message, placeholders);
        if (comp == null) return;

        BossBar bar = BossBar.bossBar(
                comp,
                Math.max(0f, Math.min(1f, progress)),
                color == null ? Color.WHITE : color,
                overlay == null ? Overlay.PROGRESS : overlay
        );

        if (scheduler != null) {
            scheduler.runEntityTask(player, () -> player.showBossBar(bar));
        } else {
            player.showBossBar(bar);
        }

        if (duration <= 0) return;

        if (countdown) {
            scheduleCountdownBar(player, bar, duration);
        } else {
            scheduler.runAsyncDelayed(() -> hideBossBarSafe(player, bar), duration);
        }
    }

    private static void scheduleCountdownBar(Player player, BossBar bar, int duration) {
        scheduler.runEntityTimer(player, new Runnable() {
            private int ticks = 0;

            @Override
            public void run() {
                ticks++;
                float newProgress = Math.max(0f, 1f - (ticks / (float) duration));
                bar.progress(newProgress);

                if (ticks >= duration) {
                    hideBossBarSafe(player, bar);
                }
            }
        }, 1L, 1L);
    }

    private static void hideBossBarSafe(Player player, BossBar bar) {
        if (scheduler != null) {
            scheduler.runEntityTask(player, () -> player.hideBossBar(bar));
        } else {
            player.hideBossBar(bar);
        }
    }
}