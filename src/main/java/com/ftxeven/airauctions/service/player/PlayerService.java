package com.ftxeven.airauctions.service.player;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.database.cache.CacheManager;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.model.PlayerData;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class PlayerService {

    private final CacheManager cache;
    private final DatabaseManager database;
    private final EconomyService economy;

    public PlayerService(CacheManager cache, DatabaseManager database, EconomyService economy) {
        this.cache = cache;
        this.database = database;
        this.economy = economy;
    }

    // Profile lookups

    public Optional<PlayerData> find(UUID uuid) {
        return cache.players().find(uuid);
    }

    public Optional<PlayerData> findByName(String name) {
        return cache.players().findByName(name);
    }

    public Map<UUID, PlayerData> findAll(Collection<UUID> uuids) {
        return cache.players().findAll(uuids);
    }

    // falls back to the raw uuid so %seller%/%buyer%/%bidder% never renders blank
    public String name(UUID uuid) {
        return find(uuid).map(PlayerData::name).orElse(uuid.toString());
    }

    public List<UUID> findByNamePrefix(String prefix) {
        return database.players().findByNamePrefix(prefix);
    }

    // Join handling

    public void handleJoin(Player player, Runnable afterJoin) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        PlayerData.Skin skin = captureSkin(player);

        Scheduler.runAsync(() -> {
            database.players().upsert(uuid, name, skin);
            database.players().find(uuid).ifPresent(cache.players()::warm);
            afterJoin.run();
        });
    }

    public void registerSynthetic(UUID uuid, String name) {
        database.players().upsert(uuid, name, PlayerData.Skin.EMPTY);
        database.players().find(uuid).ifPresent(cache.players()::warm);
    }

    private PlayerData.Skin captureSkin(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if ("textures".equals(property.getName())
                    && property.getSignature() != null && !property.getSignature().isEmpty()) {
                return new PlayerData.Skin(property.getValue(), property.getSignature());
            }
        }
        return PlayerData.Skin.EMPTY;
    }

    // Extra slots

    public SlotAdjustment adjustExtraSlots(UUID uuid, int delta) {
        if (delta < 0) {
            int current = find(uuid).map(PlayerData::extraSlots).orElse(0);
            delta = -Math.min(-delta, current);
        }
        int total = database.players().adjustExtraSlots(uuid, delta);
        cache.players().invalidate(uuid);
        return new SlotAdjustment(Math.abs(delta), total);
    }

    // Payouts + Refunds

    public void payout(UUID recipient, String economyId, double amount) {
        give(recipient, economyId, amount, false);
    }

    public void refund(UUID recipient, String economyId, double amount) {
        give(recipient, economyId, amount, true);
    }

    private void give(UUID recipient, String economyId, double amount, boolean isRefund) {
        Optional<EconomyProvider> provider = economy.get(economyId);
        boolean deposited = provider.isPresent()
                && Bukkit.getPlayer(recipient) != null
                && provider.get().deposit(Bukkit.getOfflinePlayer(recipient), amount);
        if (deposited) {
            return;
        }
        cache.writes().append(() -> {
            try {
                if (isRefund) {
                    database.players().addPendingRefunds(recipient, economyId, amount);
                } else {
                    database.players().addPendingEarnings(recipient, economyId, amount);
                }
            } catch (Exception e) {
                throw new Exception("Could not record pending " + (isRefund ? "refund" : "earnings")
                        + " of " + amount + " " + economyId + " for " + recipient, e);
            }
            cache.players().invalidate(recipient);
        });
    }

    public void clearPendingEarnings(UUID uuid) {
        cache.writes().append(() -> {
            try {
                database.players().clearPendingEarnings(uuid);
            } catch (Exception e) {
                throw new Exception("Could not clear pending earnings for " + uuid, e);
            }
            cache.players().invalidate(uuid);
        });
    }

    public void clearPendingRefunds(UUID uuid) {
        cache.writes().append(() -> {
            try {
                database.players().clearPendingRefunds(uuid);
            } catch (Exception e) {
                throw new Exception("Could not clear pending refunds for " + uuid, e);
            }
            cache.players().invalidate(uuid);
        });
    }

    // Result

    public record SlotAdjustment(int applied, int total) {}
}