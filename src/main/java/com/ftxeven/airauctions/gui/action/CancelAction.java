package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.ListingService;

public final class CancelAction extends TargetedListingAction {

    public CancelAction(ServiceManager services, ConfigManager configs) {
        super(services, configs);
    }

    @Override
    protected void perform(ActionContext context, Listing listing, String args) {
        ActionResult<ListingService.CancelSuccess> result = services.listings().cancel(context.viewer(), listing);
        if (result instanceof ActionResult.Denied<ListingService.CancelSuccess> denied) {
            notifyDenied(context, configs, denied);
        }
    }
}