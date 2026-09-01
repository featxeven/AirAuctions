package com.ftxeven.airauctions.service.discord;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.ExpansionsConfig;
import com.ftxeven.airauctions.core.command.CommandDispatch;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.player.PlayerService;
import com.ftxeven.airauctions.util.ItemDisplay;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.TimeFormatter;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class DiscordService {

    private final ConfigManager configs;
    private final EconomyService economy;
    private final PlayerService players;
    private final EmbedRenderer renderer;
    private final WebhookDispatcher dispatcher;
    private final Logger logger;

    public DiscordService(ConfigManager configs, EconomyService economy, PlayerService players, Messenger messenger, Logger logger) {
        this.configs = configs;
        this.economy = economy;
        this.players = players;
        this.renderer = new EmbedRenderer(messenger);
        this.dispatcher = new WebhookDispatcher(logger);
        this.logger = logger;
    }

    // Auctions

    public void auctionCreated(Listing.Auction auction, Map<String, String> creationPlaceholders) {
        Listing.Info info = auction.info();
        ExpansionsConfig.Discord discord = configs.expansions().discord();
        ExpansionsConfig.DiscordEvent event = discord.events().auctionNew();
        ExpansionsConfig.TimeFormat timeFormat = event.timeFormat() != null ? event.timeFormat() : discord.timeFormat();

        Map<String, String> placeholders = new HashMap<>(creationPlaceholders);
        placeholders.put("player", creationPlaceholders.get("seller"));
        placeholders.putAll(commonPlaceholders(info));
        placeholders.put("expires", TimeFormatter.discordTimestamp(info.expiresAt(), timeFormat, creationPlaceholders.get("expires")));
        fire(event, placeholders);
    }

    public void auctionSold(Listing.Info info, Map<String, String> buyerPlaceholders, Map<String, String> sellerPlaceholders, int remainingAfter) {
        Map<String, String> placeholders = new HashMap<>(sellerPlaceholders);
        placeholders.putAll(buyerPlaceholders);
        placeholders.put("economy", economy.displayName(info.economy()));
        placeholders.put("id", info.id());

        if (remainingAfter <= 0) {
            fire(events().auctionSold(), placeholders);
            return;
        }
        placeholders.put("remaining_amount", String.valueOf(remainingAfter));
        fire(events().auctionPartialSold(), placeholders);
    }

    public void auctionExpired(Listing.Auction auction) {
        Listing.Info info = auction.info();
        Map<String, String> placeholders = commonPlaceholders(info);
        placeholders.put("player", players.name(info.seller()));
        economy.formatInto(placeholders, "price", info.economy(), auction.price());
        fire(events().auctionExpired(), placeholders);
    }

    public void auctionDeleted(Listing.Auction auction, CommandSender admin) {
        deleted(auction.info(), auction.price(), admin);
    }

    // Bids

    public void bidCreated(Listing.Bid bid, Map<String, String> creationPlaceholders) {
        Listing.Info info = bid.info();
        ExpansionsConfig.Discord discord = configs.expansions().discord();
        ExpansionsConfig.DiscordEvent event = discord.events().bidNew();
        ExpansionsConfig.TimeFormat timeFormat = event.timeFormat() != null ? event.timeFormat() : discord.timeFormat();

        Map<String, String> placeholders = new HashMap<>(creationPlaceholders);
        placeholders.put("player", creationPlaceholders.get("seller"));
        placeholders.putAll(commonPlaceholders(info));
        placeholders.put("duration", TimeFormatter.discordTimestamp(info.expiresAt(), timeFormat, creationPlaceholders.get("duration")));
        fire(event, placeholders);
    }

    public void bidPlaced(Listing.Info info, Map<String, String> bidderPlaceholders, Map<String, String> sellerPlaceholders, int totalBidders) {
        Map<String, String> placeholders = new HashMap<>(bidderPlaceholders);
        placeholders.putAll(sellerPlaceholders);
        placeholders.put("price", bidderPlaceholders.get("offer"));
        placeholders.put("bid", bidderPlaceholders.get("offer"));
        placeholders.put("bid_count", String.valueOf(totalBidders));
        placeholders.put("economy", economy.displayName(info.economy()));
        placeholders.put("id", info.id());
        fire(events().bidPlaced(), placeholders);
    }

    public void bidEnded(Listing.Info info, Map<String, String> sellerPlaceholders, Map<String, String> winnerPlaceholders) {
        Map<String, String> placeholders = new HashMap<>(winnerPlaceholders);
        placeholders.putAll(sellerPlaceholders);
        placeholders.put("winner", sellerPlaceholders.get("bidder"));
        placeholders.put("price", winnerPlaceholders.get("offer"));
        placeholders.put("bid", winnerPlaceholders.get("offer"));
        placeholders.put("bid_count", winnerPlaceholders.get("total_bidders"));
        placeholders.put("economy", economy.displayName(info.economy()));
        placeholders.put("id", info.id());
        fire(events().bidEnded(), placeholders);
    }

    public void bidExpired(Listing.Bid bid) {
        Listing.Info info = bid.info();
        Map<String, String> placeholders = commonPlaceholders(info);
        placeholders.put("player", players.name(info.seller()));
        economy.formatInto(placeholders, "price", info.economy(), bid.startingPrice());
        fire(events().bidExpired(), placeholders);
    }

    public void bidDeleted(Listing.Bid bid, CommandSender admin) {
        deleted(bid.info(), bid.currentPrice(), admin);
    }

    // Shared

    private void deleted(Listing.Info info, double price, CommandSender admin) {
        Map<String, String> placeholders = commonPlaceholders(info);
        placeholders.put("player", players.name(info.seller()));
        placeholders.put("admin", CommandDispatch.senderName(admin, configs.lang().get("general.console-name").getFirst()));
        economy.formatInto(placeholders, "price", info.economy(), price);
        fire(events().listingDeleted(), placeholders);
    }

    private Map<String, String> commonPlaceholders(Listing.Info info) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", info.id());
        placeholders.put("item", ItemDisplay.name(info.item(), configs.lang()));
        placeholders.put("amount", String.valueOf(info.amount()));
        placeholders.put("economy", economy.displayName(info.economy()));
        return placeholders;
    }

    // Dispatch

    private ExpansionsConfig.Events events() {
        return configs.expansions().discord().events();
    }

    private void fire(ExpansionsConfig.DiscordEvent event, Map<String, String> placeholders) {
        ExpansionsConfig.Discord discord = configs.expansions().discord();
        if (!discord.enabled() || !event.enabled()) {
            return;
        }
        try {
            String webhookUrl = event.webhookUrl().isBlank() ? discord.webhookUrl() : event.webhookUrl();
            dispatcher.send(webhookUrl, renderer.payload(event, placeholders));
        } catch (Exception e) {
            logger.warning("Could not build Discord embed, skipping: " + e.getMessage());
        }
    }
}