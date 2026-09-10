package com.ftxeven.airauctions.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Cooldowns<K> {

    private final Map<K, Long> lastHitAt = new ConcurrentHashMap<>();

    public void hit(K key) {
        hit(key, System.currentTimeMillis());
    }

    public void hit(K key, long nowMillis) {
        lastHitAt.put(key, nowMillis);
    }

    public void clear(K key) {
        lastHitAt.remove(key);
    }

    public double remainingSeconds(K key, double cooldownSeconds) {
        return remainingSeconds(key, cooldownSeconds, System.currentTimeMillis());
    }

    public double remainingSeconds(K key, double cooldownSeconds, long nowMillis) {
        Long last = lastHitAt.get(key);
        if (last == null) {
            return 0;
        }
        double elapsedSeconds = (nowMillis - last) / 1000.0;
        return Math.max(0, cooldownSeconds - elapsedSeconds);
    }

    public double checkAndStart(K key, double cooldownSeconds, long nowMillis) {
        if (cooldownSeconds <= 0) {
            return -1;
        }
        double remaining = remainingSeconds(key, cooldownSeconds, nowMillis);
        if (remaining > 0) {
            return remaining;
        }
        hit(key, nowMillis);
        return -1;
    }

    public void mergeFrom(Cooldowns<K> other) {
        lastHitAt.putAll(other.lastHitAt);
    }
}