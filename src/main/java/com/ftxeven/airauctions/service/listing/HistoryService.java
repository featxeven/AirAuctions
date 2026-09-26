package com.ftxeven.airauctions.service.listing;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.cache.WriteBehind;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.database.cache.CacheManager;
import com.ftxeven.airauctions.database.cache.HistoryCache;
import com.ftxeven.airauctions.database.query.FacetCounts;
import com.ftxeven.airauctions.database.query.HistoryQuery;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.database.query.TransactionKind;
import com.ftxeven.airauctions.database.repository.ListingMetadataResolver;
import com.ftxeven.airauctions.model.HistoryEntry;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class HistoryService {

    private final DatabaseManager database;
    private final HistoryCache cache;
    private final WriteBehind writes;
    private final ConfigManager configs;

    public HistoryService(DatabaseManager database, CacheManager cacheManager, ConfigManager configs) {
        this.database = database;
        this.cache = cacheManager.history();
        this.writes = cacheManager.writes();
        this.configs = configs;
    }

    public PageResult<HistoryEntry> query(HistoryQuery query) {
        return cache.queryPage(query, () -> database.history().query(query));
    }

    public FacetCounts facets(HistoryQuery query) {
        return cache.queryFacets(query, () -> database.history().facets(query));
    }

    public Optional<HistoryEntry> find(String listingId, Instant completedAt) {
        return database.history().find(listingId, completedAt);
    }

    public void record(HistoryEntry entry) {
        int maxHistory = configs.main().listings().maxHistory();
        HistoryEntry.Info info = entry.info();

        writes.append(() -> {
            try {
                database.history().append(entry);
                if (maxHistory > 0) {
                    database.history().trim(info.seller(), maxHistory);
                    if (!info.buyer().equals(info.seller())) {
                        database.history().trim(info.buyer(), maxHistory);
                    }
                }
            } catch (Exception e) {
                throw new Exception("Could not record history entry for listing " + info.id(), e);
            }
            cache.invalidateAll();
        });
    }

    public Map<String, Double> spent(UUID player, @Nullable Instant since) {
        return database.history().sumForPlayer(player, TransactionKind.SPENT, since);
    }

    public Map<String, Double> earned(UUID player, @Nullable Instant since) {
        return database.history().sumForPlayer(player, TransactionKind.EARNED, since);
    }

    public Map<String, Double> volume(@Nullable Instant since) {
        return database.history().sumGlobalVolume(since);
    }

    public int resyncMetadata(ListingMetadataResolver resolver) {
        int updated = database.history().resyncMetadata(resolver);
        if (updated > 0) {
            cache.invalidateAll();
        }
        return updated;
    }

    public int deleteBySeller(Collection<UUID> sellers) {
        int removed = database.history().deleteBySeller(sellers);
        if (removed > 0) {
            cache.invalidateAll();
        }
        return removed;
    }
}