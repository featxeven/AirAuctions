package com.ftxeven.airauctions.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.service.confirmation.ConfirmationService;
import com.ftxeven.airauctions.service.discord.DiscordService;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.listing.ExpiryTask;
import com.ftxeven.airauctions.service.listing.HistoryService;
import com.ftxeven.airauctions.service.listing.ListingMetadataService;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.service.listing.ListingValidator;
import com.ftxeven.airauctions.service.listing.SearchService;
import com.ftxeven.airauctions.service.listing.match.ItemMatcher;
import com.ftxeven.airauctions.service.listing.workflow.AuctionService;
import com.ftxeven.airauctions.service.listing.workflow.BidService;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import com.ftxeven.airauctions.service.player.NotificationService;
import com.ftxeven.airauctions.service.player.PlayerService;
import com.ftxeven.airauctions.service.simulation.SimulationService;
import com.ftxeven.airauctions.util.Scheduler;

public final class ServiceManager {

    private final EconomyService economy;

    private final PlayerService players;
    private final NotificationService notifications;

    private final ItemMatcher matcher;
    private final ListingMetadataService metadata;
    private final ListingService listings;
    private final HistoryService history;
    private final ListingValidator validator;
    private final SearchService search;
    private final ExpiryTask expiry;

    private final DiscordService discord;

    private final AuctionService auctions;
    private final BidService bids;
    private final ReclaimService reclaims;

    private final ConfirmationService confirmations;

    private final SimulationService simulation;

    public ServiceManager(AirAuctions plugin) {
        economy = new EconomyService(plugin.economy(), plugin.configs());

        matcher = new ItemMatcher(plugin.hooks());
        players = new PlayerService(plugin.cache(), plugin.database(), economy);

        metadata = new ListingMetadataService(plugin.configs(), matcher);
        listings = new ListingService(plugin.database(), plugin.cache(), plugin.configs(), economy, metadata, players,
                plugin.messenger());
        history = new HistoryService(plugin.database(), plugin.cache(), plugin.configs());
        validator = new ListingValidator(plugin.configs(), players, matcher, listings);
        search = new SearchService(plugin.configs());

        discord = new DiscordService(plugin.configs(), economy, players, plugin.messenger(), plugin.getLogger());

        auctions = new AuctionService(plugin.configs(), history, economy, players, listings, plugin.messenger(), discord);
        bids = new BidService(plugin.configs(), history, economy, players, listings, plugin.messenger(), discord);
        reclaims = new ReclaimService(plugin.configs(), listings, plugin.messenger());

        notifications = new NotificationService(plugin.configs(), players, economy, listings, bids, plugin.messenger());
        confirmations = new ConfirmationService();
        expiry = new ExpiryTask(plugin.configs(), listings, auctions, bids, plugin.getLogger());
        simulation = new SimulationService(plugin.configs(), listings, history, economy, players, auctions, bids);
    }

    public void reload() {
        expiry.restart();
    }

    public void resyncMetadataAsync(Runnable onComplete) {
        Scheduler.runAsync(() -> {
            listings.resyncMetadata(metadata);
            history.resyncMetadata(metadata);
            onComplete.run();
        });
    }

    public EconomyService economy() { return economy; }

    public ItemMatcher matcher() { return matcher; }

    public NotificationService notifications() { return notifications; }

    public PlayerService players() { return players; }

    public ListingMetadataService metadata() { return metadata; }

    public ListingValidator validator() { return validator; }

    public SearchService search() { return search; }

    public ListingService listings() { return listings; }

    public HistoryService history() { return history; }

    public DiscordService discord() { return discord; }

    public AuctionService auctions() { return auctions; }

    public BidService bids() { return bids; }

    public ReclaimService reclaims() { return reclaims; }

    public ConfirmationService confirmations() { return confirmations; }

    public ExpiryTask expiry() { return expiry; }

    public SimulationService simulation() { return simulation; }
}