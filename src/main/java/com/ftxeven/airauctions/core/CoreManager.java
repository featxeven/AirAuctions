package com.ftxeven.airauctions.core;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.service.*;

public final class CoreManager {

    private final AuctionService auctionService;
    private final SearchService searchService;
    private final EconomyService economyService;
    private final PlayerProfileService playerProfileService;
    private final ItemBlacklistService itemBlacklistService;
    private final StatsService statsService;
    private final GuiManager guiManager;

    public CoreManager(AirAuctions plugin) {
        this.economyService = new EconomyService(plugin);
        this.playerProfileService = new PlayerProfileService(plugin);
        this.itemBlacklistService = new ItemBlacklistService(plugin);
        this.auctionService = new AuctionService(plugin);
        this.searchService = new SearchService(plugin);
        this.statsService = new StatsService(plugin);
        this.guiManager = new GuiManager(plugin);
    }

    public void reload() {
        guiManager.reload();
        auctionService.start();
    }

    public void startServices() {
        auctionService.start();
    }

    public GuiManager gui() { return guiManager; }
    public AuctionService auctions() { return auctionService; }
    public SearchService search() { return searchService; }
    public EconomyService economy() { return economyService; }
    public PlayerProfileService profiles() { return playerProfileService; }
    public ItemBlacklistService blacklist() { return itemBlacklistService; }
    public StatsService stats() { return statsService; }
}