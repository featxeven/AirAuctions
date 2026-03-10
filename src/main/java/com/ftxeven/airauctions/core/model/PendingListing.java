package com.ftxeven.airauctions.core.model;

import org.bukkit.inventory.ItemStack;

public record PendingListing(
        ItemStack item,
        double price,
        int amount,
        long timestamp
) {}