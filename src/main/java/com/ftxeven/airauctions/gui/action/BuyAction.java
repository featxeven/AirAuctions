package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.AuctionService;
import org.bukkit.entity.Player;

public final class BuyAction extends TargetedListingAction {

    public static final String ATTR_BUY_AMOUNT = "buy-amount";

    public BuyAction(ServiceManager services, ConfigManager configs) {
        super(services, configs);
    }

    @Override
    protected void perform(ActionContext context, Listing listing, String args) {
        if (!(listing instanceof Listing.Auction auction)) {
            return; // only auctions are ever bought
        }

        Player buyer = context.viewer();
        Integer selected = context.session().attribute(ATTR_BUY_AMOUNT, Integer.class);
        int amount = selected != null ? Math.min(selected, auction.remainingAmount()) : auction.remainingAmount();

        ActionResult<AuctionService.PurchaseSuccess> result = services.auctions().purchase(buyer, auction, amount);
        if (result instanceof ActionResult.Denied<AuctionService.PurchaseSuccess> denied) {
            notifyDenied(context, configs, denied);
        }
    }
}