package com.ftxeven.airauctions.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public sealed interface Listing {

    // sentinel for "no expiry"
    Instant NEVER_EXPIRES = Instant.parse("9999-12-31T23:59:59Z");

    Info info();

    default ListingType type() {
        return switch (this) {
            case Auction a -> ListingType.AUCTION;
            case Bid b -> ListingType.BID;
        };
    }

    default int deliverableAmount() {
        return switch (this) {
            case Auction auction -> auction.remainingAmount();
            case Bid bid -> bid.info().amount();
        };
    }

    default ItemStack displayItem() {
        ItemStack item = info().item().clone();
        item.setAmount(Math.max(1, deliverableAmount()));
        return item;
    }

    static Listing withGeneratedId(Listing listing, String id, Instant createdAt) {
        Info info = listing.info();
        Info stamped = new Info(id, info.seller(), info.item(), info.amount(), info.economy(),
                info.fee(), info.tax(), info.taxRate(), info.category(), info.searchName(), createdAt, info.expiresAt(),
                info.endedAt(), info.status());

        return switch (listing) {
            case Auction auction -> new Auction(stamped, auction.price(), auction.remainingAmount());
            case Bid bid -> new Bid(stamped, bid.startingPrice(), bid.currentPrice(), bid.currentBidder(),
                    bid.totalBidders(), bid.remindersShown());
        };
    }

    record Info(
            String id,
            UUID seller,
            ItemStack item,
            int amount,
            String economy,
            double fee,
            double tax,
            double taxRate, // resolved sales-tax % for BID listings; -1 = 'tax' is already final
            String category,
            String searchName,
            Instant createdAt,
            Instant expiresAt,
            @Nullable Instant endedAt,
            ListingStatus status
    ) {
        public Info {
            item = item.clone();
        }
    }

    record Auction(
            Info info,
            double price,
            int remainingAmount
    ) implements Listing {}

    record Bid(
            Info info,
            double startingPrice,
            double currentPrice,
            @Nullable UUID currentBidder,
            int totalBidders,
            int remindersShown
    ) implements Listing {

        public boolean hasLeader() {
            return currentBidder != null;
        }

        public boolean accepts(double offer) {
            return hasLeader() ? offer > currentPrice : offer >= currentPrice;
        }
    }
}