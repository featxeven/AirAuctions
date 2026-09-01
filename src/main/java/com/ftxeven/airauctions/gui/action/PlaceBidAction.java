package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.impl.PlaceBidGui;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ServiceManager;

import java.util.Map;

public final class PlaceBidAction extends TargetedListingAction {

    private final GuiManager guis;

    public PlaceBidAction(ServiceManager services, ConfigManager configs, GuiManager guis) {
        super(services, configs);
        this.guis = guis;
    }

    @Override
    protected void perform(ActionContext context, Listing listing, String args) {
        if (!(listing instanceof Listing.Bid bid) || services.listings().resolveScope(listing).isEmpty()) {
            notifyUnavailable(context, configs);
            return;
        }

        if (notifyIfDenied(context, configs, services.bids().eligibleToBid(context.viewer(), bid))) {
            return;
        }

        if (guiEnabled(guis, PlaceBidGui.ID)) {
            ScreenOpener.open(context, PlaceBidGui.ID, Map.of(BaseGui.ATTR_LISTING_ID, listing.info().id()));
        } else {
            requestTypedBid(context, bid);
        }
    }

    private void requestTypedBid(ActionContext context, Listing.Bid bid) {
        AmountEditor editor = AmountEditor.bid(() -> bid, () -> services.economy().get(bid.info().economy()).orElse(null),
                services.bids(), services.economy(), configs, context.messenger());
        context.manager().input().request(context, editor.inputContext(), raw -> {
            editor.applyTyped(context.viewer(), raw);
            context.manager().resume(context.viewer());
        });
    }
}