package com.ftxeven.airauctions.gui.render;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.impl.BuyAmountGui;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.model.ListingType;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.listing.ListingValidator;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

public final class ListingFlags {

    private final ConfigManager configs;
    private final ServiceManager services;
    private final GuiManager guis;

    public ListingFlags(ConfigManager configs, ServiceManager services, GuiManager guis) {
        this.configs = configs;
        this.services = services;
        this.guis = guis;
    }

    // Entry points

    public Function<String, String> forListing(Player viewer, @Nullable ListingScope scope, @Nullable Listing listing) {
        Map<String, String> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key, k -> String.valueOf(resolveListing(viewer, scope, listing, k)));
    }

    public Function<String, String> forHistory(Player viewer, @Nullable HistoryEntry entry) {
        Map<String, String> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key, k -> String.valueOf(resolveHistory(viewer, entry, k)));
    }

    public Function<String, String> forDraft(Player seller, CreationDraft draft) {
        Map<String, String> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key, k -> String.valueOf(resolveDraft(seller, draft, k)));
    }

    public Function<String, String> forBulkReclaim(Player viewer, ReclaimService.ReclaimKind kind, ListingQuery query) {
        ReclaimService.ReclaimAvailability availability = services.reclaims().availability(kind, viewer, query);
        return key -> String.valueOf(resolveBulk(viewer, kind, availability, key));
    }

    public Function<String, String> forBuyAmount(Player viewer, @Nullable Listing.Auction auction, IntSupplier amount) {
        Map<String, String> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key, k -> String.valueOf(resolveBuyAmount(viewer, auction, amount, k)));
    }

    public Function<String, String> forPlaceBid(Player viewer, @Nullable Listing.Bid bid, DoubleSupplier offer) {
        Map<String, String> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key, k -> String.valueOf(resolvePlaceBid(viewer, bid, offer, k)));
    }

    // Dispatch

    private boolean resolveListing(Player viewer, @Nullable ListingScope scope, @Nullable Listing listing, String key) {
        Flag flag = Flag.parse(key);
        if (flag == null) {
            return false;
        }
        return switch (flag.namespace()) {
            case "has" -> viewer.hasPermission(flag.value());
            case "is" -> resolveIs(viewer, scope, listing, flag.value());
            case "can" -> listing != null && resolveCan(viewer, scope, listing, flag.value());
            default -> false;
        };
    }

    private boolean resolveDraft(Player seller, CreationDraft draft, String key) {
        Flag flag = Flag.parse(key);
        if (flag == null) {
            return false;
        }
        return switch (flag.namespace()) {
            case "has" -> seller.hasPermission(flag.value());
            case "can" -> flag.value().equals("list") && canList(seller, draft);
            default -> false; // is:* has nothing to evaluate before a listing is persisted
        };
    }

    private boolean resolveHistory(Player viewer, @Nullable HistoryEntry entry, String key) {
        Flag flag = Flag.parse(key);
        if (flag == null) {
            return false;
        }
        return switch (flag.namespace()) {
            case "has" -> viewer.hasPermission(flag.value());
            case "is" -> entry != null && resolveTypeAndShulker(entry.info().item(), entry.type(), flag.value());
            default -> false;
        };
    }

    private boolean resolveBulk(Player viewer, ReclaimService.ReclaimKind kind,
                                ReclaimService.ReclaimAvailability availability, String key) {
        Flag flag = Flag.parse(key);
        if (flag == null) return false;
        return switch (flag.namespace()) {
            case "has" -> viewer.hasPermission(flag.value());
            case "is" -> flag.value().equals("valid") && availability.anyValid();
            case "can" -> flag.value().equals(kind.flagName()) && availability.anyReclaimable();
            default -> false;
        };
    }

    private boolean resolveBuyAmount(Player viewer, @Nullable Listing.Auction auction, IntSupplier amount, String key) {
        Flag flag = Flag.parse(key);
        if (flag == null) {
            return false;
        }
        return switch (flag.namespace()) {
            case "has" -> viewer.hasPermission(flag.value());
            case "is" -> resolveIs(viewer, ListingScope.ACTIVE, auction, flag.value());
            case "can" -> auction != null && flag.value().equals("buy") && services.auctions().eligibleToPurchase(viewer, auction, amount.getAsInt()).ok();
            default -> false;
        };
    }

    private boolean resolvePlaceBid(Player viewer, @Nullable Listing.Bid bid, DoubleSupplier offer, String key) {
        Flag flag = Flag.parse(key);
        if (flag == null) {
            return false;
        }
        return switch (flag.namespace()) {
            case "has" -> viewer.hasPermission(flag.value());
            case "is" -> resolveIs(viewer, ListingScope.ACTIVE, bid, flag.value());
            case "can" -> bid != null && flag.value().equals("bid") && services.bids().eligibleToBid(viewer, bid, offer.getAsDouble()).ok();
            default -> false;
        };
    }

    private boolean resolveIs(Player viewer, @Nullable ListingScope scope, @Nullable Listing listing, String value) {
        return switch (value) {
            case "valid" -> isValid(scope, listing);
            case "owned" -> listing != null && isOwned(viewer, scope, listing);
            case "shulker", "bid", "auction" -> listing != null && resolveTypeAndShulker(listing.info().item(), listing.type(), value);
            default -> false;
        };
    }

    // shared by live listings and history rows
    private boolean resolveTypeAndShulker(ItemStack item, ListingType type, String value) {
        return switch (value) {
            case "shulker" -> Tag.SHULKER_BOXES.isTagged(item.getType());
            case "bid" -> type == ListingType.BID;
            case "auction" -> type == ListingType.AUCTION;
            default -> false;
        };
    }

    private boolean resolveCan(Player viewer, ListingScope scope, Listing listing, String value) {
        return switch (value) {
            case "buy" -> listing instanceof Listing.Auction auction && eligibleToBuy(viewer, auction).ok();
            case "bid" -> listing instanceof Listing.Bid bid && services.bids().eligibleToBid(viewer, bid).ok();
            case "cancel" -> services.listings().eligibleToCancel(viewer, listing).ok();
            case "claim" -> scope == ListingScope.EXPIRED && services.reclaims().eligibleToReclaim(ReclaimService.ReclaimKind.CLAIM, viewer, listing).ok();
            case "collect" -> scope == ListingScope.STORAGE && services.reclaims().eligibleToReclaim(ReclaimService.ReclaimKind.COLLECT, viewer, listing).ok();
            default -> false;
        };
    }

    private Eligibility eligibleToBuy(Player viewer, Listing.Auction auction) {
        boolean amountFlow = buyAmountGuiEnabled() && services.auctions().usesAmountSelection(auction);
        return amountFlow
                ? services.auctions().eligibleToOpenBuyAmount(viewer, auction)
                : services.auctions().eligibleToPurchase(viewer, auction);
    }

    private boolean buyAmountGuiEnabled() {
        return guis.definition(BuyAmountGui.ID).map(gui -> gui.settings().enabled()).orElse(false);
    }

    // is:valid / is:owned

    private boolean isValid(@Nullable ListingScope scope, @Nullable Listing listing) {
        return listing != null && scope != null && services.listings().belongsToScope(listing, scope);
    }

    private boolean isOwned(Player viewer, ListingScope scope, Listing listing) {
        return switch (scope.ownerRole()) {
            case SELLER -> listing.info().seller().equals(viewer.getUniqueId());
            case CURRENT_BIDDER -> listing instanceof Listing.Bid bid && viewer.getUniqueId().equals(bid.currentBidder());
        };
    }

    // can:list

    private boolean canList(Player seller, CreationDraft draft) {
        ListingValidator validator = services.validator();
        EconomyService economy = services.economy();

        if (!validator.validate(seller, draft.item(), draft.amount()).ok()) {
            return false;
        }

        UUID uuid = seller.getUniqueId();
        if (!validator.validateListingLimit(uuid, validator.maxActiveListings(uuid)).ok()) {
            return false;
        }
        if (!validator.validateExpiredLimit(uuid, configs.main().listings().maxExpired()).ok()) {
            return false;
        }
        if (draft.bidDurationSeconds() != null && !services.bids().validateDuration(seller, draft.bidDurationSeconds()).ok()) {
            return false;
        }
        if (!economy.eligibleForPrice(draft.price(), draft.provider()).ok()) {
            return false;
        }

        EconomyService.ChargeResult fee = economy.fee(seller, draft.provider(), draft.price());
        return economy.eligibleForFee(seller, draft.provider(), fee).ok();
    }

    // Types

    private record Flag(String namespace, String value) {
        static @Nullable Flag parse(String key) {
            int separator = key.indexOf(':');
            return separator < 0 ? null : new Flag(key.substring(0, separator), key.substring(separator + 1));
        }
    }

    /** a not-yet-persisted listing - what confirm/auction.yml and confirm/bid.yml are
     * previewing before the seller confirms. 'bidDurationSeconds' is null for an auction draft */
    public record CreationDraft(ItemStack item, int amount, double price, EconomyProvider provider, @Nullable Integer bidDurationSeconds) {}
}