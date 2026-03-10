package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.PlayerAuctionProfile;
import com.ftxeven.airauctions.core.model.PlayerAuctionProfile.SkinData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProfileService {

    private final AirAuctions plugin;
    private final Map<UUID, PlayerAuctionProfile> profiles = new ConcurrentHashMap<>();

    public PlayerProfileService(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void loadProfile(Player player) {
        UUID uuid = player.getUniqueId();

        plugin.database().records().load(uuid, rs -> {
            try {
                if (rs.next()) {
                    SkinData skin = new SkinData(
                            rs.getString("skin_value"),
                            rs.getString("skin_signature")
                    );

                    PlayerAuctionProfile profile = new PlayerAuctionProfile(
                            uuid,
                            rs.getInt("total_sold"),
                            skin
                    );
                    profiles.put(uuid, profile);
                } else {
                    PlayerAuctionProfile newProfile = new PlayerAuctionProfile(uuid, 0, null);
                    profiles.put(uuid, newProfile);
                    plugin.database().records().create(player);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load profile for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void unloadProfile(UUID uuid) {
        profiles.remove(uuid);
    }

    public PlayerAuctionProfile get(UUID uuid) {
        PlayerAuctionProfile profile = profiles.get(uuid);
        if (profile != null) return profile;

        return plugin.database().records().loadSync(uuid);
    }

    public String getName(UUID uuid) {
        if (uuid == null) return "Unknown";

        PlayerAuctionProfile profile = profiles.get(uuid);
        if (profile != null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) return online.getName();
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        return plugin.database().records().getNameFromUuid(uuid);
    }

    public void unloadAll() {
        profiles.clear();
    }
}