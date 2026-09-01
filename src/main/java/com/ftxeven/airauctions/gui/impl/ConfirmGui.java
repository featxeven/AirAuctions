package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.action.ReclaimAllAction;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import org.bukkit.entity.Player;

public final class ConfirmGui extends BaseGui {

    public static final String BUY = "confirm/buy";
    public static final String BUY_SHULKER = "confirm/buy_shulker";
    public static final String CANCEL = "confirm/cancel";
    public static final String DELETE = "confirm/delete";

    public static final String ATTR_RECLAIM_KIND = "confirm-reclaim-kind";

    public ConfirmGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        super(services, configs, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        ReclaimService.ReclaimKind kind = session.attribute(ATTR_RECLAIM_KIND, ReclaimService.ReclaimKind.class);
        if (kind != null) {
            ListingQuery query = session.attribute(ReclaimAllAction.ATTR_QUERY, ListingQuery.class);
            if (query == null) {
                query = ListingQuery.owned(viewer.getUniqueId(), kind.scope());
            }
            session.flagResolver(guis.flags().forBulkReclaim(viewer, kind, query));
            return;
        }

        resolveTargetListing(viewer, session);
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        Listing listing = targetListing(session);
        ListingScope scope = targetScope(session);
        LayoutConfig.ListingRender listingRender = layout(session).listing();
        if (listing == null || scope == null || listingRender == null) {
            return;
        }

        drawFeaturedEntry(viewer, session, listingRender.forType(null), listing.displayItem(),
                guis.placeholders().forListing(listing), guis.flags().forListing(viewer, scope, listing));
    }
}