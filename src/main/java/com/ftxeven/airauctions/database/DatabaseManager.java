package com.ftxeven.airauctions.database;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.database.dao.*;

import java.io.File;
import java.sql.*;

public final class DatabaseManager {

    private final AirAuctions plugin;
    private Connection connection;

    private AuctionListings auctionListings;
    private PlayerRecords playerRecords;
    private AuctionHistories auctionHistories;

    public DatabaseManager(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void init() throws SQLException {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataFolder.getAbsolutePath());
        }

        File dbFile = new File(dataFolder, "database.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        this.connection = DriverManager.getConnection(url);
        this.connection.setAutoCommit(true);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }

        createTables();

        this.auctionListings = new AuctionListings(plugin, connection);
        this.playerRecords = new PlayerRecords(plugin, connection);
        this.auctionHistories = new AuctionHistories(plugin, connection);
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS player_records (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                last_seen INTEGER NOT NULL,
                total_sold INTEGER NOT NULL DEFAULT 0,
                skin_value TEXT,
                skin_signature TEXT
            );
        """);

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS auction_listings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller_uuid TEXT NOT NULL,
                item_data BLOB NOT NULL,
                price REAL NOT NULL,
                currency_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expiry_at INTEGER NOT NULL,
                is_sold INTEGER DEFAULT 0,
                is_expired INTEGER DEFAULT 0
            );
        """);

            stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS auction_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller_uuid TEXT NOT NULL,
                buyer_uuid TEXT NOT NULL,
                item_data BLOB NOT NULL,
                price REAL NOT NULL,
                currency_id TEXT NOT NULL,
                sold_at INTEGER NOT NULL
            );
        """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_seller ON auction_listings(seller_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_active ON auction_listings(is_sold, is_expired);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_history_seller ON auction_history(seller_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_history_buyer ON auction_history(buyer_uuid);");
        }
    }

    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close database: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    @FunctionalInterface
    public interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    public synchronized void executeAsync(String sql, SQLConsumer<PreparedStatement> binder) {
        plugin.scheduler().runAsync(() -> {
            if (isClosed()) return;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                binder.accept(ps);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!isClosed()) {
                    plugin.getLogger().warning("SQL failed: " + e.getMessage());
                }
            }
        });
    }

    public boolean isClosed() {
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    public AuctionListings listings() { return auctionListings; }
    public PlayerRecords records() { return playerRecords; }
    public AuctionHistories history() { return auctionHistories; }
}