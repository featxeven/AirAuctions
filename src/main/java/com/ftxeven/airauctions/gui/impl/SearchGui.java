package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.database.query.HistoryQuery;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SearchGui extends BaseGui {

    public static final String ID = "browsing/search";

    private static final String ATTR_PAGE_RESULT = "search-page-result";

    private enum Kind { GLOBAL, LISTING, HISTORY }

    private record Source(Kind kind, @Nullable ListingScope scope) {}

    private static final Source GLOBAL = new Source(Kind.GLOBAL, ListingScope.ACTIVE);
    private static final Source LISTING_ACTIVE = new Source(Kind.LISTING, ListingScope.ACTIVE);
    private static final Source LISTING_EXPIRED = new Source(Kind.LISTING, ListingScope.EXPIRED);
    private static final Source LISTING_STORAGE = new Source(Kind.LISTING, ListingScope.STORAGE);
    private static final Source HISTORY_SOURCE = new Source(Kind.HISTORY, null);

    private static final Map<String, Source> SOURCES = Map.ofEntries(
            Map.entry(GlobalGui.ID, GLOBAL),
            Map.entry(ActiveGui.SELF_ID, LISTING_ACTIVE),
            Map.entry(ActiveGui.TARGET_ID, LISTING_ACTIVE),
            Map.entry(ExpiredGui.SELF_ID, LISTING_EXPIRED),
            Map.entry(ExpiredGui.TARGET_ID, LISTING_EXPIRED),
            Map.entry(StorageGui.SELF_ID, LISTING_STORAGE),
            Map.entry(StorageGui.TARGET_ID, LISTING_STORAGE),
            Map.entry(HistoryGui.SELF_ID, HISTORY_SOURCE),
            Map.entry(HistoryGui.TARGET_ID, HISTORY_SOURCE)
    );

    public SearchGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        super(services, configs, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        Source source = resolveSource(session);
        LayoutConfig layout = layout(session);
        int pageSize = Math.max(1, layout.listingSlots().size());
        UUID owner = source.kind() == Kind.GLOBAL ? null : owner(viewer, session);

        String query = session.attribute(ATTR_SEARCH_QUERY, String.class);
        session.placeholders().put("query", query != null ? query : "");
        writeSellerPlaceholder(session, owner);

        if (source.kind() == Kind.HISTORY) {
            prepareHistory(session, layout, pageSize, owner);
        } else {
            prepareListing(viewer, session, layout, pageSize, owner, source.scope());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Player viewer, GuiSession session) {
        Source source = resolveSource(session);
        if (source.kind() == Kind.HISTORY) {
            renderHistory(viewer, session);
        } else {
            renderListing(viewer, session, source.scope());
        }
    }

    // Listings (global / active / expired / storage)

    private void prepareListing(Player viewer, GuiSession session, LayoutConfig layout,
                                int pageSize, @Nullable UUID owner, ListingScope scope) {
        ClampedPage<Listing> clamped = queryClamped(session.page(),
                page -> services.listings().query(buildListingQuery(session, owner, scope, pageSize, page)));
        PageResult<Listing> page = clamped.result();
        session.attribute(ATTR_PAGE_RESULT, page);
        session.page(clamped.page());

        ListingQuery query = buildListingQuery(session, owner, scope, pageSize, clamped.page());
        UnfilteredQuery<ListingQuery> unfiltered = resolveUnfiltered(query, page, services.listings()::query);

        writePageResult(session, page, unfiltered.grandTotal());
        writeFilterCyclers(session, layout.filters(), () -> services.listings().facets(unfiltered.query()));
        writeSortCycler(session, layout.sorts(), sortDimension(scope), sortOptions(scope));

        ReclaimService.ReclaimKind kind = reclaimKindFor(scope);
        if (kind != null && viewer.getUniqueId().equals(owner)) {
            String search = session.attribute(ATTR_SEARCH_QUERY, String.class);
            ListingQuery reclaimQuery = ListingQuery.builder(scope, 1).owner(owner).search(search).build();
            session.flagResolver(guis.flags().forBulkReclaim(viewer, kind, reclaimQuery));
        }
    }

    private void renderListing(Player viewer, GuiSession session, ListingScope scope) {
        PageResult<Listing> page = session.attribute(ATTR_PAGE_RESULT, PageResult.class);
        LayoutConfig.ListingRender listingRender = layout(session).listing();
        if (page == null || listingRender == null) {
            return;
        }

        List<RenderEntry> entries = new ArrayList<>(page.items().size());
        for (Listing listing : page.items()) {
            ItemConfig.Template template = listingRender.forType(null);
            if (template != null) {
                entries.add(new RenderEntry(template, listing.displayItem(),
                        guis.placeholders().forListing(listing), guis.flags().forListing(viewer, scope, listing)));
            }
        }
        drawEntries(viewer, session, entries, layout(session).listingSlots().iterator());
    }

    private ListingQuery buildListingQuery(GuiSession session, @Nullable UUID owner, ListingScope scope, int pageSize, int page) {
        return listingQueryBuilder(session, owner, scope, pageSize, page)
                .search(session.attribute(ATTR_SEARCH_QUERY, String.class))
                .build();
    }

    private static @Nullable ReclaimService.ReclaimKind reclaimKindFor(ListingScope scope) {
        return switch (scope) {
            case EXPIRED -> ReclaimService.ReclaimKind.CLAIM;
            case STORAGE -> ReclaimService.ReclaimKind.COLLECT;
            default -> null;
        };
    }

    // History

    private void prepareHistory(GuiSession session, LayoutConfig layout, int pageSize, @Nullable UUID owner) {
        HistoryQuery.Role role = resolveHistoryRole(layout.listing());
        ClampedPage<HistoryEntry> clamped = queryClamped(session.page(),
                page -> services.history().query(buildHistoryQuery(session, owner, role, pageSize, page)));
        PageResult<HistoryEntry> page = clamped.result();
        session.attribute(ATTR_PAGE_RESULT, page);
        session.page(clamped.page());

        HistoryQuery query = buildHistoryQuery(session, owner, role, pageSize, clamped.page());
        UnfilteredQuery<HistoryQuery> unfiltered = resolveUnfiltered(query, page, services.history()::query);

        writePageResult(session, page, unfiltered.grandTotal());
        writeFilterCyclers(session, layout.filters(), () -> services.history().facets(unfiltered.query()));
        writeSortCycler(session, layout.sorts(), "history", historySortOptions());
    }

    private void renderHistory(Player viewer, GuiSession session) {
        PageResult<HistoryEntry> page = session.attribute(ATTR_PAGE_RESULT, PageResult.class);
        LayoutConfig.ListingRender listingRender = layout(session).listing();
        if (page == null || listingRender == null) {
            return;
        }

        UUID owner = owner(viewer, session);
        List<RenderEntry> entries = new ArrayList<>(page.items().size());
        for (HistoryEntry entry : page.items()) {
            ItemConfig.Template template = listingRender.forType(historyVariant(entry, owner));
            if (template != null) {
                entries.add(new RenderEntry(template, entry.displayItem(),
                        guis.placeholders().forHistory(entry), guis.flags().forHistory(viewer, entry)));
            }
        }
        drawEntries(viewer, session, entries, layout(session).listingSlots().iterator());
    }

    private HistoryQuery buildHistoryQuery(GuiSession session, @Nullable UUID owner, HistoryQuery.Role role, int pageSize, int page) {
        return historyQueryBuilder(session, owner, role, pageSize, page)
                .search(session.attribute(ATTR_SEARCH_QUERY, String.class))
                .build();
    }

    // Source resolution

    private Source resolveSource(GuiSession session) {
        List<String> chain = session.originChain();
        String origin = chain.isEmpty() ? null : chain.getLast();
        return origin != null ? SOURCES.getOrDefault(origin, GLOBAL) : GLOBAL;
    }
}