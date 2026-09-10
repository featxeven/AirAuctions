package com.ftxeven.airauctions.gui.render;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.FilterConfig;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.impl.ListingDraft;
import com.ftxeven.airauctions.model.*;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.AuctionService;
import com.ftxeven.airauctions.service.listing.workflow.BidService;
import com.ftxeven.airauctions.util.MiniText;
import com.ftxeven.airauctions.util.Placeholders;
import com.ftxeven.airauctions.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class ListingPlaceholders {

    private final ConfigManager configs;
    private final ServiceManager services;

    public ListingPlaceholders(ConfigManager configs, ServiceManager services) {
        this.configs = configs;
        this.services = services;
    }

    // Batch player-name resolution

    public void prewarm(List<?> items) {
        if (items.isEmpty()) {
            return;
        }
        Set<UUID> uuids = new HashSet<>();
        for (Object item : items) {
            switch (item) {
                case Listing listing -> {
                    uuids.add(listing.info().seller());
                    if (listing instanceof Listing.Bid bid && bid.currentBidder() != null) {
                        uuids.add(bid.currentBidder());
                    }
                }
                case HistoryEntry entry -> {
                    uuids.add(entry.info().seller());
                    uuids.add(entry.info().buyer());
                }
                case BidEntry entry -> uuids.add(entry.bidder());
                default -> { /* nothing to prewarm for this item type */ }
            }
        }
        services.players().findAll(uuids);
    }

    // Active / expired / storage

    public Map<String, String> forListing(Listing listing) {
        return switch (listing) {
            case Listing.Auction auction -> forAuction(auction);
            case Listing.Bid bid -> forBid(bid);
        };
    }

    private Map<String, String> forAuction(Listing.Auction auction) {
        Listing.Info info = auction.info();
        Map<String, String> map = new HashMap<>();
        putCommon(map, info.id(), info.seller(), auction.remainingAmount(), info.category(), info.economy(), info.createdAt(), info.item());
        services.economy().formatInto(map, "price", info.economy(), services.auctions().remainingValue(auction));
        map.put("per_price", perPrice(info.economy(), auction.price(), info.amount()));

        if (info.status() == ListingStatus.ACTIVE) {
            map.put("expires", TimeFormatter.durationOrNever(info.expiresAt(), configs.main().formatting(), configs.lang()));
        } else {
            map.put("purges", purgesText(info));
        }
        return map;
    }

    private Map<String, String> forBid(Listing.Bid bid) {
        Listing.Info info = bid.info();
        Map<String, String> map = new HashMap<>();
        putCommon(map, info.id(), info.seller(), info.amount(), info.category(), info.economy(), info.createdAt(), info.item());
        services.economy().formatInto(map, "price", info.economy(), bid.startingPrice());

        switch (info.status()) {
            case ACTIVE -> {
                map.put("duration", TimeFormatter.duration(info.expiresAt(), configs.main().formatting(), configs.lang()));
                putOffer(map, bid);
            }
            case EXPIRED, CANCELLED -> {
                map.put("duration", elapsedText(info));
                map.put("purges", purgesText(info));
            }
            case ENDED, UNCOLLECTED -> {
                map.put("duration", elapsedText(info));
                map.put("purges", purgesText(info));
                putOffer(map, bid);
            }
        }
        return map;
    }

    private void putOffer(Map<String, String> map, Listing.Bid bid) {
        map.put("total_bidders", String.valueOf(bid.totalBidders()));

        UUID bidder = bid.currentBidder();
        map.put("highest_bidder", bidder != null
                ? services.players().name(bidder)
                : configs.lang().get("placeholders.empty.bidder").getFirst());

        OptionalDouble offer = bidder != null ? OptionalDouble.of(bid.currentPrice()) : OptionalDouble.empty();
        services.economy().formatInto(map, "highest_offer", bid.info().economy(), offer, "placeholders.empty.offer");
    }

    /** the viewer's own current offer on this listing, for browsing/view_bid.yml's offer button */
    public void putYourOffer(Map<String, String> map, Listing.Bid bid, UUID viewer) {
        services.economy().formatInto(map, "your_offer", bid.info().economy(),
                services.bids().findOffer(bid, viewer), "placeholders.empty.offer");
    }

    // Buy amount

    public Map<String, String> forBuyAmount(Player viewer, Listing.Auction auction, EconomyProvider provider, int buyAmount) {
        Map<String, String> map = new HashMap<>(forListing(auction));

        AuctionService.Quote quote = services.auctions().quote(auction, buyAmount, provider);
        double minPartial = services.economy().minPartialPrice();
        boolean partial = buyAmount < auction.remainingAmount();

        map.put("buy_amount", String.valueOf(buyAmount));
        map.put("max_amount", String.valueOf(auction.remainingAmount()));
        map.put("valid_price", String.valueOf(!partial || quote.price() >= minPartial));
        map.put("can_afford_price", String.valueOf(provider.has(viewer, quote.price())));
        services.economy().formatInto(map, "buy_price", provider.id(), quote.price());
        services.economy().formatInto(map, "min_price", provider.id(), minPartial);

        return map;
    }

    // Place bid

    public Map<String, String> forPlaceBid(Player viewer, Listing.Bid bid, EconomyProvider provider, double offer) {
        Map<String, String> map = new HashMap<>(forListing(bid));

        BidService.NextOffer bounds = services.bids().nextOfferBounds(bid);
        map.put("valid_min_offer", String.valueOf(offer >= bounds.min()));
        map.put("valid_max_offer", String.valueOf(bounds.max() < 0 || offer <= bounds.max()));
        map.put("can_afford_offer", String.valueOf(provider.has(viewer, offer)));

        services.economy().formatInto(map, "offer", provider.id(), offer);
        services.economy().formatInto(map, "your_offer", provider.id(), offer);
        services.economy().formatInto(map, "min_offer", provider.id(), bounds.min());
        OptionalDouble maxOffer = bounds.max() < 0 ? OptionalDouble.empty() : OptionalDouble.of(bounds.max());
        services.economy().formatInto(map, "max_offer", provider.id(), maxOffer, "placeholders.unlimited");

        return map;
    }

    // Draft preview

    public Map<String, String> forDraft(Player seller, ListingDraft draft, EconomyProvider provider) {
        Map<String, String> map = new HashMap<>();
        var metadata = services.listings().resolveMetadata(draft.itemSnapshot());

        map.put("amount", String.valueOf(draft.amount()));
        map.put("category", categoryDisplayName(metadata.category()));
        map.put("category_id", metadata.category());
        map.put("item_lore", itemLore(draft.itemSnapshot()));

        services.economy().formatInto(map, "price", provider.id(), draft.price());
        services.economy().formatEconomy(map, provider);

        if (draft.type() == ListingType.AUCTION) {
            map.put("expires", services.auctions().previewExpires(seller));
        } else {
            map.put("duration", TimeFormatter.duration(Duration.ofSeconds(draft.bidDurationSeconds()), configs.main().formatting(), configs.lang()));
        }
        return map;
    }

    // History

    public Map<String, String> forHistory(HistoryEntry entry) {
        return switch (entry) {
            case HistoryEntry.Auction auction -> forHistoryAuction(auction);
            case HistoryEntry.Bid bid -> forHistoryBid(bid);
        };
    }

    private Map<String, String> forHistoryAuction(HistoryEntry.Auction auction) {
        HistoryEntry.Info info = auction.info();
        Map<String, String> map = new HashMap<>();
        putCommon(map, info.id(), info.seller(), info.amount(), info.category(), info.economy(), info.createdAt(), info.item());
        putTransaction(map, info);
        map.put("buyer", services.players().name(info.buyer()));
        services.economy().formatInto(map, "price", info.economy(), auction.price());
        return map;
    }

    private Map<String, String> forHistoryBid(HistoryEntry.Bid bid) {
        HistoryEntry.Info info = bid.info();
        Map<String, String> map = new HashMap<>();
        putCommon(map, info.id(), info.seller(), info.amount(), info.category(), info.economy(), info.createdAt(), info.item());
        putTransaction(map, info);

        String winner = services.players().name(info.buyer());
        map.put("buyer", winner);
        map.put("highest_bidder", winner);
        map.put("total_bidders", String.valueOf(bid.totalBidders()));
        map.put("duration", TimeFormatter.duration(Duration.between(info.createdAt(), info.completedAt()), configs.main().formatting(), configs.lang()));
        services.economy().formatInto(map, "price", info.economy(), bid.startingPrice());
        services.economy().formatInto(map, "highest_offer", info.economy(), bid.finalPrice());
        return map;
    }

    private void putTransaction(Map<String, String> map, HistoryEntry.Info info) {
        map.put("transaction_date", TimeFormatter.date(info.completedAt(), configs.main().formatting()));
        map.put("transaction_time", TimeFormatter.time(info.completedAt(), configs.main().formatting()));
        map.put("completed_at", String.valueOf(info.completedAt().toEpochMilli()));
    }

    // Bid entries (browsing/view_bid)

    public Map<String, String> forBidEntry(String economyId, BidEntry entry, int position) {
        Map<String, String> map = new HashMap<>();
        map.put("bidder", services.players().name(entry.bidder()));
        map.put("position", String.valueOf(position));
        map.put("total_offers", String.valueOf(entry.totalOffers()));
        services.economy().formatInto(map, "offer", economyId, entry.offer());
        return map;
    }

    private void putCommon(Map<String, String> map, String id, UUID seller, int amount, String category,
                           String economyId, Instant createdAt, ItemStack item) {
        map.put("id", id);
        map.put("seller", services.players().name(seller));
        map.put("amount", String.valueOf(amount));
        map.put("category", categoryDisplayName(category));
        map.put("category_id", category);
        services.economy().formatEconomy(map, economyId);
        map.put("date", TimeFormatter.date(createdAt, configs.main().formatting()));
        map.put("time", TimeFormatter.time(createdAt, configs.main().formatting()));
        map.put("item_lore", itemLore(item));
    }

    private String categoryDisplayName(String categoryId) {
        FilterConfig.Category category = configs.filter().categories().get(categoryId);
        return category != null ? category.displayName() : categoryId;
    }

    private String itemLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return "";
        }
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Component line : lore) {
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(MiniText.mini().serialize(line));
        }
        return joined.toString();
    }

    private String purgesText(Listing.Info info) {
        return TimeFormatter.purgesIn(info, configs.main(), configs.lang());
    }

    private String elapsedText(Listing.Info info) {
        Instant endedAt = info.endedAt() != null ? info.endedAt() : info.createdAt();
        return TimeFormatter.duration(Duration.between(info.createdAt(), endedAt), configs.main().formatting(), configs.lang());
    }

    private String perPrice(String economyId, double totalPrice, int amount) {
        if (amount <= 1) {
            return "";
        }

        double perUnit = totalPrice / amount;
        boolean fractional = perUnit < 1 && !services.economy().get(economyId).map(EconomyProvider::allowDecimals).orElse(true);

        Map<String, String> price = new HashMap<>();
        services.economy().formatInto(price, "price", economyId, fractional ? 1 : perUnit);

        String key = fractional ? "placeholders.price.per-unit-fractional" : "placeholders.price.per-unit";
        return Placeholders.apply(null, configs.lang().get(key).getFirst(), price);
    }
}