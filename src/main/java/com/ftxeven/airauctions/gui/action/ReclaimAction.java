package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;

public final class ReclaimAction extends TargetedListingAction {

    private final ReclaimService.ReclaimKind kind;

    public ReclaimAction(ServiceManager services, ConfigManager configs, ReclaimService.ReclaimKind kind) {
        super(services, configs);
        this.kind = kind;
    }

    @Override
    protected void perform(ActionContext context, Listing listing, String args) {
        ActionResult<ReclaimService.ReclaimSuccess> result = services.reclaims().reclaim(kind, context.viewer(), listing);
        if (result instanceof ActionResult.Denied<ReclaimService.ReclaimSuccess> denied) {
            notifyDenied(context, configs, denied);
        }
    }
}