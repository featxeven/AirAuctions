package com.ftxeven.airauctions.database.dao;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.PlayerAuctionProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;
import java.util.function.Consumer;

public final class PlayerRecords {
    private final AirAuctions plugin;
    private final Connection connection;

    public PlayerRecords(AirAuctions plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public void load(UUID uuid, Consumer<ResultSet> callback) {
        String sql = "SELECT * FROM player_records WHERE uuid = ?;";
        plugin.scheduler().runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    callback.accept(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load player record for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public PlayerAuctionProfile loadSync(UUID uuid) {
        String sql = "SELECT total_sold, skin_value, skin_signature FROM player_records WHERE uuid = ? LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerAuctionProfile.SkinData skin = new PlayerAuctionProfile.SkinData(
                            rs.getString("skin_value"),
                            rs.getString("skin_signature")
                    );
                    return new PlayerAuctionProfile(uuid, rs.getInt("total_sold"), skin);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed sync load for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public void create(Player player) {
        String sql = "INSERT OR IGNORE INTO player_records (uuid, name, last_seen, total_sold) VALUES (?, ?, ?, 0);";

        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            ps.setLong(3, System.currentTimeMillis());
        });
    }

    public void updateJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        var profile = player.getPlayerProfile();
        var textures = profile.getProperties().stream()
                .filter(p -> p.getName().equals("textures"))
                .findFirst().orElse(null);

        String skinValue = textures != null ? textures.getValue() : null;
        String skinSignature = textures != null ? textures.getSignature() : null;

        String sql = """
            INSERT INTO player_records (uuid, name, last_seen, skin_value, skin_signature) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                last_seen = excluded.last_seen,
                skin_value = excluded.skin_value,
                skin_signature = excluded.skin_signature;
        """;

        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, skinValue);
            ps.setString(5, skinSignature);
        });
    }

    public UUID uuidFromName(String name) {
        if (name == null || name.isBlank()) return null;

        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();

        String sql = "SELECT uuid FROM player_records WHERE name = ? COLLATE NOCASE LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch UUID for " + name + ": " + e.getMessage());
        }
        return null;
    }

    public String getNameFromUuid(UUID uuid) {
        if (uuid == null) return "Unknown";

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        String sql = "SELECT name FROM player_records WHERE uuid = ? LIMIT 1;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch name for UUID " + uuid + ": " + e.getMessage());
        }
        return "Unknown";
    }

    public void incrementTotalSold(UUID uuid) {
        String sql = "UPDATE player_records SET total_sold = total_sold + 1 WHERE uuid = ?;";
        plugin.database().executeAsync(sql, ps -> ps.setString(1, uuid.toString()));
    }

    public int getTotalSold(UUID uuid) {
        String sql = "SELECT total_sold FROM player_records WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total_sold");
            }
        } catch (SQLException ignored) {}
        return 0;
    }
}