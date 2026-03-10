package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionListing;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class SearchService {

    private final AirAuctions plugin;

    public SearchService(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public List<AuctionListing> search(String query, String categoryFilter) {
        String cleanQuery = query.toLowerCase().trim();

        return plugin.core().auctions().getActive(categoryFilter).stream()
                .filter(listing -> matches(listing.item(), cleanQuery))
                .collect(Collectors.toList());
    }

    private boolean matches(ItemStack item, String query) {
        if (item == null) return false;

        String displayName = "";
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                var component = meta.displayName();
                if (component != null) {
                    displayName = PlainTextComponentSerializer.plainText()
                            .serialize(component)
                            .toLowerCase();
                }
            }
        }

        String materialName = item.getType().name().replace("_", " ").toLowerCase();

        return displayName.contains(query) || materialName.contains(query);
    }
}