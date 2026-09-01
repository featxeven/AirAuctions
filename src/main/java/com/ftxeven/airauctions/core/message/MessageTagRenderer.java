package com.ftxeven.airauctions.core.message;

import com.ftxeven.airauctions.core.animation.AnimationManager;
import com.ftxeven.airauctions.util.MiniText;
import com.ftxeven.airauctions.util.Scheduler;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.Locale;
import java.util.logging.Logger;

public final class MessageTagRenderer {

    private final AnimationManager animations;
    private final Logger logger;

    public MessageTagRenderer(AnimationManager animations, Logger logger) {
        this.animations = animations;
        this.logger = logger;
    }

    public void render(CommandSender target, MessageTag tag, TitleBuffer titleBuffer) {
        switch (tag) {
            case MessageTag.Sound sound -> playSound(target, sound);
            case MessageTag.ActionBar actionBar -> sendActionBar(target, actionBar.text());
            case MessageTag.Title title -> titleBuffer.apply(title);
            case MessageTag.Subtitle subtitle -> titleBuffer.sub = subtitle.text();
            case MessageTag.BossBar bossBar -> showBossBar(target, bossBar);
        }
    }

    public void flushTitle(CommandSender target, TitleBuffer buffer) {
        if (!buffer.isEmpty()) {
            showTitle(target, buffer);
        }
    }

    private void playSound(CommandSender target, MessageTag.Sound sound) {
        try {
            Key key = Key.key(sound.key().toLowerCase(Locale.ROOT));
            target.playSound(Sound.sound(key, Sound.Source.MASTER, sound.volume(), sound.pitch()));
        } catch (Exception e) {
            logger.warning("Invalid sound key '" + sound.key() + "', skipping");
        }
    }

    private void sendActionBar(CommandSender target, String rawText) {
        String text = animations.ensureLoopCount(rawText, "an action bar message");
        long refStart = animations.currentTick();
        target.sendActionBar(deserialize(animations.resolve(text, refStart)));

        if (!animations.isAnimated(text)) {
            return;
        }

        long duration = animations.duration(text);
        animations.scheduleRedrawLoop(target, refStart, duration,
                elapsed -> target.sendActionBar(deserialize(animations.resolve(text, refStart))),
                () -> { });
    }

    private void showTitle(CommandSender target, TitleBuffer buffer) {
        String main = buffer.main != null ? buffer.main : "";
        String sub = buffer.sub != null ? buffer.sub : "";
        if (buffer.sub != null && buffer.main == null) {
            logger.warning("Subtitle tag used without an accompanying title tag, using default timing (20/60/20)");
        }

        Title.Times times = Title.Times.times(ticks(buffer.fadeIn), ticks(buffer.stay), ticks(buffer.fadeOut));

        boolean animated = animations.isAnimated(main) || animations.isAnimated(sub);
        if (!animated) {
            target.showTitle(Title.title(deserialize(main), deserialize(sub), times));
            return;
        }

        long refStart = animations.currentTick();
        target.showTitle(Title.title(
                deserialize(animations.resolve(main, refStart)),
                deserialize(animations.resolve(sub, refStart)),
                times));

        if (buffer.fadeIn <= 0) {
            beginLiveTitle(target, main, sub, buffer.stay, buffer.fadeOut);
        } else {
            Scheduler.runTargetAwareLater(target, () -> beginLiveTitle(target, main, sub, buffer.stay, buffer.fadeOut), buffer.fadeIn);
        }
    }

    private void beginLiveTitle(CommandSender target, String main, String sub, long stayTicks, long fadeOutTicks) {
        long liveStart = animations.currentTick();
        animations.scheduleRedrawLoop(target, liveStart, stayTicks,
                elapsed -> target.showTitle(Title.title(
                        deserialize(animations.resolve(main, liveStart)),
                        deserialize(animations.resolve(sub, liveStart)),
                        Title.Times.times(Duration.ZERO, ticks(4), Duration.ZERO))),
                () -> target.showTitle(Title.title(
                        deserialize(animations.resolve(main, liveStart)),
                        deserialize(animations.resolve(sub, liveStart)),
                        Title.Times.times(Duration.ZERO, Duration.ZERO, ticks(fadeOutTicks)))));
    }

    private void showBossBar(CommandSender target, MessageTag.BossBar tag) {
        long refStart = animations.currentTick();
        boolean animated = animations.isAnimated(tag.text());
        String initialText = animations.resolve(tag.text(), refStart);
        BossBar bar = BossBar.bossBar(deserialize(initialText), tag.initialProgress(), tag.color(), tag.overlay());
        target.showBossBar(bar);

        if (!animated && !tag.countdown()) {
            Scheduler.runTargetAwareLater(target, () -> target.hideBossBar(bar), tag.durationTicks());
            return;
        }

        String[] lastText = animated ? new String[]{initialText} : null;

        animations.scheduleRedrawLoop(target, refStart, tag.durationTicks(), elapsed -> {
            if (animated) {
                redrawName(bar, tag, refStart, lastText);
            }
            if (tag.countdown()) {
                bar.progress(Math.max(0f, tag.initialProgress() * (1f - (float) elapsed / tag.durationTicks())));
            }
        }, () -> target.hideBossBar(bar));
    }

    private void redrawName(BossBar bar, MessageTag.BossBar tag, long refStart, String[] lastText) {
        String resolved = animations.resolve(tag.text(), refStart);
        if (!resolved.equals(lastText[0])) {
            bar.name(deserialize(resolved));
            lastText[0] = resolved;
        }
    }

    private Duration ticks(long amount) {
        return Duration.ofMillis(amount * 50);
    }

    private Component deserialize(String text) {
        try {
            return MiniText.parse(text);
        } catch (Exception e) {
            logger.warning("Could not parse MiniMessage text '" + text + "': " + e.getMessage());
            return Component.text(text);
        }
    }

    public static final class TitleBuffer {
        private String main;
        private String sub;
        private long fadeIn = 20;
        private long stay = 60;
        private long fadeOut = 20;

        private void apply(MessageTag.Title title) {
            main = title.text();
            fadeIn = title.fadeInTicks();
            stay = title.stayTicks();
            fadeOut = title.fadeOutTicks();
        }

        private boolean isEmpty() {
            return main == null && sub == null;
        }
    }
}