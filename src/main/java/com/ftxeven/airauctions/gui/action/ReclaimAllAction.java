package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.action.ActionRegistry;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;

public final class ReclaimAllAction implements ActionRegistry.Handler {

    public static final String ATTR_QUERY = "bulk-reclaim-query";

    private final ServiceManager services;
    private final ReclaimService.ReclaimKind kind;

    public ReclaimAllAction(ServiceManager services, ReclaimService.ReclaimKind kind) {
        this.services = services;
        this.kind = kind;
    }

    @Override
    public void execute(ActionContext context, String args) {
        GuiSession session = context.session();
        ListingQuery query = session.attribute(ATTR_QUERY, ListingQuery.class);
        if (query == null) {
            query = ListingQuery.owned(context.viewer().getUniqueId(), kind.scope());
        }
        services.reclaims().reclaimAll(kind, context.viewer(), query);
    }
}