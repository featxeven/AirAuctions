package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionListing;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class SearchService {

    private final AirAuctions plugin;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public SearchService(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public List<AuctionListing> search(String query, String categoryFilter) {
        if (query == null || query.isEmpty()) {
            return plugin.core().auctions().getActive(categoryFilter);
        }

        String cleanQuery = query.toLowerCase().trim();

        return plugin.core().auctions().getActive(categoryFilter).stream()
                .filter(listing -> matches(listing.item(), cleanQuery))
                .collect(Collectors.toList());
    }

    private boolean matches(ItemStack item, String query) {
        if (item == null) return false;

        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                var component = meta.displayName();
                if (component != null) {
                    String displayName = PLAIN.serialize(component).toLowerCase();
                    if (displayName.contains(query)) return true;
                }
            }
        }

        String translatedName = plugin.itemTranslations().translate(item.getType()).toLowerCase();
        if (translatedName.contains(query)) return true;

        String rawName = item.getType().name().replace("_", " ").toLowerCase();
        return rawName.contains(query);
    }
}