package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ServiceManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public abstract class ListingPreviewGui extends BaseGui {

    public static final String ATTR_HISTORY_ID = "preview-history-id";
    public static final String ATTR_HISTORY_COMPLETED_AT = "preview-history-completed-at";

    private static final String ATTR_HISTORY = "preview-history";

    protected ListingPreviewGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        super(services, configs, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        if (session.attribute(ATTR_LISTING_ID, String.class) != null) {
            resolveTargetListing(viewer, session);
            return;
        }
        prepareHistory(viewer, session);
    }

    private void prepareHistory(Player viewer, GuiSession session) {
        String historyId = session.attribute(ATTR_HISTORY_ID, String.class);
        Long completedAt = session.attribute(ATTR_HISTORY_COMPLETED_AT, Long.class);
        HistoryEntry entry = historyId != null && completedAt != null
                ? services.history().find(historyId, Instant.ofEpochMilli(completedAt)).orElse(null)
                : null;

        session.attribute(ATTR_HISTORY, entry);
        session.flagResolver(guis.flags().forHistory(viewer, entry));
        if (entry != null) {
            session.placeholders().putAll(guis.placeholders().forHistory(entry));
        }
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        LayoutConfig.ListingRender listingRender = layout(session).listing();

        Listing listing = targetListing(session);
        if (listing != null) {
            ListingScope scope = targetScope(session);
            drawFeaturedEntry(viewer, session, listingRender != null ? listingRender.forType(null) : null, listing.displayItem(),
                    guis.placeholders().forListing(listing), guis.flags().forListing(viewer, scope, listing));
            return;
        }

        HistoryEntry history = resolvedHistory(session);
        if (history != null) {
            String variant = historyVariant(history, owner(viewer, session));
            drawFeaturedEntry(viewer, session, listingRender != null ? listingRender.forType(variant) : null, history.displayItem(),
                    guis.placeholders().forHistory(history), guis.flags().forHistory(viewer, history));
        }
    }

    protected @Nullable HistoryEntry resolvedHistory(GuiSession session) {
        return session.attribute(ATTR_HISTORY, HistoryEntry.class);
    }

    /** the listing id shared by both a live listing and its eventual history record */
    protected @Nullable String resolvedId(GuiSession session) {
        Listing listing = targetListing(session);
        if (listing != null) {
            return listing.info().id();
        }
        HistoryEntry history = resolvedHistory(session);
        return history != null ? history.info().id() : null;
    }

    protected @Nullable String resolvedEconomyId(GuiSession session) {
        Listing listing = targetListing(session);
        if (listing != null) {
            return listing.info().economy();
        }
        HistoryEntry history = resolvedHistory(session);
        return history != null ? history.info().economy() : null;
    }
}