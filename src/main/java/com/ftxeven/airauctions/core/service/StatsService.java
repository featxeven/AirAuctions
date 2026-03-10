package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class StatsService {
    private final AirAuctions plugin;
    private final Map<String, CachedStat> cache = new ConcurrentHashMap<>();
    private static final long EXPIRE_TIME = TimeUnit.MINUTES.toMillis(5);

    public StatsService(AirAuctions plugin) { this.plugin = plugin; }

    public double getStat(UUID uuid, String cycle, boolean isSold) {
        String key = uuid.toString() + cycle + isSold;
        CachedStat cached = cache.get(key);

        if (cached != null && (System.currentTimeMillis() - cached.timestamp < EXPIRE_TIME)) {
            return cached.value;
        }

        plugin.scheduler().runAsync(() -> {
            double val;
            if (cycle.equalsIgnoreCase("ALLTIME")) {
                val = isSold ?
                        plugin.database().history().getSumSoldAllTime(uuid) :
                        plugin.database().history().getSumSpentAllTime(uuid);
            } else {
                long since = System.currentTimeMillis() - getMillisForCycle(cycle);
                val = isSold ?
                        plugin.database().history().getSumSold(uuid, since) :
                        plugin.database().history().getSumSpent(uuid, since);
            }
            cache.put(key, new CachedStat(val, System.currentTimeMillis()));
        });

        return cached != null ? cached.value : 0.0;
    }

    private long getMillisForCycle(String cycle) {
        return switch (cycle.toUpperCase()) {
            case "DAY" -> TimeUnit.DAYS.toMillis(1);
            case "WEEK" -> TimeUnit.DAYS.toMillis(7);
            case "MONTH" -> TimeUnit.DAYS.toMillis(30);
            default -> 0;
        };
    }

    private record CachedStat(double value, long timestamp) {}
}