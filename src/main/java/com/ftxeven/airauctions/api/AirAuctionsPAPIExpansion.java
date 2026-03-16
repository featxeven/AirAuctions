package com.ftxeven.airauctions.api;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.PageableHolder;
import com.ftxeven.airauctions.core.manager.player.ExpiredListingsManager;
import com.ftxeven.airauctions.core.manager.player.ListingHistoryManager;
import com.ftxeven.airauctions.core.manager.player.PlayerListingsManager;
import com.ftxeven.airauctions.core.manager.target.TargetHistoryManager;
import com.ftxeven.airauctions.core.manager.target.TargetListingsManager;
import com.ftxeven.airauctions.core.manager.main.AuctionHouseManager;
import com.ftxeven.airauctions.core.manager.main.AuctionSearchManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AirAuctionsPAPIExpansion extends PlaceholderExpansion {

    private final AirAuctions plugin;

    public AirAuctionsPAPIExpansion(AirAuctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() { return "ftxeven"; }

    @Override
    public @NotNull String getIdentifier() { return "airauctions"; }

    @Override
    public @NotNull String getVersion() { return "1.0.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String pLow = params.toLowerCase();

        if (pLow.equals("total_auctions")) {
            return String.valueOf(plugin.core().auctions().getTotalActiveCount());
        }

        if (offlinePlayer == null) return "0";

        UUID uuid = offlinePlayer.getUniqueId();

        if (pLow.equals("player_auctions")) {
            return String.valueOf(plugin.core().auctions().getListingCount(uuid));
        }

        if (pLow.equals("player_expired")) {
            return String.valueOf(plugin.core().auctions().getExpiredCount(uuid));
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return "0";
        }

        if (pLow.equals("player_limit")) {
            int limit = plugin.config().getPlayerLimit(player);
            return limit == Integer.MAX_VALUE ? "unlimited" : String.valueOf(limit);
        }
        if (pLow.startsWith("player_sold_") || pLow.startsWith("player_spent_")) {
            String[] parts = pLow.split("_");
            if (parts.length < 4) return "0";

            boolean isSold = parts[1].equals("sold");
            String cycle = parts[2].toUpperCase();
            String type = parts[3].toUpperCase();

            double value = plugin.core().stats().getStat(offlinePlayer.getUniqueId(), cycle, isSold);
            String currencyId = plugin.config().economyDefaultProvider();

            return switch (type) {
                case "RAW" -> String.valueOf(plugin.core().economy().formats().round(value, true));
                case "FORMATTED" -> plugin.core().economy().formats().format(value, currencyId);
                case "SHORT" -> plugin.core().economy().formats().formatShortPlaceholder(value, currencyId);
                default -> "0";
            };
        }

        if (pLow.equals("player_gui_page") || pLow.equals("player_gui_pages")) {
            boolean isPage = pLow.endsWith("gui_page");

            var ahCtx = AuctionHouseManager.CONSTRUCTION_CONTEXT.get();
            if (ahCtx != null) return String.valueOf(isPage ? ahCtx.page() + 1 : ahCtx.totalPages());

            var plCtx = PlayerListingsManager.CONSTRUCTION_CONTEXT.get();
            if (plCtx != null) return String.valueOf(isPage ? plCtx.page() + 1 : plCtx.totalPages());

            var taCtx = TargetListingsManager.CONSTRUCTION_CONTEXT.get();
            if (taCtx != null) return String.valueOf(isPage ? taCtx.page() + 1 : taCtx.totalPages());

            var thiCtx = TargetHistoryManager.CONSTRUCTION_CONTEXT.get();
            if (thiCtx != null) return String.valueOf(isPage ? thiCtx.page() + 1 : thiCtx.totalPages());

            var exCtx = ExpiredListingsManager.CONSTRUCTION_CONTEXT.get();
            if (exCtx != null) return String.valueOf(isPage ? exCtx.page() + 1 : exCtx.totalPages());

            var seCtx = AuctionSearchManager.CONSTRUCTION_CONTEXT.get();
            if (seCtx != null) return String.valueOf(isPage ? seCtx.page() + 1 : seCtx.totalPages());

            var hiCtx = ListingHistoryManager.CONSTRUCTION_CONTEXT.get();
            if (hiCtx != null) return String.valueOf(isPage ? hiCtx.page() + 1 : hiCtx.totalPages());

            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PageableHolder holder) {
                return String.valueOf(isPage ? holder.page() + 1 : holder.totalPages());
            }

            return "1";
        }

        return null;
    }
}