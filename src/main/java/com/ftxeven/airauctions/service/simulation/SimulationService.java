package com.ftxeven.airauctions.service.simulation;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingStatus;
import com.ftxeven.airauctions.model.ListingType;
import com.ftxeven.airauctions.permission.NullPermissible;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.listing.HistoryService;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.service.listing.workflow.AuctionService;
import com.ftxeven.airauctions.service.listing.workflow.BidService;
import com.ftxeven.airauctions.service.player.PlayerService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.*;

public final class SimulationService {

    private static final int MIN_POOL = 20;
    private static final int MAX_POOL = 300;
    private static final int HISTORY_WINDOW_SECONDS = 60 * 60 * 24 * 14; // 2 weeks
    private static final double FUNDING_MULTIPLIER = 25;
    private static final double FRESH_RATIO = 0.6;

    private final ConfigManager configs;
    private final ListingService listings;
    private final HistoryService history;
    private final EconomyService economy;
    private final PlayerService players;
    private final AuctionService auctions;
    private final BidService bids;
    private final PlayerPool playerPool;
    private final ItemPool itemPool;

    public SimulationService(ConfigManager configs, ListingService listings, HistoryService history,
                             EconomyService economy, PlayerService players, AuctionService auctions, BidService bids) {
        this.configs = configs;
        this.listings = listings;
        this.history = history;
        this.economy = economy;
        this.players = players;
        this.auctions = auctions;
        this.bids = bids;
        this.playerPool = new PlayerPool(players);
        this.itemPool = new ItemPool(configs);
    }

    // Generation

    public Result generate(GenerateOptions options, ProgressListener progress) {
        List<EconomyProvider> providers = usableProviders();
        if (providers.isEmpty()) {
            return new Result(0, 0, 0, 0, options.seed());
        }

        Random random = new Random(options.seed());
        int poolSize = options.playerPoolSize() > 0
                ? options.playerPoolSize()
                : Math.clamp(options.count() / 8, MIN_POOL, MAX_POOL);
        List<UUID> pool = playerPool.ensure(poolSize);

        fundPool(pool, providers);

        int auctionsCreated = 0, bidsCreated = 0, purchases = 0;
        int reportEvery = Math.max(1, options.count() / 100);

        for (int i = 0; i < options.count(); i++) {
            EconomyProvider provider = providers.get(random.nextInt(providers.size()));
            UUID seller = pool.get(random.nextInt(pool.size()));
            ItemStack item = itemPool.random(random);
            int minAmount = Math.max(1, configs.main().listings().minAmount());
            int amount = minAmount + random.nextInt(64);
            boolean fresh = random.nextDouble() < FRESH_RATIO;

            boolean asBid = switch (options.mix()) {
                case AUCTIONS -> false;
                case BIDS -> true;
                case MIXED -> random.nextBoolean();
            };

            if (asBid) {
                if (createBid(seller, item, amount, provider, fresh, pool, random)) {
                    bidsCreated++;
                }
            } else {
                OptionalInt sold = createAuction(seller, item, amount, provider, fresh, pool, random);
                if (sold.isPresent()) {
                    auctionsCreated++;
                    purchases += sold.getAsInt();
                }
            }

            if (progress != null && ((i + 1) % reportEvery == 0 || i + 1 == options.count())) {
                progress.onProgress(i + 1, options.count());
            }
        }

        return new Result(auctionsCreated, bidsCreated, purchases, pool.size(), options.seed());
    }

    private List<EconomyProvider> usableProviders() {
        List<EconomyProvider> usable = new ArrayList<>();
        for (String key : economy.economyKeys()) {
            economy.findByKey(key).filter(EconomyProvider::supportsOffline).ifPresent(usable::add);
        }
        return usable;
    }

    private void fundPool(List<UUID> pool, List<EconomyProvider> providers) {
        double funding = fundingAmount();
        for (UUID uuid : pool) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            for (EconomyProvider provider : providers) {
                if (!provider.has(offline, funding)) {
                    provider.deposit(offline, funding);
                }
            }
        }
    }

    private double fundingAmount() {
        double min = Math.max(1, economy.minPrice());
        double max = economy.maxPrice() < 0 ? min * 5000 : economy.maxPrice();
        return max * FUNDING_MULTIPLIER;
    }

    private Instant randomCreatedAt(boolean fresh, double durationSeconds, Random random) {
        Instant now = Instant.now();
        if (durationSeconds < 0) {
            return now.minusSeconds(random.nextInt(HISTORY_WINDOW_SECONDS));
        }
        long duration = Math.max(1, (long) durationSeconds);
        if (fresh) {
            long age = (long) (random.nextDouble() * duration * 0.9);
            return now.minusSeconds(age);
        }
        long window = Math.max(duration + 1, HISTORY_WINDOW_SECONDS);
        long age = duration + 1 + (long) (random.nextDouble() * (window - duration - 1));
        return now.minusSeconds(age);
    }

    private Optional<ChargedInfo> prepareListing(UUID seller, ItemStack item, int amount, EconomyProvider provider,
                                                 Instant createdAt, Instant expiresAt, ListingType type, Random random) {
        double price = randomPrice(random, provider);

        EconomyService.ChargeResult fee = economy.fee(NullPermissible.INSTANCE, provider, price);
        if (!withdraw(seller, provider, fee.amount())) {
            return Optional.empty();
        }
        EconomyService.ChargeResult tax = economy.tax(NullPermissible.INSTANCE, provider, price);
        double taxRate = type == ListingType.BID ? tax.rate() : -1;

        var metadata = listings.resolveMetadata(item);
        Listing.Info info = new Listing.Info("", seller, item, amount, provider.id(), fee.amount(), tax.amount(), taxRate,
                metadata.category(), metadata.searchName(), createdAt, expiresAt, null, ListingStatus.ACTIVE);
        return Optional.of(new ChargedInfo(info, price));
    }

    private OptionalInt createAuction(UUID seller, ItemStack item, int amount, EconomyProvider provider,
                                      boolean fresh, List<UUID> pool, Random random) {
        double lifetime = configs.main().auctions().lifetime();
        Instant createdAt = randomCreatedAt(fresh, lifetime, random);
        Instant expiresAt = lifetime < 0 ? Listing.NEVER_EXPIRES : createdAt.plusSeconds((long) lifetime);

        Optional<ChargedInfo> charged = prepareListing(seller, item, amount, provider, createdAt, expiresAt, ListingType.AUCTION, random);
        if (charged.isEmpty()) {
            return OptionalInt.empty();
        }

        Listing.Auction auction = listings.create(new Listing.Auction(charged.get().info(), charged.get().price(), amount));

        if (expiresAt.isAfter(Instant.now()) && random.nextInt(3) == 0) {
            return OptionalInt.of(simulatePurchase(auction, pool.get(random.nextInt(pool.size())), random));
        }
        if (random.nextInt(20) == 0) {
            listings.transition(auction.info().id(), ListingStatus.CANCELLED);
        }
        return OptionalInt.of(0);
    }

    private int simulatePurchase(Listing.Auction auction, UUID buyer, Random random) {
        Listing.Info info = auction.info();
        if (buyer.equals(info.seller())) {
            return 0;
        }
        Optional<EconomyProvider> provider = economy.get(info.economy());
        if (provider.isEmpty()) {
            return 0;
        }

        int amount = 1 + random.nextInt(auction.remainingAmount());
        AuctionService.Quote quote = auctions.quote(auction, amount, provider.get());
        if (!withdraw(buyer, provider.get(), quote.price())) {
            return 0;
        }

        OptionalInt remainingAfter = listings.reduceStock(info.id(), amount);
        if (remainingAfter.isEmpty()) {
            players.payout(buyer, info.economy(), quote.price());
            return 0;
        }
        if (remainingAfter.getAsInt() <= 0) {
            listings.transition(info.id(), ListingStatus.ENDED);
        }

        history.record(new HistoryEntry.Auction(
                new HistoryEntry.Info(info.id(), info.seller(), buyer, info.item(), amount, info.economy(),
                        0, quote.tax(), info.category(), info.searchName(), info.createdAt(), Instant.now()),
                quote.price()));
        players.payout(info.seller(), info.economy(), quote.payout());
        return amount;
    }

    private boolean createBid(UUID seller, ItemStack item, int amount, EconomyProvider provider,
                              boolean fresh, List<UUID> pool, Random random) {
        int min = configs.main().bids().minDuration();
        int max = configs.main().bids().maxDuration();
        int durationSeconds = min + random.nextInt(Math.max(1, max - min));
        Instant createdAt = randomCreatedAt(fresh, durationSeconds, random);
        Instant expiresAt = createdAt.plusSeconds(durationSeconds);

        Optional<ChargedInfo> charged = prepareListing(seller, item, amount, provider, createdAt, expiresAt, ListingType.BID, random);
        if (charged.isEmpty()) {
            return false;
        }

        double startingPrice = charged.get().price();
        Listing.Bid created = listings.create(new Listing.Bid(charged.get().info(), startingPrice, startingPrice, null, 0, 0));
        String id = created.info().id();

        int rounds = random.nextInt(6);
        for (int i = 0; i < rounds; i++) {
            Listing.Bid live = liveBid(id);
            if (live == null || live.info().status() != ListingStatus.ACTIVE) {
                break;
            }

            UUID bidder = pool.get(random.nextInt(pool.size()));
            if (bidder.equals(seller) || bidder.equals(live.currentBidder())) {
                continue;
            }

            double offer = bids.nextOfferBounds(live).min();
            if (!withdraw(bidder, provider, offer)) {
                continue;
            }

            OptionalInt totalBidders = listings.placeBid(live, bidder, offer, expiresAt);
            if (totalBidders.isEmpty()) {
                players.payout(bidder, live.info().economy(), offer);
                continue;
            }
            if (live.currentBidder() != null) {
                players.payout(live.currentBidder(), live.info().economy(), live.currentPrice());
            }
        }
        return true;
    }

    private Listing.Bid liveBid(String id) {
        return listings.find(id)
                .filter(Listing.Bid.class::isInstance)
                .map(Listing.Bid.class::cast)
                .orElse(null);
    }

    private double randomPrice(Random random, EconomyProvider provider) {
        double min = Math.max(1, economy.minPrice());
        double max = economy.maxPrice() < 0 ? min * 5000 : economy.maxPrice();
        double price = min + random.nextDouble() * (max - min);
        return provider.allowDecimals() ? Math.round(price * 100.0) / 100.0 : Math.round(price);
    }

    private boolean withdraw(UUID uuid, EconomyProvider provider, double amount) {
        if (amount <= 0) {
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return provider.has(offline, amount) && provider.withdraw(offline, amount);
    }

    // Clearing

    public int clear() {
        List<UUID> synthetic = players.findByNamePrefix(PlayerPool.NAME_PREFIX);
        if (synthetic.isEmpty()) {
            return 0;
        }

        int removedListings = listings.deleteBySeller(synthetic);
        history.deleteBySeller(synthetic);

        return removedListings;
    }

    // Status

    public Status status() {
        List<UUID> synthetic = players.findByNamePrefix(PlayerPool.NAME_PREFIX);
        int trackedListings = synthetic.isEmpty() ? 0 : listings.countBySeller(synthetic);
        return new Status(trackedListings, synthetic.size(), usableProviders().size());
    }

    // Types

    public enum Mix { AUCTIONS, BIDS, MIXED }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int completed, int total);
    }

    private record ChargedInfo(Listing.Info info, double price) {}

    public record GenerateOptions(int count, Mix mix, long seed, int playerPoolSize) {}

    public record Result(int auctionsCreated, int bidsCreated, int purchasesSimulated, int playersUsed, long seed) {}

    public record Status(int trackedListings, int syntheticPlayers, int usableProviders) {}
}