package com.ftxeven.airauctions.service.listing.workflow;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.MainConfig;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.database.repository.ListingMetadataResolver;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.model.BidEntry;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.model.ListingStatus;
import com.ftxeven.airauctions.permission.PermissionTiers;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.discord.DiscordService;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.listing.HistoryService;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.service.player.PlayerService;
import com.ftxeven.airauctions.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

public final class BidService {

    private final ConfigManager configs;
    private final HistoryService history;
    private final EconomyService economy;
    private final PlayerService players;
    private final ListingService listings;
    private final Messenger messenger;
    private final DiscordService discord;

    public BidService(ConfigManager configs, HistoryService history, EconomyService economy, PlayerService players,
                      ListingService listings, Messenger messenger, DiscordService discord) {
        this.configs = configs;
        this.history = history;
        this.economy = economy;
        this.players = players;
        this.listings = listings;
        this.messenger = messenger;
        this.discord = discord;
    }

    // Creation

    public Optional<ListingService.Prepared> prepare(Player seller, ItemStack item, int amount, double startingPrice, int durationSeconds, EconomyProvider provider) {
        EconomyService.ChargeResult fee = economy.fee(seller, provider, startingPrice);
        if (!economy.charge(seller, provider, fee)) {
            return Optional.empty();
        }

        EconomyService.ChargeResult tax = economy.tax(seller, provider, startingPrice);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(durationSeconds);
        ListingMetadataResolver.Metadata itemMetadata = listings.resolveMetadata(item);

        Listing.Info info = new Listing.Info(
                "", seller.getUniqueId(), item, amount, provider.id(), fee.amount(), tax.amount(), tax.rate(),
                itemMetadata.category(), itemMetadata.searchName(), now, expiresAt, null, ListingStatus.ACTIVE);

        Listing.Bid bid = new Listing.Bid(info, startingPrice, startingPrice, null, 0, 0);

        Map<String, String> placeholders = listings.creationPlaceholders(seller, item, amount, provider.id(), startingPrice, fee);
        placeholders.put("duration", TimeFormatter.duration(Duration.ofSeconds(durationSeconds), configs.main().formatting(), configs.lang()));

        return Optional.of(new ListingService.Prepared(bid, Map.copyOf(placeholders)));
    }

    public void announceCreated(Player seller, ListingService.Prepared prepared) {
        notify(seller.getUniqueId(), "bids.create.success", prepared.placeholders());
        UUID excluded = configs.main().bids().flow().announceToSeller() ? null : seller.getUniqueId();
        messenger.broadcast(configs.lang().get("bids.create.broadcast"), prepared.placeholders(), excluded);
        discord.bidCreated((Listing.Bid) prepared.listing(), prepared.placeholders());
    }

    // Duration bounds

    public DurationBounds resolveDurationBounds(Player seller) {
        MainConfig.Bids bidsConfig = configs.main().bids();
        int max = (int) PermissionTiers.resolveUnlimitedTier(seller, Permissions.Bypass.DURATION_TIME, bidsConfig.maxDuration());
        return new DurationBounds(bidsConfig.minDuration(), max);
    }

    public com.ftxeven.airauctions.service.listing.ValidationResult validateDuration(Player seller, int durationSeconds) {
        DurationBounds bounds = resolveDurationBounds(seller);
        if (durationSeconds < bounds.min() || (bounds.max() >= 0 && durationSeconds > bounds.max())) {
            return new com.ftxeven.airauctions.service.listing.ValidationResult.InvalidDuration(bounds.min(), bounds.max());
        }
        return new com.ftxeven.airauctions.service.listing.ValidationResult.Ok();
    }

    // Eligibility

    public NextOffer nextOfferBounds(Listing.Bid bid) {
        MainConfig.Bids bidsConfig = configs.main().bids();
        double min = bid.currentPrice() + bidsConfig.minIncrement();
        double max = bidsConfig.maxIncrement() < 0 ? -1 : bid.currentPrice() + bidsConfig.maxIncrement();
        return new NextOffer(min, max);
    }

    // status/ownership/highest-bidder
    private Eligibility eligiblePrelim(Player bidder, Listing.Bid bid) {
        Listing.Info info = bid.info();
        if (info.status() != ListingStatus.ACTIVE) {
            return Eligibility.denied("errors.item.unavailable");
        }
        if (!configs.main().bids().allowSelfBidding() && info.seller().equals(bidder.getUniqueId())) {
            return Eligibility.denied("bids.place.errors.self-bid");
        }
        if (bidder.getUniqueId().equals(bid.currentBidder())) {
            return Eligibility.denied("bids.place.errors.already-highest");
        }
        return Eligibility.eligible();
    }

    public Eligibility eligibleToBid(Player bidder, Listing.Bid bid, double offer) {
        Eligibility prelim = eligiblePrelim(bidder, bid);
        if (!prelim.ok()) {
            return prelim;
        }

        Listing.Info info = bid.info();
        NextOffer bounds = nextOfferBounds(bid);
        if (offer < bounds.min()) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "min_offer", info.economy(), bounds.min());
            return Eligibility.denied("bids.place.errors.below-minimum", placeholders);
        }
        if (bounds.max() >= 0 && offer > bounds.max()) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "max_offer", info.economy(), bounds.max());
            return Eligibility.denied("bids.place.errors.above-maximum", placeholders);
        }

        Optional<EconomyProvider> providerLookup = economy.get(info.economy());
        if (providerLookup.isEmpty()) {
            return Eligibility.denied("errors.item.unavailable");
        }
        if (!providerLookup.get().has(bidder, offer)) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "offer", info.economy(), offer);
            economy.formatInto(placeholders, "amount", info.economy(), economy.missing(bidder, providerLookup.get(), offer));
            return Eligibility.denied("bids.place.errors.insufficient-funds", placeholders);
        }

        return eligibleStorageRoom(bidder);
    }

    public Eligibility eligibleToBid(Player bidder, Listing.Bid bid) {
        Eligibility prelim = eligiblePrelim(bidder, bid);
        if (!prelim.ok()) {
            return prelim;
        }

        Listing.Info info = bid.info();
        double minOffer = nextOfferBounds(bid).min();

        Optional<EconomyProvider> providerLookup = economy.get(info.economy());
        if (providerLookup.isEmpty()) {
            return Eligibility.denied("errors.item.unavailable");
        }
        if (!providerLookup.get().has(bidder, minOffer)) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "offer", info.economy(), minOffer);
            economy.formatInto(placeholders, "amount", info.economy(), economy.missing(bidder, providerLookup.get(), minOffer));
            return Eligibility.denied("bids.place.errors.insufficient-funds", placeholders);
        }

        return eligibleStorageRoom(bidder);
    }

    private Eligibility eligibleStorageRoom(Player bidder) {
        int maxUncollected = configs.main().bids().maxUncollected();
        if (maxUncollected < 0) {
            return Eligibility.eligible();
        }
        int uncollected = listings.count(bidder.getUniqueId(), ListingScope.STORAGE);
        if (uncollected < maxUncollected) {
            return Eligibility.eligible();
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(uncollected));
        placeholders.put("limit", String.valueOf(maxUncollected));
        return Eligibility.denied("bids.place.errors.storage-limit", placeholders);
    }

    public ActionResult<PlaceBidSuccess> placeBid(Player bidder, Listing.Bid bid, double offer) {
        Eligibility eligibility = eligibleToBid(bidder, bid, offer);
        if (eligibility instanceof Eligibility.Denied denied) {
            return ActionResult.denied(denied);
        }

        Listing.Info info = bid.info();
        EconomyProvider provider = economy.get(info.economy()).orElseThrow();

        if (!provider.withdraw(bidder, offer)) {
            return ActionResult.denied("auctions.purchase.errors.withdraw-failed");
        }

        OptionalInt totalBidders = listings.placeBid(info.id(), bidder.getUniqueId(), offer, resolveSnipeExpiry(info));
        if (totalBidders.isEmpty()) {
            // outraced between the eligibility check above and the atomic price-accept just now -
            // nothing was ever recorded, so just return the payment
            players.payout(bidder.getUniqueId(), info.economy(), offer);
            return ActionResult.denied("errors.item.unavailable");
        }

        UUID previousBidder = bid.currentBidder();
        if (previousBidder != null) {
            players.payout(previousBidder, info.economy(), bid.currentPrice());
        }

        announcePlaced(info, bidder, offer, previousBidder, totalBidders.getAsInt());
        return ActionResult.success(new PlaceBidSuccess(offer, totalBidders.getAsInt()));
    }

    private Instant resolveSnipeExpiry(Listing.Info info) {
        MainConfig.Bids bidsConfig = configs.main().bids();
        if (bidsConfig.snipeExtend() < 0) {
            return info.expiresAt();
        }
        Instant now = Instant.now();
        Instant snipeThreshold = info.expiresAt().minusSeconds(bidsConfig.snipeWindow());
        return now.isBefore(snipeThreshold) ? info.expiresAt() : now.plusSeconds(bidsConfig.snipeExtend());
    }

    // Bid entries

    public PageResult<BidEntry> bidEntries(String listingId, int page, int pageSize) {
        return listings.bidEntries(listingId, page, pageSize);
    }

    public OptionalDouble findOffer(Listing.Bid bid, UUID bidder) {
        int total = bid.totalBidders();
        if (total <= 0) {
            return OptionalDouble.empty();
        }
        return bidEntries(bid.info().id(), 1, total).items().stream()
                .filter(entry -> entry.bidder().equals(bidder))
                .mapToDouble(BidEntry::offer)
                .findFirst();
    }

    // Expiry

    public void expire(Listing.Bid bid) {
        if (bid.currentBidder() == null) {
            listings.transition(bid.info().id(), ListingStatus.EXPIRED);
            discord.bidExpired(bid);
        } else {
            settleWon(bid);
        }
    }

    private void settleWon(Listing.Bid bid) {
        Listing.Info info = bid.info();
        Optional<Instant> endedAt = listings.transition(info.id(), ListingStatus.UNCOLLECTED);
        if (endedAt.isEmpty()) {
            return; // another server already settled this bid
        }

        UUID winner = bid.currentBidder();
        double tax = economy.get(info.economy())
                .map(provider -> economy.finalizeTax(provider, info.taxRate(), info.tax(), bid.currentPrice()))
                .orElse(info.tax());
        double payout = bid.currentPrice() - tax;

        players.payout(info.seller(), info.economy(), payout);

        history.record(new HistoryEntry.Bid(
                new HistoryEntry.Info(info.id(), info.seller(), winner, info.item(), info.amount(), info.economy(),
                        info.fee(), tax, info.category(), info.searchName(), info.createdAt(), endedAt.get()),
                bid.startingPrice(), bid.currentPrice(), bid.totalBidders()));

        settleDelivery(info, bid, winner, payout, tax, endedAt.get());
    }

    private void settleDelivery(Listing.Info info, Listing.Bid bid, UUID winner, double payout, double tax, Instant endedAt) {
        Player player = Bukkit.getPlayer(winner);
        if (player == null) {
            announceWon(info, bid, winner, payout, tax, endedAt, Optional.empty());
            return;
        }
        Scheduler.runEntity(player,
                () -> announceWon(info, bid, winner, payout, tax, endedAt, tryInstantDeliver(player, bid)),
                () -> announceWon(info, bid, winner, payout, tax, endedAt, Optional.empty()));
    }

    private Optional<ItemDelivery.Result> tryInstantDeliver(Player player, Listing.Bid bid) {
        if (!configs.main().bids().instantCollect()) {
            return Optional.empty();
        }
        return Optional.of(listings.deliver(player, bid));
    }

    // Messaging

    private void announcePlaced(Listing.Info info, Player bidder, double offer, UUID previousBidder, int totalBidders) {
        String amount = String.valueOf(info.amount());
        String sellerName = players.name(info.seller());

        Map<String, String> bidderPlaceholders = new HashMap<>();
        bidderPlaceholders.put("amount", amount);
        ItemDisplay.formatInto(bidderPlaceholders, "item", info.item(), configs.lang());
        bidderPlaceholders.put("seller", sellerName);
        economy.formatInto(bidderPlaceholders, "offer", info.economy(), offer);
        notify(bidder.getUniqueId(), "bids.place.success", bidderPlaceholders);

        if (previousBidder != null) {
            Map<String, String> outbidPlaceholders = new HashMap<>();
            outbidPlaceholders.put("amount", amount);
            ItemDisplay.formatInto(outbidPlaceholders, "item", info.item(), configs.lang());
            outbidPlaceholders.put("bidder", bidder.getName());
            economy.formatInto(outbidPlaceholders, "highest_offer", info.economy(), offer);
            notify(previousBidder, "bids.place.outbid-notify", outbidPlaceholders);
        }

        Map<String, String> sellerPlaceholders = new HashMap<>();
        sellerPlaceholders.put("amount", amount);
        ItemDisplay.formatInto(sellerPlaceholders, "item", info.item(), configs.lang());
        sellerPlaceholders.put("bidder", bidder.getName());
        economy.formatInto(sellerPlaceholders, "offer", info.economy(), offer);
        notify(info.seller(), "bids.place.seller-notify", sellerPlaceholders);

        discord.bidPlaced(info, bidderPlaceholders, sellerPlaceholders, totalBidders);
    }

    private void announceWon(Listing.Info info, Listing.Bid bid, UUID winner, double payout, double tax,
                             Instant endedAt, Optional<ItemDelivery.Result> delivery) {
        String amount = String.valueOf(info.amount());
        String sellerName = players.name(info.seller());
        String winnerName = players.name(winner);

        Map<String, String> sellerPlaceholders = new HashMap<>();
        sellerPlaceholders.put("amount", amount);
        ItemDisplay.formatInto(sellerPlaceholders, "item", info.item(), configs.lang());
        sellerPlaceholders.put("bidder", winnerName);
        economy.formatInto(sellerPlaceholders, "payout", info.economy(), payout);
        economy.formatInto(sellerPlaceholders, "tax", info.economy(), tax, EconomyService.ChargeKind.TAX);
        notify(info.seller(), "bids.create.sold", sellerPlaceholders);

        Map<String, String> winnerPlaceholders = new HashMap<>();
        winnerPlaceholders.put("amount", amount);
        ItemDisplay.formatInto(winnerPlaceholders, "item", info.item(), configs.lang());
        winnerPlaceholders.put("seller", sellerName);
        economy.formatInto(winnerPlaceholders, "offer", info.economy(), bid.currentPrice());
        winnerPlaceholders.put("total_bidders", String.valueOf(bid.totalBidders()));
        winnerPlaceholders.put("purges", purgesPlaceholder(endedAt));
        notify(winner, "bids.end.winner", winnerPlaceholders);
        if (delivery.isPresent() && delivery.get() == ItemDelivery.Result.DROPPED) {
            notify(winner, "bids.end.dropped", Map.of());
        }

        discord.bidEnded(info, sellerPlaceholders, winnerPlaceholders);

        int pageSize = Math.max(1, bid.totalBidders());
        for (BidEntry entry : bidEntries(info.id(), 1, pageSize).items()) {
            if (entry.bidder().equals(winner)) {
                continue;
            }
            Map<String, String> loserPlaceholders = new HashMap<>();
            loserPlaceholders.put("amount", amount);
            ItemDisplay.formatInto(loserPlaceholders, "item", info.item(), configs.lang());
            loserPlaceholders.put("seller", sellerName);
            loserPlaceholders.put("winner", winnerName);
            economy.formatInto(loserPlaceholders, "highest_offer", info.economy(), bid.currentPrice());
            economy.formatInto(loserPlaceholders, "offer", info.economy(), entry.offer());
            loserPlaceholders.put("total_bidders", String.valueOf(bid.totalBidders()));
            notify(entry.bidder(), "bids.end.loser", loserPlaceholders);
        }
    }

    public String purgesPlaceholder(Instant endedAt) {
        return TimeFormatter.purgesIn(endedAt, configs.main().bids().collectPurgeDelay(), configs.main().formatting(), configs.lang());
    }

    private void notify(UUID uuid, String langKey, Map<String, String> placeholders) {
        messenger.send(uuid, configs.lang().get(langKey), placeholders);
    }

    // Deletion (admin)

    public boolean delete(Listing.Bid bid) {
        Listing.Info info = bid.info();
        boolean refunded = info.status() == ListingStatus.ACTIVE && bid.currentBidder() != null;
        if (refunded) {
            players.refund(bid.currentBidder(), info.economy(), bid.currentPrice());
        }
        listings.delete(bid);
        return refunded;
    }

    public void delete(Listing.Bid bid, CommandSender admin) {
        boolean refunded = delete(bid);
        listings.notifySellerOfDeletion(bid.info(), admin);
        listings.notifyAdminOfDeletion(bid.info(), admin);
        if (refunded) {
            notifyBidderOfDeletion(bid, admin);
        }
        discord.bidDeleted(bid, admin);
    }

    private void notifyBidderOfDeletion(Listing.Bid bid, CommandSender admin) {
        Listing.Info info = bid.info();
        UUID bidder = bid.currentBidder();
        if (!listings.deletionFeedbackAllowed(admin, bidder)) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(info.amount()));
        ItemDisplay.formatInto(placeholders, "item", info.item(), configs.lang());
        placeholders.put("admin", listings.adminDisplayName(admin));
        economy.formatInto(placeholders, "refund", info.economy(), bid.currentPrice());
        messenger.send(bidder, configs.lang().get("bids.end.errors.deleted"), placeholders);
    }

    // Result

    public record NextOffer(double min, double max) {}

    public record DurationBounds(int min, int max) {} // max = -1 means unlimited

    public record PlaceBidSuccess(double offer, int totalBidders) {}
}