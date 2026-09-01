package com.ftxeven.airauctions.core.animation;

import com.ftxeven.airauctions.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;

public final class AnimationManager {

    private final Supplier<Map<String, Animation>> animations;
    private final JavaPlugin plugin;
    private final AtomicLong tick = new AtomicLong();
    private ScheduledTask task;

    public AnimationManager(JavaPlugin plugin, Supplier<Map<String, Animation>> animations) {
        this.plugin = plugin;
        this.animations = animations;
    }

    public void start() {
        task = Scheduler.runGlobalTimer(tick::incrementAndGet, 1, 1);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long currentTick() {
        return tick.get();
    }

    public boolean has(String key) {
        return animations.get().containsKey(key);
    }

    public boolean isAnimated(String text) {
        return AnimationTag.PATTERN.matcher(text).find();
    }

    public String resolve(String text, long startTick) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        StringBuilder out = new StringBuilder();
        long now = currentTick();

        while (matcher.find()) {
            String key = matcher.group(1);
            Animation animation = animations.get().get(key);
            String replacement = animation == null
                    ? matcher.group() // unknown key
                    : frame(key, animation, now, startTick, matcher.group(2));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public String resolve(String text) {
        return resolve(text, 0);
    }

    public List<String> resolve(List<String> lines, long startTick) {
        List<String> resolved = new ArrayList<>(lines.size());
        for (String line : lines) {
            resolved.add(resolve(line, startTick));
        }
        return resolved;
    }

    public long duration(String text) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        long longest = 0;
        while (matcher.find()) {
            String loopsGroup = matcher.group(2);
            if (loopsGroup == null) {
                continue;
            }
            Animation animation = animations.get().get(matcher.group(1));
            if (animation == null || animation.frames().size() == 1) {
                continue;
            }
            long ticks = Integer.parseInt(loopsGroup) * animation.mode().cycleLength(animation.frames().size()) * animation.interval();
            longest = Math.max(longest, ticks);
        }
        return longest;
    }

    // smallest per-tick redraw cadence any <anim:...> tag in 'text' needs to stay moving,
    // or -1 if none do
    public int minInterval(String text) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        int smallest = -1;
        while (matcher.find()) {
            Animation animation = animations.get().get(matcher.group(1));
            if (animation == null || animation.frames().size() == 1) {
                continue;
            }
            smallest = smallest < 0 ? animation.interval() : Math.min(smallest, animation.interval());
        }
        return smallest;
    }

    // chat/actionbar have no external duration control, so an animation tag used there needs
    // its own explicit loop count, or it has no defined stop point. Default it to 1 and warn
    public String ensureLoopCount(String text, String context) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        matcher.reset();
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            if (matcher.group(2) == null) {
                plugin.getLogger().warning("Animation tag " + matcher.group() + " used in " + context + " without a loop count, defaulting to 1");
                result.append("<anim:").append(matcher.group(1)).append(":1>");
            } else {
                result.append(matcher.group());
            }
            last = matcher.end();
        }
        result.append(text, last, text.length());
        return result.toString();
    }

    // shared per-tick redraw loop backing chat/actionbar/bossbar/title animations - runs
    // until duration elapses then fires a completion action
    public ScheduledTask scheduleRedrawLoop(CommandSender target, long refStart, long durationTicks, LongConsumer onTick, Runnable onComplete) {
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = Scheduler.runTargetAwareTimer(target, () -> {
            long elapsed = currentTick() - refStart;
            if (elapsed >= durationTicks) {
                onComplete.run();
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                return;
            }
            onTick.accept(elapsed);
        }, 0, 1);
        return holder[0];
    }

    private String frame(String key, Animation animation, long now, long startTick, String loopsGroup) {
        List<String> frames = animation.frames();
        if (frames.size() == 1) {
            return frames.getFirst();
        }

        Animation.Mode mode = animation.mode();
        long step = Math.max(0, now - startTick) / animation.interval();

        if (loopsGroup != null && step >= Integer.parseInt(loopsGroup) * mode.cycleLength(frames.size())) {
            return frames.get(mode.settledIndex(frames.size()));
        }

        return frames.get(mode.frameIndex(key, step, frames.size()));
    }
}