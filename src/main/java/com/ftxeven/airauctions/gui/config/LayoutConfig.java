package com.ftxeven.airauctions.gui.config;

import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record LayoutConfig(
        @Nullable Cycler filters,
        @Nullable Cycler sorts,
        @Nullable Set<Integer> listingSlots,
        @Nullable ListingRender listing,
        @Nullable Set<Integer> bidderSlots,
        @Nullable ItemConfig.Template bidder,
        @Nullable Set<Integer> shulkerSlots,
        @Nullable AvailableSlots availableSlots
) {
    public static final LayoutConfig EMPTY = new LayoutConfig(null, null, null, null, null, null, null, null);

    public LayoutConfig {
        listingSlots = listingSlots != null ? orderedCopy(listingSlots) : null;
        bidderSlots = bidderSlots != null ? orderedCopy(bidderSlots) : null;
        shulkerSlots = shulkerSlots != null ? orderedCopy(shulkerSlots) : null;
    }

    private static Set<Integer> orderedCopy(Set<Integer> slots) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(slots));
    }

    public Cycler filters() {
        return filters != null ? filters : Cycler.EMPTY;
    }

    public Cycler sorts() {
        return sorts != null ? sorts : Cycler.EMPTY;
    }

    public Set<Integer> listingSlots() {
        return listingSlots != null ? listingSlots : Set.of();
    }

    public Set<Integer> bidderSlots() {
        return bidderSlots != null ? bidderSlots : Set.of();
    }

    public Set<Integer> shulkerSlots() {
        return shulkerSlots != null ? shulkerSlots : Set.of();
    }

    public AvailableSlots availableSlots() {
        return availableSlots != null ? availableSlots : AvailableSlots.DISABLED;
    }

    public record Cycler(List<String> excluded, Map<String, Format> format) {

        public static final Cycler EMPTY = new Cycler(List.of(), Map.of());

        public Cycler {
            excluded = List.copyOf(excluded);
            format = Map.copyOf(format);
        }

        public boolean isExcluded(String key) {
            return excluded.stream().anyMatch(key::equalsIgnoreCase);
        }

        public Format format(String key, Format fallback) {
            return format.getOrDefault(key, fallback);
        }

        public record Format(String selected, String unselected) {
            public static final Format FILTER_DEFAULT = new Format(
                    "<aqua>> <white>%name% <gray>(%count%)",
                    "<dark_gray>  %name% <gray>(%count%)");

            public static final Format SORT_DEFAULT = new Format(
                    "<aqua>> <white>%name%",
                    "<dark_gray>  %name%");
        }
    }

    public record AvailableSlots(boolean enabled, ItemConfig.Template template) {
        public static final AvailableSlots DISABLED = new AvailableSlots(false, new ItemConfig.Template(ItemConfig.Fields.EMPTY, List.of()));
    }

    public record ListingRender(@Nullable ItemConfig.Template shared, Map<String, ItemConfig.Template> variants) {

        public ListingRender {
            variants = Map.copyOf(variants);
        }

        public @Nullable ItemConfig.Template forType(@Nullable String type) {
            if (type != null && !variants.isEmpty()) {
                return variants.get(type);
            }
            return shared;
        }

        // true if this render config would actually produce something for 'type'
        public boolean allows(String type) {
            return variants.isEmpty() || variants.containsKey(type);
        }
    }
}