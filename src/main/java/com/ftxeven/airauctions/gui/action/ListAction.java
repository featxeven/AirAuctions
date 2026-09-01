package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.action.ActionRegistry;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.impl.DraftGui;
import com.ftxeven.airauctions.gui.impl.ListingDraft;
import com.ftxeven.airauctions.model.ListingType;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.listing.ValidationResult;
import org.bukkit.entity.Player;

public final class ListAction implements ActionRegistry.Handler {

    private final ServiceManager services;
    private final ConfigManager configs;

    public ListAction(ServiceManager services, ConfigManager configs) {
        this.services = services;
        this.configs = configs;
    }

    @Override
    public void execute(ActionContext context, String args) {
        Player seller = context.viewer();
        ListingDraft draft = context.session().attribute(DraftGui.ATTR_DRAFT, ListingDraft.class);
        if (draft == null) {
            context.logger().warning("[list] action used on item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' but no listing draft is present on the session");
            return;
        }

        EconomyProvider provider = services.economy().get(draft.economyId()).orElse(null);
        if (provider == null) {
            TargetedListingAction.notifyUnavailable(context, configs);
            return;
        }

        ValidationResult structural = services.validator().validate(seller, draft.itemSnapshot(), draft.amount());
        if (!structural.ok()) {
            structural.send(seller, configs, context.messenger());
            return;
        }

        EconomyService economy = services.economy();
        if (TargetedListingAction.notifyIfDenied(context, configs, economy.eligibleForPrice(draft.price(), provider))) {
            return;
        }

        EconomyService.ChargeResult fee = economy.fee(seller, provider, draft.price());
        if (TargetedListingAction.notifyIfDenied(context, configs, economy.eligibleForFee(seller, provider, fee))) {
            return;
        }

        services.validator().checkListingLimits(seller, context.messenger(),
                () -> finalizeListing(context, seller, draft, provider, fee));
    }

    private void finalizeListing(ActionContext context, Player seller, ListingDraft draft,
                                 EconomyProvider provider, EconomyService.ChargeResult fee) {
        services.listings().finalizeCreation(seller, draft.itemSnapshot(), draft.amount(), draft.slot(), provider, fee,
                context.placeholders(),
                item -> draft.type() == ListingType.AUCTION
                        ? services.auctions().prepare(seller, item, draft.amount(), draft.price(), provider)
                        : services.bids().prepare(seller, item, draft.amount(), draft.price(), draft.bidDurationSeconds(), provider),
                prepared -> {
                    services.validator().markListed(seller.getUniqueId());
                    if (draft.type() == ListingType.AUCTION) {
                        services.auctions().announceCreated(seller, prepared);
                    } else {
                        services.bids().announceCreated(seller, prepared);
                    }
                });
    }
}