package com.ftxeven.airauctions.api.papi;

import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ServiceManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

final class ListingPlaceholders {

    private final ServiceManager services;

    ListingPlaceholders(ServiceManager services) {
        this.services = services;
    }

    // %max_listings%
    @Nullable String maxListings(@Nullable OfflinePlayer viewer, String key) {
        if (viewer == null || !key.isEmpty()) {
            return null;
        }
        return String.valueOf(services.validator().maxActiveListings(viewer.getUniqueId()));
    }

    // %available_slots% - bare, same as above
    @Nullable String availableSlots(@Nullable OfflinePlayer viewer, String key) {
        if (viewer == null || !key.isEmpty()) {
            return null;
        }
        return String.valueOf(services.validator().availableSlots(viewer.getUniqueId()));
    }

    // %listings_<scope>% - the viewer's own listings
    @Nullable String perPlayer(@Nullable OfflinePlayer viewer, String key) {
        return viewer != null ? count(viewer.getUniqueId(), key) : null;
    }

    // %global_listings_<scope>% - every player's listings
    @Nullable String global(@Nullable OfflinePlayer viewer, String key) {
        return count(null, key);
    }

    // a null owner counts across every player
    private @Nullable String count(@Nullable UUID owner, String key) {
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.equals("total")) {
            return String.valueOf(services.listings().totalCount(owner));
        }
        ListingScope scope = scope(lower);
        return scope != null ? String.valueOf(services.listings().count(owner, scope)) : null;
    }

    private @Nullable ListingScope scope(String token) {
        return switch (token) {
            case "active" -> ListingScope.ACTIVE;
            case "expired" -> ListingScope.EXPIRED;
            case "storage" -> ListingScope.STORAGE;
            default -> null;
        };
    }
}