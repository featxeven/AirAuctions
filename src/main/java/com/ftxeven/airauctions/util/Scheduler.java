package com.ftxeven.airauctions.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class Scheduler {

    private static final Plugin PLUGIN = JavaPlugin.getProvidingPlugin(Scheduler.class);

    private Scheduler() {
    }

    private static boolean enabled() {
        return PLUGIN.isEnabled();
    }

    public static ScheduledTask runGlobal(Runnable task) {
        if (!enabled()) { task.run(); return null; }
        return Bukkit.getGlobalRegionScheduler().run(PLUGIN, t -> task.run());
    }

    public static ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        if (!enabled()) { task.run(); return null; }
        return Bukkit.getGlobalRegionScheduler().runDelayed(PLUGIN, t -> task.run(), clampDelay(delayTicks));
    }

    public static ScheduledTask runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        if (!enabled()) { return null; }
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(PLUGIN, t -> task.run(), clampDelay(initialDelayTicks), clampPeriod(periodTicks));
    }

    public static void cancelGlobal() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(PLUGIN);
    }

    public static ScheduledTask runAsync(Runnable task) {
        if (!enabled()) { task.run(); return null; }
        return Bukkit.getAsyncScheduler().runNow(PLUGIN, t -> task.run());
    }

    public static ScheduledTask runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        if (!enabled()) { task.run(); return null; }
        return Bukkit.getAsyncScheduler().runDelayed(PLUGIN, t -> task.run(), delay, unit);
    }

    public static ScheduledTask runAsyncTimer(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (!enabled()) { return null; }
        return Bukkit.getAsyncScheduler()
                .runAtFixedRate(PLUGIN, t -> task.run(), clampDelay(initialDelay), clampPeriod(period), unit);
    }

    public static void cancelAsync() {
        Bukkit.getAsyncScheduler().cancelTasks(PLUGIN);
    }

    public static ScheduledTask runRegion(Location location, Runnable task) {
        if (!enabled()) { task.run(); return null; }
        return Bukkit.getRegionScheduler().run(PLUGIN, location, t -> task.run());
    }

    public static ScheduledTask runRegionLater(Location location, Runnable task, long delayTicks) {
        if (!enabled()) { task.run(); return null; }
        return Bukkit.getRegionScheduler().runDelayed(PLUGIN, location, t -> task.run(), clampDelay(delayTicks));
    }

    public static ScheduledTask runRegionTimer(Location location, Runnable task, long initialDelayTicks, long periodTicks) {
        if (!enabled()) { return null; }
        return Bukkit.getRegionScheduler()
                .runAtFixedRate(PLUGIN, location, t -> task.run(), clampDelay(initialDelayTicks), clampPeriod(periodTicks));
    }

    public static Optional<ScheduledTask> runEntity(Entity entity, Runnable task) {
        return runEntity(entity, task, null);
    }

    public static Optional<ScheduledTask> runEntity(Entity entity, Runnable task, Runnable retired) {
        if (!enabled()) { task.run(); return Optional.empty(); }
        return Optional.ofNullable(entity.getScheduler().run(PLUGIN, t -> task.run(), retired));
    }

    public static Optional<ScheduledTask> runEntityLater(Entity entity, Runnable task, long delayTicks) {
        return runEntityLater(entity, task, null, delayTicks);
    }

    public static Optional<ScheduledTask> runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        if (!enabled()) { task.run(); return Optional.empty(); }
        return Optional.ofNullable(entity.getScheduler().runDelayed(PLUGIN, t -> task.run(), retired, clampDelay(delayTicks)));
    }

    public static Optional<ScheduledTask> runEntityTimer(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        return runEntityTimer(entity, task, null, initialDelayTicks, periodTicks);
    }

    public static Optional<ScheduledTask> runEntityTimer(Entity entity, Runnable task, Runnable retired,
                                                         long initialDelayTicks, long periodTicks) {
        if (!enabled()) { return Optional.empty(); }
        return Optional.ofNullable(entity.getScheduler()
                .runAtFixedRate(PLUGIN, t -> task.run(), retired, clampDelay(initialDelayTicks), clampPeriod(periodTicks)));
    }

    public static ScheduledTask runTargetAware(CommandSender target, Runnable task) {
        return target instanceof Player player
                ? runEntity(player, task).orElse(null)
                : runGlobal(task);
    }

    public static ScheduledTask runTargetAwareLater(CommandSender target, Runnable task, long delayTicks) {
        return target instanceof Player player
                ? runEntityLater(player, task, delayTicks).orElse(null)
                : runGlobalLater(task, delayTicks);
    }

    public static ScheduledTask runTargetAwareTimer(CommandSender target, Runnable task, long initialDelayTicks, long periodTicks) {
        return target instanceof Player player
                ? runEntityTimer(player, task, initialDelayTicks, periodTicks).orElse(null)
                : runGlobalTimer(task, initialDelayTicks, periodTicks);
    }

    /** hands a future's outcome to {@code continuation} on {@code actor}'s own thread */
    public static <T> void continueOn(CommandSender actor, CompletableFuture<T> future,
                                      BiConsumer<? super T, ? super Throwable> continuation) {
        if (future.isDone()) {
            T value = null;
            Throwable error = null;
            try {
                value = future.join();
            } catch (CompletionException | CancellationException failure) {
                error = unwrap(failure);
            }
            continuation.accept(value, error);
            return;
        }
        future.whenComplete((value, error) -> runTargetAware(actor, () -> continuation.accept(value, unwrap(error))));
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private static long clampDelay(long delay) { return Math.max(1L, delay); }

    private static long clampPeriod(long period) { return Math.max(1L, period); }
}