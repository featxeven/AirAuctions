package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.BidService;

public final class BidAction extends TargetedListingAction {

    public static final String ATTR_OFFER = "bid-offer";

    public BidAction(ServiceManager services, ConfigManager configs) {
        super(services, configs);
    }

    @Override
    protected void perform(ActionContext context, Listing listing, String args) {
        if (!(listing instanceof Listing.Bid bid)) {
            return;
        }

        Double selected = context.session().attribute(ATTR_OFFER, Double.class);
        double offer = selected != null ? selected : services.bids().nextOfferBounds(bid).min();

        ActionResult<BidService.PlaceBidSuccess> result = services.bids().placeBid(context.viewer(), bid, offer);
        if (result instanceof ActionResult.Denied<BidService.PlaceBidSuccess> denied) {
            notifyDenied(context, configs, denied);
        }
    }
}