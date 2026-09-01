package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.database.query.HistoryQuery;
import com.ftxeven.airauctions.database.query.PageResult;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.service.ServiceManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HistoryGui extends BaseGui {

    public static final String SELF_ID = "player/history";
    public static final String TARGET_ID = "target/history";

    private static final String ATTR_PAGE_RESULT = "history-page-result";

    public HistoryGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        super(services, configs, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        LayoutConfig layout = layout(session);
        int pageSize = Math.max(1, layout.listingSlots().size());
        UUID owner = owner(viewer, session);
        writeSellerPlaceholder(session, owner);

        HistoryQuery.Role role = resolveHistoryRole(layout.listing());
        ClampedPage<HistoryEntry> clamped = queryClamped(session.page(),
                page -> services.history().query(buildQuery(session, owner, pageSize, page, role)));
        PageResult<HistoryEntry> page = clamped.result();
        session.attribute(ATTR_PAGE_RESULT, page);
        session.page(clamped.page());

        HistoryQuery query = buildQuery(session, owner, pageSize, clamped.page(), role);
        UnfilteredQuery<HistoryQuery> unfiltered = resolveUnfiltered(query, page, services.history()::query);

        writePageResult(session, page, unfiltered.grandTotal());
        writeFilterCyclers(session, layout.filters(), () -> services.history().facets(unfiltered.query()));
        writeSortCycler(session, layout.sorts(), "history", historySortOptions());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Player viewer, GuiSession session) {
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

    private HistoryQuery buildQuery(GuiSession session, @Nullable UUID owner, int pageSize, int page, HistoryQuery.Role role) {
        return historyQueryBuilder(session, owner, role, pageSize, page).build();
    }
}