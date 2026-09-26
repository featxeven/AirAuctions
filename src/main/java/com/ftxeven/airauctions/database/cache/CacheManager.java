package com.ftxeven.airauctions.database.cache;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.StorageConfig;
import com.ftxeven.airauctions.core.cache.WriteBehind;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.database.cache.sync.CacheSync;
import com.ftxeven.airauctions.database.cache.sync.RedisCacheSync;
import com.ftxeven.airauctions.util.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

public final class CacheManager {

    private final WriteBehind writes;
    private final ListingCache listings;
    private final PlayerCache players;
    private final HistoryCache history;
    private final CacheSync sync;

    public CacheManager(JavaPlugin plugin, ConfigManager configs, DatabaseManager database) {
        writes = new WriteBehind("airauctions-writeback", plugin.getLogger());

        StorageConfig.Redis redis = configs.storage().redis();
        String serverId = resolveServerId(plugin, configs.storage());

        sync = redis.enabled()
                ? new RedisCacheSync(plugin, buildJedisPool(redis), redis, serverId)
                : CacheSync.disabled();

        listings = new ListingCache(database.listings(), sync);
        listings.loadAll();

        players = new PlayerCache(database.players(), sync, Duration.ofMinutes(5));
        history = new HistoryCache();

        if (redis.enabled() && redis.resyncInterval() > 0) {
            long periodTicks = redis.resyncInterval() * 20L;
            Scheduler.runGlobalTimer(this::resync, periodTicks, periodTicks);
        }
    }

    public WriteBehind writes() {
        return writes;
    }

    public ListingCache listings() {
        return listings;
    }

    public PlayerCache players() {
        return players;
    }

    public HistoryCache history() {
        return history;
    }

    public void close() {
        writes.close();
        sync.close();
    }

    private void resync() {
        Scheduler.runAsync(() -> {
            listings.reload();
            players.invalidateAll();
            history.invalidateAll();
        });
    }

    private JedisPool buildJedisPool(StorageConfig.Redis redis) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(redis.poolSize());

        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(redis.password().isBlank() ? null : redis.password())
                .database(redis.database())
                .timeoutMillis(redis.timeout())
                .build();

        return new JedisPool(poolConfig, new HostAndPort(redis.host(), redis.port()), clientConfig);
    }

    // an explicit server-id always wins; otherwise it's read from (or generated and
    // persisted to) data/.server-id
    private String resolveServerId(JavaPlugin plugin, StorageConfig config) {
        String configured = config.serverId();
        if (!configured.isBlank()) {
            return configured;
        }

        File file = new File(plugin.getDataFolder(), "data/.server-id");
        String stored = readServerId(file);
        if (stored != null) {
            return stored;
        }

        String generated = UUID.randomUUID().toString();
        writeServerId(plugin, file, generated);
        plugin.getLogger().info("No server-id configured, generated new server-id: " + generated);
        return generated;
    }

    private String readServerId(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeServerId(JavaPlugin plugin, File file, String serverId) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(file.toPath(), serverId, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not persist generated server-id to " + file.getPath()
                    + ", a new one will be generated on next restart: " + e.getMessage());
        }
    }
}