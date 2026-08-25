package com.ftxeven.airauctions.service.listing.workflow;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.ExpansionsConfig;
import com.ftxeven.airauctions.database.repository.ListingMetadataResolver;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
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
import com.ftxeven.airauctions.util.ItemDelivery;
import com.ftxeven.airauctions.util.ItemDisplay;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.TimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class AuctionService {

    private final ConfigManager configs;
    private final HistoryService history;
    private final EconomyService economy;
    private final PlayerService players;
    private final ListingService listings;
    private final Messenger messenger;
    private final DiscordService discord;

    public AuctionService(ConfigManager configs, HistoryService history, EconomyService economy, PlayerService players,
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

    public String previewExpires(Player seller) {
        return formatExpiry(resolveExpiry(seller, Instant.now()));
    }

    public Optional<ListingService.Prepared> prepare(Player seller, ItemStack item, int amount, double price, EconomyProvider provider) {
        EconomyService.ChargeResult fee = economy.fee(seller, provider, price);
        if (!economy.charge(seller, provider, fee)) {
            return Optional.empty();
        }

        EconomyService.ChargeResult tax = economy.tax(seller, provider, price);
        Instant now = Instant.now();
        Optional<Instant> expiresAt = resolveExpiry(seller, now);
        ListingMetadataResolver.Metadata itemMetadata = listings.resolveMetadata(item);

        Listing.Info info = new Listing.Info(
                "", seller.getUniqueId(), item, amount, provider.id(), fee.amount(), tax.amount(), -1,
                itemMetadata.category(), itemMetadata.searchName(), now,
                expiresAt.orElse(Listing.NEVER_EXPIRES), null, ListingStatus.ACTIVE);

        Listing.Auction auction = new Listing.Auction(info, price, amount);

        Map<String, String> placeholders = listings.creationPlaceholders(seller, item, amount, provider.id(), price, fee);
        placeholders.put("expires", formatExpiry(expiresAt));

        return Optional.of(new ListingService.Prepared(auction, Map.copyOf(placeholders)));
    }

    public void announceCreated(Player seller, ListingService.Prepared prepared) {
        messenger.send(seller, configs.lang().get("auctions.sell.success"), prepared.placeholders());
        UUID excluded = configs.main().auctions().flow().announceToSeller() ? null : seller.getUniqueId();
        messenger.broadcast(configs.lang().get("auctions.sell.broadcast"), prepared.placeholders(), excluded);
        discord.auctionCreated((Listing.Auction) prepared.listing(), prepared.placeholders());
    }

    private Optional<Instant> resolveExpiry(Permissible seller, Instant now) {
        double seconds = PermissionTiers.resolveUnlimitedTier(seller, Permissions.Bypass.EXPIRE_TIME, configs.main().auctions().lifetime());
        return seconds < 0 ? Optional.empty() : Optional.of(now.plusSeconds((long) seconds));
    }

    private String formatExpiry(Optional<Instant> expiresAt) {
        return TimeFormatter.durationOrNever(expiresAt.orElse(null), configs.main().formatting(), configs.lang());
    }

    // Eligibility

    public Eligibility eligibleToPurchase(Player buyer, Listing.Auction auction, int amount) {
        Eligibility gate = preflight(buyer, auction);
        if (gate instanceof Eligibility.Denied) {
            return gate;
        }
        if (amount <= 0 || amount > auction.remainingAmount()) {
            return Eligibility.denied("errors.economy.invalid-amount");
        }

        Listing.Info info = auction.info();
        EconomyProvider provider = economy.get(info.economy()).orElseThrow();

        Quote quote = quote(auction, amount, provider);
        boolean partial = amount < auction.remainingAmount();
        double minPartial = economy.minPartialPrice();
        if (partial && quote.price() < minPartial) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "min", info.economy(), minPartial);
            return Eligibility.denied("auctions.purchase.errors.below-min-price", placeholders);
        }
        if (!provider.has(buyer, quote.price())) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "amount", info.economy(), economy.missing(buyer, provider, quote.price()));
            return Eligibility.denied("auctions.purchase.errors.insufficient-funds", placeholders);
        }

        if (ItemDelivery.rejects(buyer, info.item(), amount, configs.main().listings().dropOnFullInventory())) {
            return Eligibility.denied("auctions.purchase.errors.inventory-full");
        }

        return Eligibility.eligible();
    }

    public Eligibility eligibleToPurchase(Player buyer, Listing.Auction auction) {
        return eligibleToPurchase(buyer, auction, auction.remainingAmount());
    }

    public Eligibility eligibleToOpenBuyAmount(Player buyer, Listing.Auction auction) {
        if (configs.expansions().economy().buyCheck() == ExpansionsConfig.BuyCheck.FULL) {
            return eligibleToPurchase(buyer, auction);
        }

        Eligibility gate = preflight(buyer, auction);
        if (gate instanceof Eligibility.Denied) {
            return gate;
        }

        Listing.Info info = auction.info();
        EconomyProvider provider = economy.get(info.economy()).orElseThrow();

        double required = minimumPurchasePrice(auction, provider);
        if (!provider.has(buyer, required)) {
            Map<String, String> placeholders = new HashMap<>();
            economy.formatInto(placeholders, "amount", info.economy(), economy.missing(buyer, provider, required));
            return Eligibility.denied("auctions.purchase.errors.insufficient-funds", placeholders);
        }

        if (ItemDelivery.rejects(buyer, info.item(), 1, configs.main().listings().dropOnFullInventory())) {
            return Eligibility.denied("auctions.purchase.errors.inventory-full");
        }

        return Eligibility.eligible();
    }

    private Eligibility preflight(Player buyer, Listing.Auction auction) {
        Listing.Info info = auction.info();
        if (info.status() != ListingStatus.ACTIVE) {
            return Eligibility.denied("errors.item.unavailable");
        }
        if (!configs.main().auctions().allowSelfPurchase() && info.seller().equals(buyer.getUniqueId())) {
            return Eligibility.denied("auctions.purchase.errors.self-purchase");
        }
        if (economy.get(info.economy()).isEmpty()) {
            return Eligibility.denied("errors.item.unavailable");
        }
        return Eligibility.eligible();
    }

    private double minimumPurchasePrice(Listing.Auction auction, EconomyProvider provider) {
        double remaining = remainingValue(auction);
        double onePrice = quote(auction, 1, provider).price();
        return Math.min(remaining, Math.max(onePrice, economy.minPartialPrice()));
    }

    public boolean usesAmountSelection(Listing.Auction auction) {
        return auction.remainingAmount() >= configs.expansions().economy().buyAmountTrigger();
    }

    // Purchase

    public ActionResult<PurchaseSuccess> purchase(Player buyer, Listing.Auction auction, int amount) {
        Listing.Info info = auction.info();

        Eligibility eligibility = eligibleToPurchase(buyer, auction, amount);
        if (eligibility instanceof Eligibility.Denied denied) {
            return ActionResult.denied(denied);
        }

        boolean dropOnFull = configs.main().listings().dropOnFullInventory();
        if (ItemDelivery.rejects(buyer, info.item(), amount, dropOnFull)) {
            return ActionResult.denied("auctions.purchase.errors.inventory-full");
        }

        EconomyProvider provider = economy.get(info.economy()).orElseThrow();
        Quote quote = quote(auction, amount, provider);

        if (!provider.withdraw(buyer, quote.price())) {
            return ActionResult.denied("auctions.purchase.errors.withdraw-failed");
        }

        OptionalInt remainingAfter = listings.reduceStock(info.id(), amount);
        if (remainingAfter.isEmpty()) {
            // lost the race to another buyer between the eligibility check above and here
            players.payout(buyer.getUniqueId(), info.economy(), quote.price());
            return ActionResult.denied("errors.item.unavailable");
        }

        int remaining = remainingAfter.getAsInt();
        if (remaining <= 0) {
            listings.transition(info.id(), ListingStatus.ENDED);
        }

        ItemDelivery.Result delivery = ItemDelivery.give(buyer, info.item(), amount, dropOnFull);
        if (delivery == ItemDelivery.Result.REJECTED) {
            players.payout(buyer.getUniqueId(), info.economy(), quote.price());
            return ActionResult.denied("auctions.purchase.errors.inventory-full");
        }

        history.record(new HistoryEntry.Auction(
                new HistoryEntry.Info(info.id(), info.seller(), buyer.getUniqueId(), info.item(), amount, info.economy(),
                        0, quote.tax(), info.category(), info.searchName(), info.createdAt(), Instant.now()),
                quote.price()));

        players.payout(info.seller(), info.economy(), quote.payout());

        announcePurchased(info, buyer, amount, remaining, quote, delivery);

        return ActionResult.success(new PurchaseSuccess(amount, remaining, quote.price(), quote.tax(), quote.payout()));
    }

    public Quote quote(Listing.Auction auction, int amount, EconomyProvider provider) {
        Listing.Info info = auction.info();
        int originalAmount = info.amount();
        int soldBefore = originalAmount - auction.remainingAmount();
        int soldAfter = soldBefore + amount;

        double price = slice(auction.price(), soldBefore, soldAfter, originalAmount, provider);
        double tax = slice(info.tax(), soldBefore, soldAfter, originalAmount, provider);

        return new Quote(price, tax, price - tax);
    }

    public double remainingValue(Listing.Auction auction) {
        Optional<EconomyProvider> provider = economy.get(auction.info().economy());
        if (provider.isPresent()) {
            return quote(auction, auction.remainingAmount(), provider.get()).price();
        }
        int originalAmount = auction.info().amount();
        return originalAmount <= 0 ? auction.price() : auction.price() * auction.remainingAmount() / originalAmount;
    }

    private double slice(double total, int soldBefore, int soldAfter, int originalAmount, EconomyProvider provider) {
        return cumulative(total, soldAfter, originalAmount, provider) - cumulative(total, soldBefore, originalAmount, provider);
    }

    private double cumulative(double total, int sold, int originalAmount, EconomyProvider provider) {
        if (sold >= originalAmount) {
            return total;
        }
        double share = total * sold / originalAmount;
        return provider.allowDecimals() ? share : Math.round(share);
    }

    // Messaging

    private void announcePurchased(Listing.Info info, Player buyer, int amount, int remainingAfter, Quote quote, ItemDelivery.Result delivery) {
        Map<String, String> buyerPlaceholders = new HashMap<>();
        buyerPlaceholders.put("amount", String.valueOf(amount));
        ItemDisplay.formatInto(buyerPlaceholders, "item", info.item(), configs.lang());
        buyerPlaceholders.put("seller", players.name(info.seller()));
        economy.formatInto(buyerPlaceholders, "price", info.economy(), quote.price());
        messenger.send(buyer, configs.lang().get("auctions.purchase.success"), buyerPlaceholders);
        if (delivery == ItemDelivery.Result.DROPPED) {
            messenger.send(buyer, configs.lang().get("auctions.purchase.dropped"));
        }

        Map<String, String> sellerPlaceholders = new HashMap<>();
        sellerPlaceholders.put("amount", String.valueOf(amount));
        ItemDisplay.formatInto(sellerPlaceholders, "item", info.item(), configs.lang());
        sellerPlaceholders.put("buyer", buyer.getName());
        economy.formatInto(sellerPlaceholders, "payout", info.economy(), quote.payout());
        economy.formatInto(sellerPlaceholders, "tax", info.economy(), quote.tax(), EconomyService.ChargeKind.TAX);

        if (remainingAfter <= 0) {
            messenger.send(info.seller(), configs.lang().get("auctions.sell.sold"), sellerPlaceholders);
        } else {
            sellerPlaceholders.put("remaining_amount", String.valueOf(remainingAfter));
            messenger.send(info.seller(), configs.lang().get("auctions.sell.partial-sold"), sellerPlaceholders);
        }

        discord.auctionSold(info, buyerPlaceholders, sellerPlaceholders, remainingAfter);
    }

    // Expiry

    public void expire(Listing.Auction auction) {
        listings.transition(auction.info().id(), ListingStatus.EXPIRED);
        discord.auctionExpired(auction);
    }

    // Deletion (admin)

    public void delete(Listing.Auction auction, CommandSender admin) {
        listings.delete(auction);
        listings.notifySellerOfDeletion(auction.info(), admin);
        listings.notifyAdminOfDeletion(auction.info(), admin);
        discord.auctionDeleted(auction, admin);
    }

    // Result

    public record Quote(double price, double tax, double payout) {}

    public record PurchaseSuccess(int amount, int remainingAfter, double price, double tax, double payout) {}
}