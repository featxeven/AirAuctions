package com.ftxeven.airauctions.service.listing;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.database.DatabaseManager;
import com.ftxeven.airauctions.database.cache.CacheManager;
import com.ftxeven.airauctions.database.cache.ListingCache;
import com.ftxeven.airauctions.database.query.FacetCounts;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.database.repository.ListingMetadataResolver;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.model.BidEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.model.ListingStatus;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.player.PlayerService;
import com.ftxeven.airauctions.common.command.CommandDispatch;
import com.ftxeven.airauctions.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public final class ListingService {

    private final DatabaseManager database;
    private final CacheManager cache;
    private final ConfigManager configs;
    private final EconomyService economy;
    private final ListingMetadataService metadata;
    private final PlayerService players;
    private final Messenger messenger;
    private final Logger logger;

    public ListingService(DatabaseManager database, CacheManager cache, ConfigManager configs, EconomyService economy,
                          ListingMetadataService metadata, PlayerService players, Messenger messenger, Logger logger) {
        this.database = database;
        this.cache = cache;
        this.configs = configs;
        this.economy = economy;
        this.metadata = metadata;
        this.players = players;
        this.messenger = messenger;
        this.logger = logger;
    }

    // Lookup

    public Optional<Listing> find(String id) {
        return cache.listings().find(id);
    }

    public List<String> findRecentIds(int limit) {
        return cache.listings().recentIds(limit);
    }

    public List<Listing> findAll(ListingQuery query) {
        return cache.listings().findAll(bounded(query));
    }

    public PageResult<Listing> query(ListingQuery query) {
        return cache.listings().query(bounded(query));
    }

    public FacetCounts facets(ListingQuery query) {
        return cache.listings().facets(bounded(query));
    }

    public int count(UUID owner, ListingScope scope) {
        return cache.listings().count(bounded(ListingQuery.owned(owner, scope)));
    }

    // combined count across every non-history scope (active + expired + storage)
    public int totalCount(UUID owner) {
        int total = 0;
        for (ListingScope scope : ListingScope.values()) {
            total += count(owner, scope);
        }
        return total;
    }

    private ListingQuery bounded(ListingQuery query) {
        return query.validAsOf(Instant.now(), configs.main().purgeDelaySeconds(query.scope()));
    }

    public List<Listing.Info> dueToExpire() {
        return cache.listings().dueToExpire(Instant.now());
    }

    public boolean belongsToScope(Listing listing, ListingScope scope) {
        Listing.Info info = listing.info();
        if (!scope.statuses().contains(info.status())) {
            return false;
        }
        return ListingCache.isValid(info, scope, configs.main().purgeDelaySeconds(scope), Instant.now());
    }

    public Optional<ListingScope> resolveScope(Listing listing) {
        for (ListingScope scope : ListingScope.values()) {
            if (belongsToScope(listing, scope)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

    // Creation support

    @SuppressWarnings("unchecked")
    public <T extends Listing> T create(T listing) {
        T created = (T) database.listings().create(listing);
        cache.listings().index(created);
        return created;
    }

    public ListingMetadataResolver.Metadata resolveMetadata(ItemStack item) {
        return metadata.resolve(item);
    }

    public Map<String, String> creationPlaceholders(Player seller, ItemStack item, int amount, String economyId, double price, EconomyService.ChargeResult fee) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("seller", seller.getName());
        placeholders.put("amount", String.valueOf(amount));
        ItemDisplay.formatInto(placeholders, "item", item, configs.lang());
        economy.formatInto(placeholders, "price", economyId, price);
        economy.formatInto(placeholders, "fee", economyId, fee);
        return placeholders;
    }

    // Stock

    public OptionalInt reduceStock(String id, int amount) {
        OptionalInt remaining = cache.listings().tryReduceStock(id, amount);
        remaining.ifPresent(ignored -> persistAsync(
                () -> database.listings().reduceAuctionAmount(id, amount), "stock reduction for listing " + id));
        return remaining;
    }

    // Bidding

    public OptionalInt placeBid(String id, UUID bidder, double offer, Instant newExpiresAt) {
        OptionalInt totalBidders = cache.listings().tryPlaceBid(id, bidder, offer, newExpiresAt);
        totalBidders.ifPresent(ignored -> persistAsync(
                () -> database.listings().placeBid(id, bidder, offer, newExpiresAt), "bid on listing " + id));
        return totalBidders;
    }

    public PageResult<BidEntry> bidEntries(String listingId, int page, int pageSize) {
        return database.listings().bidEntries(listingId, page, pageSize);
    }

    public void markBidReminderShown(String id) {
        cache.listings().incrementBidReminders(id);
        persistAsync(() -> database.listings().incrementBidReminders(id), "reminder counter for listing " + id);
    }

    // Status transitions

    public Optional<Instant> transition(String id, ListingStatus status) {
        Optional<Instant> endedAt = cache.listings().tryTransition(id, status);
        endedAt.ifPresent(instant -> persistAsync(
                () -> database.listings().updateStatus(id, status, instant), "status update for listing " + id));
        return endedAt;
    }

    // Cancellation

    public boolean ownsActive(UUID seller, Listing.Info info) {
        return info.seller().equals(seller) && info.status() == ListingStatus.ACTIVE;
    }

    public Eligibility eligibleToCancel(Player seller, Listing listing) {
        if (!ownsActive(seller.getUniqueId(), listing.info())) {
            return Eligibility.denied("errors.item.unavailable");
        }
        if (listing instanceof Listing.Bid bid && bid.currentBidder() != null) {
            return Eligibility.denied("bids.cancel.errors.has-bids");
        }
        if (!configs.main().listings().instantCancel()) {
            return Eligibility.eligible();
        }
        boolean dropOnFull = configs.main().listings().dropOnFullInventory();
        if (ItemDelivery.rejects(seller, listing.info().item(), listing.deliverableAmount(), dropOnFull)) {
            return Eligibility.denied("listings.cancel.errors.inventory-full");
        }
        return Eligibility.eligible();
    }

    // shared by both listing types
    public ActionResult<CancelSuccess> cancel(Player seller, Listing listing) {
        Eligibility eligibility = eligibleToCancel(seller, listing);
        if (eligibility instanceof Eligibility.Denied denied) {
            return ActionResult.denied(denied);
        }

        Listing.Info info = listing.info();
        int amount = listing.deliverableAmount();

        if (!configs.main().listings().instantCancel()) {
            if (transition(info.id(), ListingStatus.CANCELLED).isEmpty()) {
                return ActionResult.denied("errors.item.unavailable");
            }
            messenger.send(seller, configs.lang().get("listings.cancel.success"), cancelPlaceholders(info, amount));
            return ActionResult.success(new CancelSuccess(amount, Optional.empty()));
        }

        ItemDelivery.Result delivery = deliver(seller, listing);
        if (delivery == ItemDelivery.Result.REJECTED) {
            // eligibleToCancel already confirmed this fits
            return ActionResult.denied("listings.cancel.errors.inventory-full");
        }
        messenger.send(seller, configs.lang().get("listings.cancel.instant.success"), cancelPlaceholders(info, amount));
        if (delivery == ItemDelivery.Result.DROPPED) {
            messenger.send(seller, configs.lang().get("listings.cancel.instant.dropped"));
        }
        return ActionResult.success(new CancelSuccess(amount, Optional.of(delivery)));
    }

    private Map<String, String> cancelPlaceholders(Listing.Info info, int amount) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        ItemDisplay.formatInto(placeholders, "item", info.item(), configs.lang());
        return placeholders;
    }

    // Deletion

    public void delete(Listing listing) {
        String id = listing.info().id();
        cache.listings().remove(id);
        persistAsync(() -> database.listings().delete(id), "deletion of listing " + id);
    }

    public ItemDelivery.Result deliver(Player player, Listing listing) {
        boolean dropOnFull = configs.main().listings().dropOnFullInventory();
        ItemDelivery.Result result = ItemDelivery.give(player, listing.info().item(), listing.deliverableAmount(), dropOnFull);
        if (result != ItemDelivery.Result.REJECTED) {
            delete(listing);
        }
        return result;
    }

    public void notifySellerOfDeletion(Listing.Info info, CommandSender admin) {
        if (!deletionFeedbackAllowed(admin, info.seller())) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(info.amount()));
        ItemDisplay.formatInto(placeholders, "item", info.item(), configs.lang());
        placeholders.put("admin", adminDisplayName(admin));
        messenger.send(info.seller(), configs.lang().get("listings.delete.notify-seller"), placeholders);
    }

    public void notifyAdminOfDeletion(Listing.Info info, CommandSender admin) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", info.id());
        placeholders.put("amount", String.valueOf(info.amount()));
        ItemDisplay.formatInto(placeholders, "item", info.item(), configs.lang());
        placeholders.put("seller", players.name(info.seller()));
        messenger.send(admin, configs.lang().get("listings.delete.success"), placeholders);
    }

    public boolean deletionFeedbackAllowed(CommandSender admin, UUID target) {
        return CommandDispatch.targetFeedbackAllowed(admin, target, configs.main().general().consoleFeedback());
    }

    public String adminDisplayName(CommandSender admin) {
        return CommandDispatch.senderName(admin, configs.lang().get("general.console-name").getFirst());
    }

    // Creation - finalize

    public void finalizeCreation(Player seller, ItemStack snapshot, int amount, int slot, EconomyProvider provider,
                                 EconomyService.ChargeResult fee, Map<String, String> placeholders,
                                 Function<ItemStack, Optional<Prepared>> prepare, Consumer<Prepared> onListed) {
        if (!ItemWithdrawal.has(seller.getInventory(), snapshot, amount)) {
            messenger.send(seller, configs.lang().get("errors.item.changed"), placeholders);
            return;
        }

        ItemStack listedItem = snapshot.clone();
        listedItem.setAmount(amount);

        Optional<Prepared> prepared = prepare.apply(listedItem);
        if (prepared.isEmpty()) {
            messenger.send(seller, configs.lang().get("errors.economy.insufficient-funds-fee"), placeholders);
            return;
        }

        commitAndWithdraw(seller, snapshot, amount, slot, provider, fee, prepared.get(),
                () -> messenger.send(seller, configs.lang().get("errors.item.changed"), placeholders),
                () -> onListed.accept(prepared.get()));
    }

    // persists the listing, then takes the item from the seller's inventory on their own thread
    private void commitAndWithdraw(Player seller, ItemStack snapshot, int amount, int slot, EconomyProvider provider,
                                   EconomyService.ChargeResult fee, Prepared prepared, Runnable onItemChanged, Runnable onListed) {
        Scheduler.runAsync(() -> {
            Listing created = create(prepared.listing());

            Scheduler.runEntity(seller, () -> {
                Inventory inventory = seller.getInventory();
                if (!ItemWithdrawal.has(inventory, snapshot, amount)) {
                    rollbackCreation(created, seller.getUniqueId(), provider, fee);
                    onItemChanged.run();
                    return;
                }
                ItemWithdrawal.take(inventory, snapshot, amount, slot);
                onListed.run();
            }, () -> rollbackCreation(created, seller.getUniqueId(), provider, fee));
        });
    }

    // undoes a listing that was already persisted (and its fee already charged) once it turns
    // out, back on the seller's own thread, that they no longer actually have the item
    private void rollbackCreation(Listing created, UUID seller, EconomyProvider provider, EconomyService.ChargeResult fee) {
        Scheduler.runAsync(() -> {
            delete(created);
            if (!fee.waived()) {
                players.payout(seller, provider.id(), fee.amount());
            }
        });
    }

    // Purging

    public void purgeDue(ListingScope scope, int purgeDelaySeconds) {
        if (purgeDelaySeconds < 0) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(purgeDelaySeconds);
        List<String> due = cache.listings().dueToPurge(scope, cutoff);
        for (String id : due) {
            cache.listings().remove(id);
        }
        if (!due.isEmpty()) {
            persistAsync(() -> due.forEach(id -> database.listings().delete(id)), "purge of " + due.size() + " " + scope + " listings");
        }
    }

    // Maintenance

    public int resyncMetadata(ListingMetadataResolver resolver) {
        int updated = cache.listings().resyncMetadata(resolver);
        if (updated > 0) {
            persistAsync(() -> database.listings().resyncMetadata(resolver), "metadata resync");
        }
        return updated;
    }

    public int countBySeller(Collection<UUID> sellers) {
        return cache.listings().countBySeller(sellers);
    }

    public int deleteBySeller(Collection<UUID> sellers) {
        int removed = cache.listings().removeBySeller(sellers);
        if (removed > 0) {
            persistAsync(() -> database.listings().deleteBySeller(sellers), "bulk deletion for " + sellers.size() + " sellers");
        }
        return removed;
    }

    private void persistAsync(Runnable task, String description) {
        Scheduler.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.warning("Could not persist " + description + " (in-memory state is unaffected): " + e.getMessage());
            }
        });
    }

    // Results

    public record Prepared(Listing listing, Map<String, String> placeholders) {}

    public record CancelSuccess(int amount, Optional<ItemDelivery.Result> delivery) {}
}