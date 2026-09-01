package com.ftxeven.airauctions.core.message;

public sealed interface MessageTag {

    record Sound(String key, float volume, float pitch) implements MessageTag {}

    record ActionBar(String text) implements MessageTag {}

    record Title(String text, long fadeInTicks, long stayTicks, long fadeOutTicks) implements MessageTag {}

    record Subtitle(String text) implements MessageTag {}

    record BossBar(
            String text,
            long durationTicks,
            net.kyori.adventure.bossbar.BossBar.Color color,
            net.kyori.adventure.bossbar.BossBar.Overlay overlay,
            float initialProgress,
            boolean countdown
    ) implements MessageTag {}
}