package com.ftxeven.airauctions.database.dao;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionHistory;
import com.ftxeven.airauctions.util.ItemSerializerUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuctionHistories {
    private final AirAuctions plugin;
    private final Connection connection;

    public AuctionHistories(AirAuctions plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public List<AuctionHistory> getHistorySync(UUID uuid, int limit) {
        List<AuctionHistory> list = new ArrayList<>();
        String sql = "SELECT * FROM auction_history WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY sold_at DESC LIMIT ?;";
        String defaultCurrency = plugin.config().economyDefaultProvider();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String s = uuid.toString();
            ps.setString(1, s);
            ps.setString(2, s);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String currency = rs.getString("currency_id");
                    if (currency == null) currency = defaultCurrency;

                    list.add(new AuctionHistory(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            UUID.fromString(rs.getString("buyer_uuid")),
                            ItemSerializerUtil.deserialize(rs.getBytes("item_data")),
                            rs.getDouble("price"),
                            currency,
                            rs.getLong("sold_at")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("History Load Error: " + e.getMessage());
        }
        return list;
    }

    public AuctionHistory getHistoryByIdSync(int id) {
        String sql = "SELECT * FROM auction_history WHERE id = ?;";
        String defaultCurrency = plugin.config().economyDefaultProvider();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String currency = rs.getString("currency_id");
                    if (currency == null) currency = defaultCurrency;

                    return new AuctionHistory(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            UUID.fromString(rs.getString("buyer_uuid")),
                            ItemSerializerUtil.deserialize(rs.getBytes("item_data")),
                            rs.getDouble("price"),
                            currency,
                            rs.getLong("sold_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch history by ID: " + e.getMessage());
        }
        return null;
    }

    public double getSumSold(UUID uuid, long since) {
        String sql = "SELECT SUM(price) FROM auction_history WHERE seller_uuid = ? AND sold_at >= ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch sold stats: " + e.getMessage());
        }
        return 0.0;
    }

    public double getSumSpent(UUID uuid, long since) {
        String sql = "SELECT SUM(price) FROM auction_history WHERE buyer_uuid = ? AND sold_at >= ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch spent stats: " + e.getMessage());
        }
        return 0.0;
    }

    public double getSumSoldAllTime(UUID uuid) {
        String sql = "SELECT SUM(price) FROM auction_history WHERE seller_uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch all-time sold stats: " + e.getMessage());
        }
        return 0.0;
    }

    public double getSumSpentAllTime(UUID uuid) {
        String sql = "SELECT SUM(price) FROM auction_history WHERE buyer_uuid = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch all-time spent stats: " + e.getMessage());
        }
        return 0.0;
    }
}