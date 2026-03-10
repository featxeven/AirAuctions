package com.ftxeven.airauctions.core.model;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public record AuctionHistory(
        int id,
        UUID sellerUuid,
        UUID buyerUuid,
        ItemStack item,
        double price,
        String currencyId,
        long soldAt
) {}