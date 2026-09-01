package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.ServiceManager;
import org.bukkit.entity.Player;

public final class DeleteAction extends TargetedListingAction {

    public static final String PERMISSION = "airauctions.command.delete";

    public DeleteAction(ServiceManager services, ConfigManager configs) {
        super(services, configs);
    }

    @Override
    protected void perform(ActionContext context, Listing listing, String args) {
        Player admin = context.viewer();
        Eligibility eligibility = eligibleToDelete(admin);
        if (eligibility instanceof Eligibility.Denied denied) {
            notifyDenied(context, configs, denied);
            return;
        }

        switch (listing) {
            case Listing.Auction auction -> services.auctions().delete(auction, admin);
            case Listing.Bid bid -> services.bids().delete(bid, admin);
        }
    }

    public static Eligibility eligibleToDelete(Player admin) {
        return admin.hasPermission(PERMISSION) ? Eligibility.eligible() : Eligibility.denied("errors.item.unavailable");
    }
}