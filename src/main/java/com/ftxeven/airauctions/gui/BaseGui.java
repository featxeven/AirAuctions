package com.ftxeven.airauctions.gui;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.GuiRenderer;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.database.query.*;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.gui.render.FilterOptions;
import com.ftxeven.airauctions.gui.render.SortOptions;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.model.ListingType;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Placeholders;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public abstract class BaseGui implements GuiRenderer.DynamicRenderer {

    public static final String ATTR_FILTER_CATEGORY = "filter-category";
    public static final String ATTR_FILTER_TYPE = "filter-type";
    public static final String ATTR_FILTER_ECONOMY = "filter-economy";
    public static final String ATTR_SORT_DIMENSION = "sort-dimension";
    public static final String ATTR_SEARCH_QUERY = "search-query";

    public static final Set<String> FILTER_DIMENSIONS = Set.of("category", "type", "economy");
    public static final Set<String> SORT_DIMENSIONS = Set.of("active", "unclaimed", "history");

    public static final String HISTORY_VARIANT_SALE = "sale";
    public static final String HISTORY_VARIANT_PURCHASE = "purchase";

    protected static final Map<String, ListingType> TYPE_FILTER_KEYS = Map.of(
            "auctions", ListingType.AUCTION,
            "bids", ListingType.BID
    );

    protected final ServiceManager services;
    protected final ConfigManager configs;
    protected final ListingGuiManager guis;

    protected BaseGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        this.services = services;
        this.configs = configs;
        this.guis = guis;
    }

    protected LayoutConfig layout(GuiSession session) {
        return guis.layout(session.definition());
    }

    // Self / target duality

    protected boolean isTargetView(GuiSession session) {
        return session.target() != null;
    }

    protected UUID requireTarget(GuiSession session) {
        UUID target = session.target();
        return target != null ? target : new UUID(0, 0);
    }

    protected @Nullable UUID owner(Player viewer, GuiSession session) {
        return isTargetView(session) ? requireTarget(session) : viewer.getUniqueId();
    }

    public static final String ATTR_LISTING_ID = "target-listing-id";

    private static final String ATTR_TARGET_LISTING = "target-listing-resolved";
    private static final String ATTR_TARGET_SCOPE = "target-listing-scope";

    protected @Nullable Listing resolveTargetListing(Player viewer, GuiSession session) {
        String id = session.attribute(ATTR_LISTING_ID, String.class);
        Listing listing = id != null ? services.listings().find(id).orElse(null) : null;
        ListingScope scope = listing != null ? services.listings().resolveScope(listing).orElse(null) : null;
        if (scope == null) {
            listing = null; // found but out of every valid scope (or not found at all)
        }

        session.attribute(ATTR_TARGET_LISTING, listing);
        session.attribute(ATTR_TARGET_SCOPE, scope);
        session.flagResolver(guis.flags().forListing(viewer, scope, listing));
        if (listing != null) {
            session.placeholders().putAll(guis.placeholders().forListing(listing));
        }
        return listing;
    }

    protected @Nullable Listing targetListing(GuiSession session) {
        return session.attribute(ATTR_TARGET_LISTING, Listing.class);
    }

    protected @Nullable ListingScope targetScope(GuiSession session) {
        return session.attribute(ATTR_TARGET_SCOPE, ListingScope.class);
    }

    // Filter / sort attribute keys

    public static String filterAttribute(String dimension) {
        return "filter-" + dimension.toLowerCase(Locale.ROOT);
    }

    public static String sortAttribute(String dimension) {
        return "sort-" + dimension.toLowerCase(Locale.ROOT);
    }

    protected String selected(GuiSession session, String attributeKey, LinkedHashMap<String, String> options) {
        String value = session.attribute(attributeKey, String.class);
        if (value != null && (value.equals("all") || options.containsKey(value))) {
            return value;
        }

        String resolved = options.isEmpty() ? "all" : options.keySet().iterator().next();
        session.attribute(attributeKey, resolved);
        return resolved;
    }

    // Filter option sources

    protected LinkedHashMap<String, String> categoryOptions() {
        return FilterOptions.category(configs);
    }

    protected LinkedHashMap<String, String> typeOptions() {
        return FilterOptions.type(configs);
    }

    protected LinkedHashMap<String, String> economyOptions() {
        return FilterOptions.economy(configs, true);
    }

    // Filter selection -> query value

    protected Optional<String> resolveCategoryFilter(GuiSession session, LayoutConfig.Cycler filters) {
        if (filters.isExcluded("category")) {
            return Optional.empty();
        }
        String category = selected(session, ATTR_FILTER_CATEGORY, categoryOptions());
        return category.equals("all") ? Optional.empty() : Optional.of(category);
    }

    protected Optional<ListingType> resolveTypeFilter(GuiSession session, LayoutConfig.Cycler filters) {
        if (filters.isExcluded("type")) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPE_FILTER_KEYS.get(selected(session, ATTR_FILTER_TYPE, typeOptions())));
    }

    protected Optional<String> resolveEconomyFilter(GuiSession session, LayoutConfig.Cycler filters) {
        if (filters.isExcluded("economy") || !configs.expansions().economy().multiCurrency()) {
            return Optional.empty();
        }
        String economy = selected(session, ATTR_FILTER_ECONOMY, economyOptions());
        return economy.equals("all") ? Optional.empty() : Optional.of(economy);
    }

    // Sorting

    protected static String sortDimension(ListingScope scope) {
        return switch (scope) {
            case ACTIVE -> "active";
            case EXPIRED, STORAGE -> "unclaimed";
        };
    }

    protected static Map<String, ListingSort> sortKeys(ListingScope scope) {
        return scope == ListingScope.ACTIVE ? SortOptions.ACTIVE : SortOptions.UNCLAIMED;
    }

    protected LinkedHashMap<String, String> sortOptions(ListingScope scope) {
        return new LinkedHashMap<>(configs.main().listings().sort().options(sortDimension(scope)));
    }

    protected LinkedHashMap<String, String> historySortOptions() {
        return new LinkedHashMap<>(configs.main().listings().sort().options("history"));
    }

    protected ListingSort currentListingSort(GuiSession session, ListingScope scope) {
        String dimension = sortDimension(scope);
        if (layout(session).sorts().isExcluded(dimension)) {
            return ListingSort.NEWEST;
        }
        String key = selected(session, sortAttribute(dimension), sortOptions(scope));
        return sortKeys(scope).getOrDefault(key, ListingSort.NEWEST);
    }

    protected ListingSort currentHistorySort(GuiSession session) {
        if (layout(session).sorts().isExcluded("history")) {
            return ListingSort.NEWEST;
        }
        String key = selected(session, sortAttribute("history"), historySortOptions());
        return SortOptions.HISTORY.getOrDefault(key, ListingSort.NEWEST);
    }

    // Query building

    protected ListingQuery.Builder listingQueryBuilder(GuiSession session, @Nullable UUID owner, ListingScope scope, int pageSize, int page) {
        LayoutConfig.Cycler filters = layout(session).filters();
        ListingQuery.Builder builder = ListingQuery.builder(scope, pageSize)
                .owner(owner)
                .page(page)
                .sort(currentListingSort(session, scope));

        resolveCategoryFilter(session, filters).ifPresent(builder::category);
        resolveTypeFilter(session, filters).ifPresent(builder::listingType);
        resolveEconomyFilter(session, filters).ifPresent(builder::economy);

        return builder;
    }

    protected HistoryQuery.Builder historyQueryBuilder(GuiSession session, @Nullable UUID owner, HistoryQuery.Role role, int pageSize, int page) {
        LayoutConfig.Cycler filters = layout(session).filters();
        HistoryQuery.Builder builder = HistoryQuery.builder(pageSize)
                .player(owner)
                .role(role)
                .page(page)
                .sort(currentHistorySort(session));

        resolveCategoryFilter(session, filters).ifPresent(builder::category);
        resolveTypeFilter(session, filters).ifPresent(builder::listingType);
        resolveEconomyFilter(session, filters).ifPresent(builder::economy);

        return builder;
    }

    protected static <Q extends FilterableQuery<Q>> UnfilteredQuery<Q> resolveUnfiltered(Q query, PageResult<?> filteredPage, Function<Q, PageResult<?>> repositoryQuery) {
        Q stripped = query.withoutCategory().withoutListingType().withoutEconomy();
        long grandTotal = stripped.equals(query) ? filteredPage.totalResults() : repositoryQuery.apply(stripped).totalResults();
        return new UnfilteredQuery<>(stripped, grandTotal);
    }

    protected record UnfilteredQuery<Q extends FilterableQuery<Q>>(Q query, long grandTotal) {}

    // History rendering

    protected static String historyVariant(HistoryEntry entry, @Nullable UUID owner) {
        return entry.info().buyer().equals(owner) ? HISTORY_VARIANT_PURCHASE : HISTORY_VARIANT_SALE;
    }

    protected static HistoryQuery.Role resolveHistoryRole(@Nullable LayoutConfig.ListingRender listingRender) {
        if (listingRender == null) {
            return HistoryQuery.Role.EITHER;
        }
        boolean sale = listingRender.allows(HISTORY_VARIANT_SALE);
        boolean purchase = listingRender.allows(HISTORY_VARIANT_PURCHASE);
        if (sale && !purchase) {
            return HistoryQuery.Role.SELLER;
        }
        if (purchase && !sale) {
            return HistoryQuery.Role.BUYER;
        }
        return HistoryQuery.Role.EITHER;
    }

    // Pagination + cycler placeholders

    protected void writePageResult(GuiSession session, PageResult<?> page, long grandTotal) {
        Map<String, String> placeholders = session.placeholders();
        placeholders.put("current", String.valueOf(page.totalResults()));
        placeholders.put("total", String.valueOf(grandTotal));

        session.totalPages(page.totalPages());
    }

    protected void writeSellerPlaceholder(GuiSession session, @Nullable UUID owner) {
        if (owner != null) {
            session.placeholders().put("seller", services.players().name(owner));
        }
    }

    protected void writeFilterCyclers(GuiSession session, LayoutConfig.Cycler filters, Supplier<FacetCounts> facets) {
        FacetCounts counts = facets.get();

        LinkedHashMap<String, String> categoryOptions = categoryOptions();
        String selectedCategory = selected(session, ATTR_FILTER_CATEGORY, categoryOptions);
        writeCycler(session, "filter_CATEGORY", "category", filters, categoryOptions, selectedCategory, withAllCount(counts.byCategory()), LayoutConfig.Cycler.Format.FILTER_DEFAULT);

        Map<ListingType, Long> byType = counts.byListingType();
        long auctions = byType.getOrDefault(ListingType.AUCTION, 0L);
        long bids = byType.getOrDefault(ListingType.BID, 0L);
        Map<String, Long> typeCounts = Map.of("auctions", auctions, "bids", bids, "all", auctions + bids);
        LinkedHashMap<String, String> typeOptions = typeOptions();
        String selectedType = selected(session, ATTR_FILTER_TYPE, typeOptions);
        writeCycler(session, "filter_TYPE", "type", filters, typeOptions, selectedType, typeCounts, LayoutConfig.Cycler.Format.FILTER_DEFAULT);

        if (configs.expansions().economy().multiCurrency()) {
            LinkedHashMap<String, String> economyOptions = economyOptions();
            String selectedEconomy = selected(session, ATTR_FILTER_ECONOMY, economyOptions);
            writeCycler(session, "filter_ECONOMY", "economy", filters, economyOptions, selectedEconomy, withAllCount(counts.byEconomy()), LayoutConfig.Cycler.Format.FILTER_DEFAULT);
        }
    }

    protected void writeSortCycler(GuiSession session, LayoutConfig.Cycler sorts, String dimension, LinkedHashMap<String, String> options) {
        session.attribute(ATTR_SORT_DIMENSION, dimension);
        String selectedSort = selected(session, sortAttribute(dimension), options);
        writeCycler(session, "sort_" + dimension.toUpperCase(Locale.ROOT), dimension, sorts, options, selectedSort, null, LayoutConfig.Cycler.Format.SORT_DEFAULT);
    }

    private Map<String, Long> withAllCount(Map<String, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Long> withAll = new HashMap<>(counts);
        withAll.put("all", total);
        return withAll;
    }

    protected void writeCycler(GuiSession session, String placeholderKey, String dimension, LayoutConfig.Cycler cycler,
                               LinkedHashMap<String, String> options, String selectedOption, @Nullable Map<String, Long> counts,
                               LayoutConfig.Cycler.Format fallback) {
        if (options.isEmpty()) {
            return;
        }

        LayoutConfig.Cycler.Format format = cycler.format(dimension, fallback);

        Map<String, String> placeholders = session.placeholders();
        placeholders.put(placeholderKey, options.getOrDefault(selectedOption, selectedOption));
        placeholders.put(placeholderKey + "_id", selectedOption);

        StringBuilder list = new StringBuilder();
        for (Map.Entry<String, String> option : options.entrySet()) {
            String template = option.getKey().equals(selectedOption) ? format.selected() : format.unselected();
            long count = counts != null ? counts.getOrDefault(option.getKey(), 0L) : 0L;
            String line = Placeholders.apply(null, template, Map.of("name", option.getValue(), "count", String.valueOf(count)));
            if (!list.isEmpty()) {
                list.append('\n');
            }
            list.append(line);
        }
        placeholders.put(placeholderKey + "_list", list.toString());
    }

    // Query clamping

    protected <T> ClampedPage<T> queryClamped(int requestedPage, IntFunction<PageResult<T>> query) {
        int page = Math.max(1, requestedPage);
        PageResult<T> result = query.apply(page);

        int totalPages = Math.max(1, result.totalPages());
        if (page > totalPages) {
            page = totalPages;
            result = query.apply(page);
        }
        return new ClampedPage<>(result, page);
    }

    protected record ClampedPage<T>(PageResult<T> result, int page) {}

    // Entry drawing

    protected Iterator<Integer> drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries, Iterator<Integer> slots) {
        return guis.guis().renderer().drawEntries(viewer, session, entries, slots);
    }

    protected void drawEntries(Player viewer, GuiSession session, List<RenderEntry> entries, Set<Integer> slots) {
        guis.guis().renderer().drawEntries(viewer, session, entries, slots);
    }

    protected @Nullable ItemStack drawFeaturedEntry(Player viewer, GuiSession session, @Nullable ItemConfig.Template template,
                                                    ItemStack item, Map<String, String> placeholders, Function<String, String> flags) {
        if (template != null) {
            drawEntries(viewer, session, List.of(new RenderEntry(template, item, placeholders, flags)), layout(session).listingSlots());
        }
        drawShulkerContents(session, item);
        return item;
    }

    protected void drawShulkerContents(GuiSession session, ItemStack item) {
        Set<Integer> shulkerSlots = layout(session).shulkerSlots();
        if (shulkerSlots.isEmpty() || !(item.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return;
        }

        Iterator<Integer> slots = shulkerSlots.iterator();
        for (ItemStack content : shulkerBox.getInventory().getContents()) {
            if (!slots.hasNext()) {
                break;
            }
            int slot = slots.next();
            if (content != null && !content.getType().isAir()) {
                session.inventory().setItem(slot, content.clone());
            }
        }
    }
}