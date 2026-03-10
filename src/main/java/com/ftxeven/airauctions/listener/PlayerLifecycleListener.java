package com.ftxeven.airauctions.listener;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionListing;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerLifecycleListener implements Listener {

    private final AirAuctions plugin;

    public PlayerLifecycleListener(AirAuctions plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.database().records().updateJoin(event.getPlayer());
        plugin.core().profiles().loadProfile(event.getPlayer());

        plugin.scheduler().runTask(() -> {
            for (AuctionListing listing : plugin.core().auctions().getCache()) {
                if (listing.sellerUuid().equals(event.getPlayer().getUniqueId()) && listing.isTimedOut()) {
                    plugin.core().auctions().getActive(null);
                    break;
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        plugin.core().profiles().unloadProfile(uuid);
        plugin.core().auctions().clearPlayerData(uuid);
    }
}