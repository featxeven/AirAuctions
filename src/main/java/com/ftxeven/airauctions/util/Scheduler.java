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
import java.util.concurrent.TimeUnit;

public final class Scheduler {

    private static final boolean FOLIA = foliaPresent();
    private static final Plugin PLUGIN = JavaPlugin.getProvidingPlugin(Scheduler.class);

    private Scheduler() {
    }

    public static ScheduledTask runGlobal(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(PLUGIN, t -> task.run());
    }

    public static ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(PLUGIN, t -> task.run(), clampDelay(delayTicks));
    }

    public static ScheduledTask runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(PLUGIN, t -> task.run(), clampDelay(initialDelayTicks), periodTicks);
    }

    public static void cancelGlobal() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(PLUGIN);
    }

    public static ScheduledTask runAsync(Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(PLUGIN, t -> task.run());
    }

    public static ScheduledTask runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runDelayed(PLUGIN, t -> task.run(), delay, unit);
    }

    public static ScheduledTask runAsyncTimer(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(PLUGIN, t -> task.run(), clampDelay(initialDelay), period, unit);
    }

    public static void cancelAsync() {
        Bukkit.getAsyncScheduler().cancelTasks(PLUGIN);
    }

    // location-based, doesn't follow entities around
    public static ScheduledTask runRegion(Location location, Runnable task) {
        return Bukkit.getRegionScheduler().run(PLUGIN, location, t -> task.run());
    }

    public static ScheduledTask runRegionLater(Location location, Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(PLUGIN, location, t -> task.run(), clampDelay(delayTicks));
    }

    public static ScheduledTask runRegionTimer(Location location, Runnable task, long initialDelayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler().runAtFixedRate(PLUGIN, location, t -> task.run(), clampDelay(initialDelayTicks), periodTicks);
    }

    // empty if the entity was already retired when called
    public static Optional<ScheduledTask> runEntity(Entity entity, Runnable task) {
        return runEntity(entity, task, null);
    }

    public static Optional<ScheduledTask> runEntity(Entity entity, Runnable task, Runnable retired) {
        return Optional.ofNullable(entity.getScheduler().run(PLUGIN, t -> task.run(), retired));
    }

    public static Optional<ScheduledTask> runEntityLater(Entity entity, Runnable task, long delayTicks) {
        return runEntityLater(entity, task, null, delayTicks);
    }

    public static Optional<ScheduledTask> runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        return Optional.ofNullable(entity.getScheduler().runDelayed(PLUGIN, t -> task.run(), retired, delayTicks));
    }

    public static Optional<ScheduledTask> runEntityTimer(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        return runEntityTimer(entity, task, null, initialDelayTicks, periodTicks);
    }

    public static Optional<ScheduledTask> runEntityTimer(Entity entity, Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
        return Optional.ofNullable(entity.getScheduler().runAtFixedRate(PLUGIN, t -> task.run(), retired, clampDelay(initialDelayTicks), periodTicks));
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

    private static long clampDelay(long delay) {
        return Math.max(1L, delay);
    }

    private static boolean foliaPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}