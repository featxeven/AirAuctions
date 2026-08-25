package com.ftxeven.airauctions.service.listing.workflow;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.util.ItemDelivery;
import com.ftxeven.airauctions.util.ItemDisplay;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;

import java.util.*;

// item-handoff a player triggers for themselves: claiming an expired/canceled listing back
// from the seller side, and collecting a won bid out of storage from the bidder side
public final class ReclaimService {

    private final ConfigManager configs;
    private final ListingService listings;
    private final Messenger messenger;

    public ReclaimService(ConfigManager configs, ListingService listings, Messenger messenger) {
        this.configs = configs;
        this.listings = listings;
        this.messenger = messenger;
    }

    // Eligibility

    public Eligibility eligibleToReclaim(ReclaimKind kind, Player player, Listing listing) {
        if (!ownsAndActive(kind, player, listing)) {
            return Eligibility.denied("errors.item.unavailable");
        }
        boolean dropOnFull = configs.main().listings().dropOnFullInventory();
        if (ItemDelivery.rejects(player, listing.info().item(), listing.deliverableAmount(), dropOnFull)) {
            return Eligibility.denied(kind.key("errors.inventory-full"));
        }
        return Eligibility.eligible();
    }

    private boolean ownsAndActive(ReclaimKind kind, Player player, Listing listing) {
        return owns(kind, player, listing) && kind.scope().statuses().contains(listing.info().status());
    }

    // Single

    public ActionResult<ReclaimSuccess> reclaim(ReclaimKind kind, Player player, Listing listing) {
        Eligibility eligibility = eligibleToReclaim(kind, player, listing);
        if (eligibility instanceof Eligibility.Denied denied) {
            return ActionResult.denied(denied);
        }

        Listing.Info info = listing.info();
        int amount = listing.deliverableAmount();
        ItemDelivery.Result delivery = listings.deliver(player, listing);
        if (delivery == ItemDelivery.Result.REJECTED) {
            // eligibleToReclaim already confirmed this fits
            return ActionResult.denied(kind.key("errors.inventory-full"));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        ItemDisplay.formatInto(placeholders, "item", info.item(), configs.lang());
        messenger.send(player, configs.lang().get(kind.key("success")), placeholders);
        if (delivery == ItemDelivery.Result.DROPPED) {
            messenger.send(player, configs.lang().get(kind.key("dropped")));
        }

        return ActionResult.success(new ReclaimSuccess(amount, delivery));
    }

    // Bulk

    public ReclaimAvailability availability(ReclaimKind kind, Player player) {
        return availability(kind, player, ListingQuery.owned(player.getUniqueId(), kind.scope()));
    }

    public ReclaimAvailability availability(ReclaimKind kind, Player player, ListingQuery query) {
        boolean anyValid = false;
        for (Listing listing : listings.findAll(query)) {
            if (!owns(kind, player, listing) || !listings.belongsToScope(listing, kind.scope())) {
                continue; // not this player's, or already past its purge window
            }
            anyValid = true;
            if (eligibleToReclaim(kind, player, listing).ok()) {
                return new ReclaimAvailability(true, true); // both facets proven, stop scanning
            }
        }
        return new ReclaimAvailability(anyValid, false);
    }

    public ReclaimAllResult reclaimAll(ReclaimKind kind, Player player) {
        return reclaimAll(kind, player, ListingQuery.owned(player.getUniqueId(), kind.scope()));
    }

    public ReclaimAllResult reclaimAll(ReclaimKind kind, Player player, ListingQuery query) {
        List<Listing> owned = listings.findAll(query).stream()
                .filter(listing -> owns(kind, player, listing))
                .toList();
        if (owned.isEmpty()) {
            messenger.send(player, configs.lang().get(kind.nothingKey()));
            return new ReclaimAllResult(0, 0, false);
        }

        int reclaimed = 0;
        boolean anyDropped = false;

        for (Listing listing : owned) {
            if (!eligibleToReclaim(kind, player, listing).ok()) {
                continue;
            }
            ItemDelivery.Result delivery = listings.deliver(player, listing);
            if (delivery == ItemDelivery.Result.REJECTED) {
                continue; // inventory filled up mid-batch - leave it for next time
            }
            if (delivery == ItemDelivery.Result.DROPPED) {
                anyDropped = true;
            }
            reclaimed++;
        }

        if (reclaimed > 0) {
            messenger.send(player, configs.lang().get(kind.key("success-all")), Map.of("total", String.valueOf(reclaimed)));
            if (anyDropped) {
                messenger.send(player, configs.lang().get(kind.key("dropped-all")));
            }
        } else {
            messenger.send(player, configs.lang().get(kind.key("errors.inventory-full")));
        }

        return new ReclaimAllResult(owned.size(), reclaimed, anyDropped);
    }

    private boolean owns(ReclaimKind kind, Player player, Listing listing) {
        return switch (kind) {
            case CLAIM -> listing.info().seller().equals(player.getUniqueId());
            case COLLECT -> listing instanceof Listing.Bid bid && player.getUniqueId().equals(bid.currentBidder());
        };
    }

    // Kind

    public enum ReclaimKind {

        CLAIM(ListingScope.EXPIRED, "listings.claim", "errors.nothing-to-claim", "confirm/claim", "confirm/claim_all"),
        COLLECT(ListingScope.STORAGE, "bids.collect", "errors.nothing-to-collect", "confirm/collect", "confirm/collect_all");

        private final ListingScope scope;
        private final String langPrefix;
        private final String nothingSuffix;
        private final String confirmGuiId;
        private final String confirmAllGuiId;

        ReclaimKind(ListingScope scope, String langPrefix, String nothingSuffix, String confirmGuiId, String confirmAllGuiId) {
            this.scope = scope;
            this.langPrefix = langPrefix;
            this.nothingSuffix = nothingSuffix;
            this.confirmGuiId = confirmGuiId;
            this.confirmAllGuiId = confirmAllGuiId;
        }

        public ListingScope scope() { return scope; }

        public String key(String suffix) { return langPrefix + "." + suffix; }

        public String nothingKey() { return key(nothingSuffix); }

        public String flagName() { return name().toLowerCase(Locale.ROOT); }

        public String confirmGuiId() { return confirmGuiId; }

        public String confirmAllGuiId() { return confirmAllGuiId; }
    }

    // Result

    public record ReclaimSuccess(int amount, ItemDelivery.Result delivery) {}

    // available = how many listings matched before this call; reclaimed can be lower when
    // drop-on-full-inventory is off and some items didn't fit
    public record ReclaimAllResult(int available, int reclaimed, boolean anyDropped) {}

    public record ReclaimAvailability(boolean anyValid, boolean anyReclaimable) {

        public Eligibility toEligibility(ReclaimKind kind) {
            if (!anyValid) {
                return Eligibility.denied(kind.nothingKey());
            }
            if (!anyReclaimable) {
                return Eligibility.denied(kind.key("errors.inventory-full"));
            }
            return Eligibility.eligible();
        }
    }
}