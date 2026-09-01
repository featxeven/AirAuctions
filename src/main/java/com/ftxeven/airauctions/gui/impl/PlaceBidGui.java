package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.action.AmountAction;
import com.ftxeven.airauctions.gui.action.AmountEditor;
import com.ftxeven.airauctions.gui.action.BidAction;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PlaceBidGui extends BaseGui {

    public static final String ID = "confirm/place_bid";

    private static final String ATTR_PROVIDER = "place-bid-provider";

    private final Messenger messenger;

    public PlaceBidGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis, Messenger messenger) {
        super(services, configs, guis);
        this.messenger = messenger;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        Listing resolved = resolveTargetListing(viewer, session);
        Listing.Bid bid = resolved instanceof Listing.Bid b ? b : null;
        EconomyProvider provider = bid != null ? services.economy().get(bid.info().economy()).orElse(null) : null;

        session.attribute(ATTR_PROVIDER, provider);
        session.flagResolver(guis.flags().forPlaceBid(viewer, bid, () -> offer(session, bid)));
        session.attribute(AmountAction.ATTR_EDITOR, AmountEditor.offer(
                () -> offer(session, bid),
                amount -> session.attribute(BidAction.ATTR_OFFER, amount),
                () -> provider,
                () -> bid != null,
                services.economy(), configs, messenger));

        if (bid == null || provider == null) {
            return;
        }

        double offer = offer(session, bid);
        session.attribute(BidAction.ATTR_OFFER, offer);
        session.placeholders().putAll(guis.placeholders().forPlaceBid(viewer, bid, provider, offer));
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        Listing.Bid bid = targetListing(session) instanceof Listing.Bid b ? b : null;
        EconomyProvider provider = session.attribute(ATTR_PROVIDER, EconomyProvider.class);
        LayoutConfig.ListingRender listingRender = layout(session).listing();
        if (bid == null || provider == null || listingRender == null) {
            return;
        }

        ItemConfig.Template template = listingRender.forType(null);
        if (template == null) {
            return;
        }

        double offer = offer(session, bid);
        RenderEntry entry = new RenderEntry(template, bid.displayItem(),
                guis.placeholders().forPlaceBid(viewer, bid, provider, offer),
                guis.flags().forPlaceBid(viewer, bid, () -> offer(session, bid)));
        drawEntries(viewer, session, List.of(entry), layout(session).listingSlots());
    }

    private double offer(GuiSession session, @Nullable Listing.Bid bid) {
        Double amount = session.attribute(BidAction.ATTR_OFFER, Double.class);
        if (amount != null) {
            return amount;
        }
        return bid != null ? services.bids().nextOfferBounds(bid).min() : 0;
    }
}