package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class ListingGridGui extends BaseGui {

    private static final String ATTR_PAGE_RESULT = "grid-page-result";

    protected ListingGridGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        super(services, configs, guis);
    }

    protected abstract ListingScope scope();

    /** Override in self-view GUIs that carry a bulk-reclaim button. */
    protected @Nullable ReclaimService.ReclaimKind reclaimKind() {
        return null;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        LayoutConfig layout = layout(session);
        int pageSize = Math.max(1, layout.listingSlots().size());
        UUID owner = owner(viewer, session);
        writeSellerPlaceholder(session, owner);

        ClampedPage<Listing> clamped = queryClamped(session.page(),
                page -> services.listings().query(buildQuery(session, owner, pageSize, page)));
        PageResult<Listing> page = clamped.result();
        session.attribute(ATTR_PAGE_RESULT, page);
        session.page(clamped.page());

        ListingQuery query = buildQuery(session, owner, pageSize, clamped.page());
        UnfilteredQuery<ListingQuery> unfiltered = resolveUnfiltered(query, page, services.listings()::query);

        writePageResult(session, page, unfiltered.grandTotal());
        writeFilterCyclers(session, layout.filters(), () -> services.listings().facets(unfiltered.query()));
        writeSortCycler(session, layout.sorts(), sortDimension(scope()), sortOptions(scope()));

        ReclaimService.ReclaimKind kind = reclaimKind();
        if (kind != null && viewer.getUniqueId().equals(owner)) {
            session.flagResolver(guis.flags().forBulkReclaim(viewer, kind,
                    ListingQuery.owned(owner, scope())));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Player viewer, GuiSession session) {
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
                        guis.placeholders().forListing(listing), guis.flags().forListing(viewer, scope(), listing)));
            }
        }

        Iterator<Integer> remaining = drawEntries(viewer, session, entries, layout(session).listingSlots().iterator());
        drawAvailableSlots(viewer, session, owner(viewer, session), remaining);
    }

    private ListingQuery buildQuery(GuiSession session, @Nullable UUID owner, int pageSize, int page) {
        return listingQueryBuilder(session, owner, scope(), pageSize, page).build();
    }

    // Available slots

    private void drawAvailableSlots(Player viewer, GuiSession session, @Nullable UUID owner, Iterator<Integer> remainingSlots) {
        LayoutConfig.AvailableSlots availableSlots = layout(session).availableSlots();
        if (!availableSlots.enabled() || owner == null || !remainingSlots.hasNext()) {
            return;
        }

        int max = services.validator().maxActiveListings(owner);
        int active = services.listings().count(owner, ListingScope.ACTIVE);
        int remaining = max < 0 ? Integer.MAX_VALUE : Math.max(0, max - active);
        if (remaining == 0) {
            return;
        }

        RenderEntry tile = new RenderEntry(availableSlots.template(), null, session.placeholders(), session.flagResolver());
        drawEntries(viewer, session, Collections.nCopies(remaining, tile), remainingSlots);
    }
}