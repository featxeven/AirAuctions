package com.ftxeven.airauctions.api.papi;

import com.ftxeven.airauctions.AirAuctions;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AirAuctionsExpansion extends PlaceholderExpansion {

    @FunctionalInterface
    private interface Section {
        @Nullable String resolve(@Nullable OfflinePlayer viewer, String key);
    }

    private final AirAuctions plugin;
    private final Map<String, Section> sections;

    public AirAuctionsExpansion(AirAuctions plugin) {
        this.plugin = plugin;
        this.sections = buildSections(plugin);
    }

    private static Map<String, Section> buildSections(AirAuctions plugin) {
        ListingPlaceholders listings = new ListingPlaceholders(plugin.services());
        EconomyPlaceholders economy = new EconomyPlaceholders(plugin.services(), plugin.configs());
        GuiPlaceholders gui = new GuiPlaceholders(plugin.guis(), plugin.configs(), plugin.services().players());

        Map<String, Section> sections = new LinkedHashMap<>();
        sections.put("max_listings", listings::maxListings);
        sections.put("available_slots", listings::availableSlots);
        sections.put("listings_", listings::perPlayer);
        sections.put("global_listings_", listings::global);
        sections.put("spent_", economy::spent);
        sections.put("earned_", economy::earned);
        sections.put("volume_", economy::volume);
        sections.put("gui_", (viewer, key) -> {
            if (viewer == null) {
                return null;
            }
            return viewer.isOnline() ? gui.resolve(viewer.getPlayer(), key) : "";
        });

        return Map.copyOf(sections);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "airauctions";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public @NotNull String getRequiredPlugin() {
        return "AirAuctions";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Section> section : sections.entrySet()) {
            String prefix = section.getKey();
            if (lower.startsWith(prefix)) {
                return section.getValue().resolve(player, params.substring(prefix.length()));
            }
        }
        return null;
    }
}