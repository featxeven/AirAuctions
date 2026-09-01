package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.action.AmountAction;
import com.ftxeven.airauctions.gui.action.AmountEditor;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.BidEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ViewBidGui extends ListingPreviewGui {

    public static final String ID = "browsing/view_bid";

    private static final String ATTR_PAGE_RESULT = "view-bid-page-result";

    private final Messenger messenger;

    public ViewBidGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis, Messenger messenger) {
        super(services, configs, guis);
        this.messenger = messenger;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        super.prepare(viewer, session);
        installOfferEditor(viewer, session);

        String listingId = resolvedId(session);
        if (listingId == null) {
            return;
        }

        int pageSize = Math.max(1, layout(session).bidderSlots().size());
        ClampedPage<BidEntry> clamped = queryClamped(session.page(),
                page -> services.bids().bidEntries(listingId, page, pageSize));
        session.attribute(ATTR_PAGE_RESULT, clamped.result());
        session.page(clamped.page());
        session.totalPages(clamped.result().totalPages());
    }

    private void installOfferEditor(Player viewer, GuiSession session) {
        Listing listing = targetListing(session);
        Listing.Bid bid = (listing instanceof Listing.Bid live) ? live : null;
        EconomyProvider provider = bid != null ? services.economy().get(bid.info().economy()).orElse(null) : null;

        if (bid != null) {
            services.economy().formatInto(session.placeholders(), "offer", bid.info().economy(),
                    services.bids().nextOfferBounds(bid).min());
            guis.placeholders().putYourOffer(session.placeholders(), bid, viewer.getUniqueId());
        }

        session.attribute(AmountAction.ATTR_EDITOR, AmountEditor.bid(
                () -> bid, () -> provider, services.bids(), services.economy(), configs, messenger));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Player viewer, GuiSession session) {
        super.render(viewer, session);

        LayoutConfig layout = layout(session);
        ItemConfig.Template bidderTemplate = layout.bidder();
        PageResult<BidEntry> page = session.attribute(ATTR_PAGE_RESULT, PageResult.class);
        String economyId = resolvedEconomyId(session);
        if (bidderTemplate == null || page == null || economyId == null) {
            return;
        }

        int pageSize = Math.max(1, layout.bidderSlots().size());
        int position = (session.page() - 1) * pageSize + 1;

        Function<String, String> flags = session.flagResolver();
        List<RenderEntry> entries = new ArrayList<>(page.items().size());
        for (BidEntry entry : page.items()) {
            entries.add(new RenderEntry(bidderTemplate, null,
                    guis.placeholders().forBidEntry(economyId, entry, position++), flags));
        }
        drawEntries(viewer, session, entries, layout.bidderSlots());
    }
}