package com.ftxeven.airauctions.database.cache;

import com.ftxeven.airauctions.database.cache.sync.CacheSync;
import com.ftxeven.airauctions.database.query.FacetCounts;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.database.query.ListingSort;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.database.repository.ListingMetadataResolver;
import com.ftxeven.airauctions.database.repository.ListingRepository;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.model.ListingStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ListingCache {

    private static final String CHANNEL = "listings";

    private final ListingRepository repository;
    private final CacheSync sync;

    private final Map<String, Listing> byId = new ConcurrentHashMap<>();

    private final Map<ListingScope, Set<String>> idsByScope = new EnumMap<>(ListingScope.class);
    private final Map<ListingScope, Map<UUID, Set<String>>> idsByOwner = new EnumMap<>(ListingScope.class);
    private final Map<ListingScope, AtomicLong> versions = new EnumMap<>(ListingScope.class);
    private final Map<ListingScope, Map<ListingSort, SortedView>> views = new EnumMap<>(ListingScope.class);

    // seller -> every listing id owned by them, regardless of status
    private final Map<UUID, Set<String>> idsBySeller = new ConcurrentHashMap<>();

    // per-listing set of bidders seen so far, used only to decide whether an incoming offer is
    // from a brand-new bidder or a repeat one
    private final Map<String, Set<UUID>> biddersSeen = new ConcurrentHashMap<>();

    public ListingCache(ListingRepository repository, CacheSync sync) {
        this.repository = repository;
        this.sync = sync;

        for (ListingScope scope : ListingScope.values()) {
            idsByScope.put(scope, ConcurrentHashMap.newKeySet());
            idsByOwner.put(scope, new ConcurrentHashMap<>());
            versions.put(scope, new AtomicLong());

            Map<ListingSort, SortedView> bySort = new EnumMap<>(ListingSort.class);
            for (ListingSort sort : ListingSort.values()) {
                bySort.put(sort, new SortedView(scope, sort));
            }
            views.put(scope, bySort);
        }

        sync.subscribe(CHANNEL, this::onRemoteInvalidate);
    }

    // Startup / recovery

    public void loadAll() {
        for (ListingScope scope : ListingScope.values()) {
            for (Listing listing : repository.findAll(ListingQuery.builder(scope, 1).build())) {
                applyIndex(listing);
            }
        }
    }

    public void reload() {
        byId.clear();
        idsByScope.values().forEach(Set::clear);
        idsByOwner.values().forEach(Map::clear);
        idsBySeller.clear();
        biddersSeen.clear();
        versions.values().forEach(AtomicLong::incrementAndGet);
        loadAll();
    }

    public int size() {
        return byId.size();
    }

    // Lookup

    public Optional<Listing> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<String> recentIds(int limit) {
        return byId.values().stream()
                .sorted(Comparator.comparing((Listing l) -> l.info().createdAt()).reversed())
                .limit(limit)
                .map(l -> l.info().id())
                .toList();
    }

    public PageResult<Listing> query(ListingQuery query) {
        List<Listing> matches = matching(query);
        long total = matches.size();
        if (total == 0) {
            return PageResult.empty(query.page());
        }

        int pageSize = query.pageSize();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int page = Math.clamp(query.page(), 1, totalPages);
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, matches.size());

        return new PageResult<>(matches.subList(from, to), page, totalPages, total);
    }

    public FacetCounts facets(ListingQuery query) {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        Map<String, Long> byEconomy = new LinkedHashMap<>();
        Map<String, Long> byListingType = new LinkedHashMap<>();

        ListingQuery categoryBase = query.withoutCategory();
        ListingQuery economyBase = query.withoutEconomy();
        ListingQuery typeBase = query.withoutListingType();

        // a single pass over the (already owner/scope-narrowed) candidate set computes all
        // three facet groups together, instead of three separate scans
        for (Listing listing : rawCandidates(query.scope(), query.owner())) {
            if (matches(listing, categoryBase)) {
                byCategory.merge(listing.info().category(), 1L, Long::sum);
            }
            if (matches(listing, economyBase)) {
                byEconomy.merge(listing.info().economy(), 1L, Long::sum);
            }
            if (matches(listing, typeBase)) {
                byListingType.merge(listing.type().name(), 1L, Long::sum);
            }
        }
        return FacetCounts.of(byCategory, byEconomy, byListingType);
    }

    public List<Listing> findAll(ListingQuery query) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : orderedCandidates(query)) {
            if (matches(listing, query)) {
                result.add(listing);
            }
        }
        return result;
    }

    public int count(ListingQuery query) {
        int count = 0;
        for (Listing listing : rawCandidates(query.scope(), query.owner())) {
            if (matches(listing, query)) {
                count++;
            }
        }
        return count;
    }

    public int countBySeller(Collection<UUID> sellers) {
        int count = 0;
        for (UUID seller : sellers) {
            Set<String> ids = idsBySeller.get(seller);
            count += ids != null ? ids.size() : 0;
        }
        return count;
    }

    public List<Listing.Info> dueToExpire(Instant now) {
        List<Listing.Info> due = new ArrayList<>();
        for (String id : idsByScope.get(ListingScope.ACTIVE)) {
            Listing listing = byId.get(id);
            if (listing != null && !listing.info().expiresAt().isAfter(now)) {
                due.add(listing.info());
            }
        }
        return due;
    }

    public List<String> dueToPurge(ListingScope scope, Instant cutoff) {
        List<String> due = new ArrayList<>();
        for (String id : idsByScope.get(scope)) {
            Listing listing = byId.get(id);
            Instant endedAt = listing != null ? listing.info().endedAt() : null;
            if (endedAt != null && !endedAt.isAfter(cutoff)) {
                due.add(id);
            }
        }
        return due;
    }

    // Mutations - creation / removal

    public void index(Listing listing) {
        applyIndex(listing);
        sync.publish(CHANNEL, listing.info().id());
    }

    public void remove(String id) {
        applyRemove(id);
        sync.publish(CHANNEL, id);
    }

    public int removeBySeller(Collection<UUID> sellers) {
        int removed = 0;
        for (UUID seller : sellers) {
            Set<String> ids = idsBySeller.get(seller);
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            for (String id : List.copyOf(ids)) {
                remove(id);
                removed++;
            }
        }
        return removed;
    }

    // Mutations - atomic in-memory transitions

    public OptionalInt tryReduceStock(String id, int amount) {
        int[] result = {-1};
        byId.computeIfPresent(id, (key, current) -> {
            if (!(current instanceof Listing.Auction(Listing.Info info, double price, int remainingAmount)) || info.status() != ListingStatus.ACTIVE
                    || remainingAmount < amount) {
                return current;
            }
            Listing.Auction updated = new Listing.Auction(info, price, remainingAmount - amount);
            result[0] = updated.remainingAmount();
            return updated;
        });
        if (result[0] >= 0) {
            sync.publish(CHANNEL, id);
            return OptionalInt.of(result[0]);
        }
        return OptionalInt.empty();
    }

    public OptionalInt tryPlaceBid(String id, UUID bidder, double offer, Instant newExpiresAt) {
        int[] result = {-1};
        byId.computeIfPresent(id, (key, current) -> {
            if (!(current instanceof Listing.Bid bid) || bid.info().status() != ListingStatus.ACTIVE
                    || bid.currentPrice() >= offer) {
                return current;
            }
            int totalBidders = registerBidder(id, bidder, bid.totalBidders());
            Listing.Bid updated = new Listing.Bid(withExpiry(bid.info(), newExpiresAt), bid.startingPrice(), offer,
                    bidder, totalBidders, bid.remindersShown());
            result[0] = totalBidders;
            return updated;
        });
        if (result[0] >= 0) {
            versions.get(ListingScope.ACTIVE).incrementAndGet(); // currentPrice feeds PRICE sort
            sync.publish(CHANNEL, id);
            return OptionalInt.of(result[0]);
        }
        return OptionalInt.empty();
    }

    public Optional<Instant> tryTransition(String id, ListingStatus newStatus) {
        Instant endedAt = Instant.now();
        Listing[] before = new Listing[1];
        Listing[] after = new Listing[1];

        byId.computeIfPresent(id, (key, current) -> {
            if (current.info().status() != ListingStatus.ACTIVE) {
                return current;
            }
            before[0] = current;
            after[0] = withStatus(current, newStatus, endedAt);
            return after[0];
        });

        if (after[0] == null) {
            return Optional.empty();
        }

        unlink(before[0], ListingScope.ACTIVE);
        ListingScope newScope = ListingScope.forStatus(newStatus);
        link(after[0], newScope);
        versions.get(ListingScope.ACTIVE).incrementAndGet();
        versions.get(newScope).incrementAndGet();
        sync.publish(CHANNEL, id);
        return Optional.of(endedAt);
    }

    public void incrementBidReminders(String id) {
        byId.computeIfPresent(id, (key, current) -> current instanceof Listing.Bid(
                Listing.Info info, double startingPrice, double currentPrice, UUID currentBidder, int totalBidders,
                int remindersShown
        )
                ? new Listing.Bid(info, startingPrice, currentPrice, currentBidder, totalBidders, remindersShown + 1)
                : current);
        sync.publish(CHANNEL, id);
    }

    // Maintenance

    public int resyncMetadata(ListingMetadataResolver resolver) {
        int updated = 0;
        for (Listing listing : byId.values()) {
            ListingMetadataResolver.Metadata metadata = resolver.resolve(listing.info().item());
            if (metadata.category().equals(listing.info().category()) && metadata.searchName().equals(listing.info().searchName())) {
                continue;
            }
            byId.put(listing.info().id(), withMetadata(listing, metadata));
            updated++;
        }
        return updated;
    }

    // Validity

    public static boolean isExpired(Listing.Info info, Instant now) {
        return !now.isBefore(info.expiresAt());
    }

    public static boolean isPurged(Listing.Info info, long purgeDelaySeconds, Instant now) {
        if (purgeDelaySeconds < 0 || info.endedAt() == null) {
            return false;
        }
        return !now.isBefore(info.endedAt().plusSeconds(purgeDelaySeconds));
    }

    public static boolean isValid(Listing.Info info, ListingScope scope, long purgeDelaySeconds, Instant now) {
        return switch (scope) {
            case ACTIVE -> !isExpired(info, now);
            case EXPIRED, STORAGE -> !isPurged(info, purgeDelaySeconds, now);
        };
    }

    // Internal indexing

    private void applyIndex(Listing listing) {
        Listing.Info info = listing.info();
        ListingScope scope = ListingScope.forStatus(info.status());

        Listing previous = byId.put(info.id(), listing);
        if (previous != null) {
            ListingScope previousScope = ListingScope.forStatus(previous.info().status());
            if (previousScope != scope) {
                unlink(previous, previousScope);
                link(listing, scope);
                versions.get(previousScope).incrementAndGet();
            }
        } else {
            link(listing, scope);
        }
        idsBySeller.computeIfAbsent(info.seller(), k -> ConcurrentHashMap.newKeySet()).add(info.id());
        versions.get(scope).incrementAndGet();
    }

    private void applyRemove(String id) {
        Listing removed = byId.remove(id);
        if (removed == null) {
            return;
        }
        ListingScope scope = ListingScope.forStatus(removed.info().status());
        unlink(removed, scope);

        Set<String> sellerIds = idsBySeller.get(removed.info().seller());
        if (sellerIds != null) {
            sellerIds.remove(id);
        }
        biddersSeen.remove(id);
        versions.get(scope).incrementAndGet();
    }

    private void onRemoteInvalidate(String id) {
        com.ftxeven.airauctions.util.Scheduler.runAsync(() -> {
            try {
                Optional<Listing> fresh = repository.find(id);
                if (fresh.isPresent()) {
                    applyIndex(fresh.get());
                } else {
                    applyRemove(id);
                }
            } catch (Exception ignored) {
                // a future local mutation on this id republishes and retries
            }
        });
    }

    private void link(Listing listing, ListingScope scope) {
        idsByScope.get(scope).add(listing.info().id());
        UUID owner = ownerFor(listing, scope);
        if (owner != null) {
            idsByOwner.get(scope).computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(listing.info().id());
        }
    }

    private void unlink(Listing listing, ListingScope scope) {
        idsByScope.get(scope).remove(listing.info().id());
        UUID owner = ownerFor(listing, scope);
        if (owner != null) {
            Set<String> owned = idsByOwner.get(scope).get(owner);
            if (owned != null) {
                owned.remove(listing.info().id());
            }
        }
    }

    private static @Nullable UUID ownerFor(Listing listing, ListingScope scope) {
        return switch (scope.ownerRole()) {
            case SELLER -> listing.info().seller();
            case CURRENT_BIDDER -> listing instanceof Listing.Bid bid ? bid.currentBidder() : null;
        };
    }

    private int registerBidder(String listingId, UUID bidder, int currentTotal) {
        Set<UUID> seen = biddersSeen.computeIfAbsent(listingId, k -> ConcurrentHashMap.newKeySet());
        return seen.add(bidder) ? currentTotal + 1 : currentTotal;
    }

    // Querying

    private List<Listing> matching(ListingQuery query) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : orderedCandidates(query)) {
            if (matches(listing, query)) {
                result.add(listing);
            }
        }
        return result;
    }

    private List<Listing> orderedCandidates(ListingQuery query) {
        if (query.owner() != null) {
            List<Listing> owned = new ArrayList<>(rawCandidates(query.scope(), query.owner()));
            owned.sort(comparator(query.sort(), query.scope()));
            return owned;
        }
        return views.get(query.scope()).get(query.sort()).get();
    }

    private List<Listing> rawCandidates(ListingScope scope, @Nullable UUID owner) {
        Set<String> ids = owner != null
                ? idsByOwner.get(scope).getOrDefault(owner, Set.of())
                : idsByScope.get(scope);

        List<Listing> listings = new ArrayList<>(ids.size());
        for (String id : ids) {
            Listing listing = byId.get(id);
            if (listing != null) {
                listings.add(listing);
            }
        }
        return listings;
    }

    private boolean matches(Listing listing, ListingQuery query) {
        Listing.Info info = listing.info();
        if (query.validAsOf() != null && !isValid(info, query.scope(), query.purgeDelaySeconds(), query.validAsOf())) {
            return false;
        }
        if (query.listingType() != null && listing.type() != query.listingType()) {
            return false;
        }
        if (query.category() != null && !query.category().equals(info.category())) {
            return false;
        }
        if (query.economy() != null && !query.economy().equals(info.economy())) {
            return false;
        }
        String search = query.search();
        return search == null || search.isBlank() || info.searchName().contains(search);
    }

    // Sorted, cached-per-scope views

    private final class SortedView {
        private final ListingScope scope;
        private final ListingSort sort;
        private volatile Snapshot snapshot = Snapshot.EMPTY;

        SortedView(ListingScope scope, ListingSort sort) {
            this.scope = scope;
            this.sort = sort;
        }

        List<Listing> get() {
            long currentVersion = versions.get(scope).get();
            Snapshot current = snapshot;
            if (current.version() == currentVersion) {
                return current.listings();
            }
            synchronized (this) {
                current = snapshot;
                if (current.version() == currentVersion) {
                    return current.listings();
                }
                List<Listing> rebuilt = new ArrayList<>(idsByScope.get(scope).size());
                for (String id : idsByScope.get(scope)) {
                    Listing listing = byId.get(id);
                    if (listing != null) {
                        rebuilt.add(listing);
                    }
                }
                rebuilt.sort(comparator(sort, scope));
                Snapshot fresh = new Snapshot(currentVersion, List.copyOf(rebuilt));
                snapshot = fresh;
                return fresh.listings();
            }
        }
    }

    private record Snapshot(long version, List<Listing> listings) {
        static final Snapshot EMPTY = new Snapshot(-1, List.of());
    }

    private static Comparator<Listing> comparator(ListingSort sort, ListingScope scope) {
        Comparator<Listing> primary = switch (sort) {
            case NEWEST, OLDEST -> Comparator.comparing(listing -> chronoKey(listing, scope));
            case PRICE_HIGH, PRICE_LOW -> Comparator.comparingDouble(ListingCache::sortPrice);
            case ALPHABETICAL -> Comparator.comparing(listing -> listing.info().searchName());
            case AMOUNT -> Comparator.comparingInt(listing -> listing.info().amount());
        };
        Comparator<Listing> withTieBreak = primary.thenComparing(listing -> listing.info().id());
        return sort.ascending() ? withTieBreak : withTieBreak.reversed();
    }

    private static Instant chronoKey(Listing listing, ListingScope scope) {
        Listing.Info info = listing.info();
        if (scope == ListingScope.ACTIVE) {
            return info.createdAt();
        }
        return info.endedAt() != null ? info.endedAt() : info.createdAt();
    }

    private static double sortPrice(Listing listing) {
        return switch (listing) {
            case Listing.Auction auction -> auction.price();
            case Listing.Bid bid -> bid.currentPrice();
        };
    }

    // Record reconstruction

    private static Listing withStatus(Listing listing, ListingStatus status, Instant endedAt) {
        Listing.Info info = listing.info();
        Listing.Info updated = new Listing.Info(info.id(), info.seller(), info.item(), info.amount(), info.economy(),
                info.fee(), info.tax(), info.taxRate(), info.category(), info.searchName(), info.createdAt(),
                info.expiresAt(), endedAt, status);
        return rebuild(listing, updated);
    }

    private static Listing withMetadata(Listing listing, ListingMetadataResolver.Metadata metadata) {
        Listing.Info info = listing.info();
        Listing.Info updated = new Listing.Info(info.id(), info.seller(), info.item(), info.amount(), info.economy(),
                info.fee(), info.tax(), info.taxRate(), metadata.category(), metadata.searchName(), info.createdAt(),
                info.expiresAt(), info.endedAt(), info.status());
        return rebuild(listing, updated);
    }

    private static Listing.Info withExpiry(Listing.Info info, Instant expiresAt) {
        if (info.expiresAt().equals(expiresAt)) {
            return info;
        }
        return new Listing.Info(info.id(), info.seller(), info.item(), info.amount(), info.economy(), info.fee(),
                info.tax(), info.taxRate(), info.category(), info.searchName(), info.createdAt(), expiresAt,
                info.endedAt(), info.status());
    }

    private static Listing rebuild(Listing original, Listing.Info newInfo) {
        return switch (original) {
            case Listing.Auction auction -> new Listing.Auction(newInfo, auction.price(), auction.remainingAmount());
            case Listing.Bid bid -> new Listing.Bid(newInfo, bid.startingPrice(), bid.currentPrice(),
                    bid.currentBidder(), bid.totalBidders(), bid.remindersShown());
        };
    }
}