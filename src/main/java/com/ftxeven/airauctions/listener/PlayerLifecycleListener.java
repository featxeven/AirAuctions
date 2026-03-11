package com.ftxeven.airauctions.listener;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public final class PlayerLifecycleListener implements Listener {

    private final AirAuctions plugin;

    public PlayerLifecycleListener(AirAuctions plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.database().records().updateJoin(event.getPlayer());
        plugin.core().profiles().loadProfile(event.getPlayer());

        handleUpdateNotification(player);

        plugin.scheduler().runTask(() -> {
            for (AuctionListing listing : plugin.core().auctions().getCache()) {
                if (listing.sellerUuid().equals(event.getPlayer().getUniqueId()) && listing.isTimedOut()) {
                    plugin.core().auctions().getActive(null);
                    break;
                }
            }
        });
    }

    private void handleUpdateNotification(org.bukkit.entity.Player player) {
        if (!plugin.config().notifyUpdates()) return;

        String latest = plugin.getLatestVersion();
        if (latest != null && (player.hasPermission("airauctions.admin") || player.isOp())) {
            plugin.scheduler().runDelayed(() -> {
                if (player.isOnline()) {
                    MessageUtil.send(player, plugin.lang().get("general.plugin-outdated"),
                            Map.of(
                                    "current", plugin.getPluginMeta().getVersion(),
                                    "latest", latest
                            ));
                }
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        plugin.core().profiles().unloadProfile(uuid);
        plugin.core().auctions().clearPlayerData(uuid);
    }
}