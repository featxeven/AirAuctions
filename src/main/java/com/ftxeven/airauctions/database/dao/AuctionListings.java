package com.ftxeven.airauctions.database.dao;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.ItemSerializerUtil;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class AuctionListings {
    private final AirAuctions plugin;
    private final Connection connection;

    public AuctionListings(AirAuctions plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public int createListingSync(UUID seller, ItemStack item, double price, long expiry, String currencyId) {
        String sql = "INSERT INTO auction_listings (seller_uuid, item_data, price, created_at, expiry_at, currency_id) VALUES (?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.toString());
            ps.setBytes(2, ItemSerializerUtil.serialize(item));
            ps.setDouble(3, price);
            ps.setLong(4, System.currentTimeMillis());
            ps.setLong(5, expiry);
            ps.setString(6, currencyId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error (createListing): " + e.getMessage());
        }
        return -1;
    }

    public void processPurchase(int id, UUID buyer, UUID seller, ItemStack item, double price, String currencyId) {
        byte[] serializedItem = ItemSerializerUtil.serialize(item);

        plugin.database().executeAsync("UPDATE auction_listings SET is_sold = 1 WHERE id = ?;", ps -> ps.setInt(1, id));

        String historySql = "INSERT INTO auction_history (id, seller_uuid, buyer_uuid, item_data, price, currency_id, sold_at) VALUES (?, ?, ?, ?, ?, ?, ?);";
        plugin.database().executeAsync(historySql, ps -> {
            ps.setInt(1, id);
            ps.setString(2, seller.toString());
            ps.setString(3, buyer.toString());
            ps.setBytes(4, serializedItem);
            ps.setDouble(5, price);
            ps.setString(6, currencyId);
            ps.setLong(7, System.currentTimeMillis());
        });

        purgeHistory(seller);
        purgeHistory(buyer);
    }

    private void purgeHistory(UUID uuid) {
        int limit = plugin.config().historyLimit();
        String s = uuid.toString();

        String sql = "DELETE FROM auction_history WHERE (seller_uuid = ? OR buyer_uuid = ?) AND id NOT IN " +
                "(SELECT id FROM (SELECT id FROM auction_history WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY sold_at DESC LIMIT ?) tmp);";

        plugin.database().executeAsync(sql, ps -> {
            ps.setString(1, s); ps.setString(2, s);
            ps.setString(3, s); ps.setString(4, s);
            ps.setInt(5, limit);
        });

        int purgeSeconds = plugin.config().purgeTime();
        if (purgeSeconds > 0) {
            long threshold = System.currentTimeMillis() - (purgeSeconds * 1000L);
            plugin.database().executeAsync("DELETE FROM auction_history WHERE (seller_uuid = ? OR buyer_uuid = ?) AND sold_at < ?;", ps -> {
                ps.setString(1, s);
                ps.setString(2, s);
                ps.setLong(3, threshold);
            });
        }
    }

    public void getAllActive(Consumer<List<AuctionListing>> callback) {
        plugin.scheduler().runAsync(() -> {
            List<AuctionListing> list = new ArrayList<>();
            String sql = "SELECT * FROM auction_listings WHERE is_sold = 0 AND is_expired = 0 ORDER BY created_at DESC";
            String defaultCurrency = plugin.config().economyDefaultProvider();

            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String currency = rs.getString("currency_id");
                    if (currency == null) currency = defaultCurrency;

                    list.add(new AuctionListing(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            ItemSerializerUtil.deserialize(rs.getBytes("item_data")),
                            rs.getDouble("price"),
                            currency,
                            rs.getLong("created_at"),
                            rs.getLong("expiry_at")
                    ));
                }
                callback.accept(list);
            } catch (SQLException e) {
                plugin.getLogger().warning("Load Error: " + e.getMessage());
            }
        });
    }

    public List<AuctionListing> getExpiredSync(UUID seller) {
        List<AuctionListing> list = new ArrayList<>();
        String sql = "SELECT * FROM auction_listings WHERE seller_uuid = ? AND is_sold = 0 AND is_expired = 1 " +
                "ORDER BY expiry_at DESC LIMIT ?;";
        String defaultCurrency = plugin.config().economyDefaultProvider();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            ps.setInt(2, plugin.config().expiredLimit());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String currency = rs.getString("currency_id");
                    if (currency == null) currency = defaultCurrency;

                    list.add(new AuctionListing(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            ItemSerializerUtil.deserialize(rs.getBytes("item_data")),
                            rs.getDouble("price"),
                            currency,
                            rs.getLong("created_at"),
                            rs.getLong("expiry_at")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error (getExpiredSync): " + e.getMessage());
        }
        return list;
    }

    public AuctionListing getExpiredByIdSync(int id) {
        String sql = "SELECT * FROM auction_listings WHERE id = ? AND is_expired = 1 AND is_sold = 0;";
        String defaultCurrency = plugin.config().economyDefaultProvider();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String currency = rs.getString("currency_id");
                    if (currency == null) currency = defaultCurrency;

                    return new AuctionListing(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            ItemSerializerUtil.deserialize(rs.getBytes("item_data")),
                            rs.getDouble("price"),
                            currency,
                            rs.getLong("created_at"),
                            rs.getLong("expiry_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error (getExpiredByIdSync): " + e.getMessage());
        }
        return null;
    }

    public boolean deleteListingSync(int id) {
        String sql = "DELETE FROM auction_listings WHERE id = ?;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error (deleteListingSync): " + e.getMessage());
            return false;
        }
    }

    public int getExpiredCountSync(UUID seller) {
        String sql = "SELECT COUNT(*) FROM auction_listings WHERE seller_uuid = ? AND is_expired = 1 AND is_sold = 0;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB Error (getExpiredCountSync): " + e.getMessage());
        }
        return 0;
    }

    public void deletePurgedSync(UUID seller, int purgeSeconds) {
        long now = System.currentTimeMillis();
        long buffer = purgeSeconds * 1000L;

        String sql = "DELETE FROM auction_listings WHERE seller_uuid = ? " +
                "AND is_expired = 1 AND is_sold = 0 " +
                "AND (expiry_at + ?) < ?;";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            ps.setLong(2, buffer);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Lazy Purge Error: " + e.getMessage());
        }
    }

    public void markAsExpired(int id) {
        long now = System.currentTimeMillis();
        plugin.database().executeAsync("UPDATE auction_listings SET is_expired = 1, expiry_at = ? WHERE id = ?;", ps -> {
            ps.setLong(1, now);
            ps.setInt(2, id);
        });
    }
}