package com.ftxeven.airauctions.core.model;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public record AuctionListing(
        int id,
        UUID sellerUuid,
        ItemStack item,
        double price,
        String currencyId,
        long createdAt,
        long expiryAt) {

    public boolean isTimedOut() {
        return expiryAt != -1 && expiryAt < System.currentTimeMillis();
    }
}