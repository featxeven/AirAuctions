package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.core.gui.action.ActionTokens;
import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.action.ActionRegistry;
import com.ftxeven.airauctions.database.query.ListingQuery;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.impl.BuyAmountGui;
import com.ftxeven.airauctions.gui.impl.ConfirmGui;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import org.bukkit.Tag;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

public final class ConfirmGate implements ActionRegistry.Handler {

    private final ConfigManager configs;
    private final Resolver resolver;

    private ConfirmGate(ConfigManager configs, Resolver resolver) {
        this.configs = configs;
        this.resolver = resolver;
    }

    @Override
    public void execute(ActionContext context, String args) {
        Optional<Target> target = resolver.resolve(context, args);
        if (target.isEmpty()) {
            TargetedListingAction.notifyUnavailable(context, configs);
            return;
        }
        if (target.get().eligibility() instanceof Eligibility.Denied denied) {
            TargetedListingAction.notifyDenied(context, configs, denied);
            return;
        }
        ScreenOpener.open(context, target.get().guiId(), target.get().attributes());
    }

    // confirm/cancel, confirm/delete, confirm/claim, confirm/collect
    public static ConfirmGate forListing(ServiceManager services, ConfigManager configs, String guiId,
                                         BiFunction<Player, Listing, Eligibility> eligibilityCheck) {
        return new ConfirmGate(configs, (context, args) -> {
            Optional<Listing> listing = TargetedListingAction.resolve(services, context);
            if (listing.isEmpty() || services.listings().resolveScope(listing.get()).isEmpty()) {
                return Optional.empty();
            }
            Eligibility eligibility = eligibilityCheck.apply(context.viewer(), listing.get());
            return Optional.of(new Target(guiId, eligibility, Map.of(BaseGui.ATTR_LISTING_ID, listing.get().info().id())));
        });
    }

    // confirm/buy, confirm/buy_shulker, confirm/buy_amount
    public static ConfirmGate forBuy(ServiceManager services, ConfigManager configs, GuiManager guis,
                                     BiFunction<Player, Listing, Eligibility> eligibilityCheck,
                                     BiFunction<Player, Listing, Eligibility> buyAmountEligibilityCheck) {
        return new ConfirmGate(configs, (context, args) -> {
            Optional<Listing> listing = TargetedListingAction.resolve(services, context);
            if (listing.isEmpty() || services.listings().resolveScope(listing.get()).isEmpty()) {
                return Optional.empty();
            }
            String guiId = buyGuiId(guis, configs, listing.get());
            BiFunction<Player, Listing, Eligibility> checker = guiId.equals(BuyAmountGui.ID) ? buyAmountEligibilityCheck : eligibilityCheck;
            Eligibility eligibility = checker.apply(context.viewer(), listing.get());
            return Optional.of(new Target(guiId, eligibility, Map.of(BaseGui.ATTR_LISTING_ID, listing.get().info().id())));
        });
    }

    private static String buyGuiId(GuiManager guis, ConfigManager configs, Listing listing) {
        boolean isShulker = Tag.SHULKER_BOXES.isTagged(listing.info().item().getType());
        if (isShulker && TargetedListingAction.guiEnabled(guis, ConfirmGui.BUY_SHULKER)) {
            return ConfirmGui.BUY_SHULKER;
        }
        if (listing.deliverableAmount() >= configs.expansions().economy().buyAmountTrigger() && TargetedListingAction.guiEnabled(guis, BuyAmountGui.ID)) {
            return BuyAmountGui.ID;
        }
        return ConfirmGui.BUY;
    }

    // confirm/claim_all, confirm/collect_all
    public static ConfirmGate forBulkReclaim(ServiceManager services, ConfigManager configs, ReclaimService.ReclaimKind kind) {
        return new ConfirmGate(configs, (context, args) -> {
            boolean filtered = Boolean.parseBoolean(ActionTokens.parse(args).getOrDefault("filtered", "false"));
            ListingQuery query = resolveQuery(context, kind, filtered);
            Eligibility eligibility = services.reclaims().availability(kind, context.viewer(), query).toEligibility(kind);
            return Optional.of(new Target(kind.confirmAllGuiId(), eligibility, Map.of(
                    ReclaimAllAction.ATTR_QUERY, query,
                    ConfirmGui.ATTR_RECLAIM_KIND, kind)));
        });
    }

    private static ListingQuery resolveQuery(ActionContext context, ReclaimService.ReclaimKind kind, boolean filtered) {
        UUID owner = context.viewer().getUniqueId();
        if (!filtered) return ListingQuery.owned(owner, kind.scope());
        String search = context.session().attribute(BaseGui.ATTR_SEARCH_QUERY, String.class);
        return ListingQuery.builder(kind.scope(), 1).owner(owner).search(search).build();
    }

    @FunctionalInterface
    private interface Resolver {
        Optional<Target> resolve(ActionContext context, String args);
    }

    private record Target(String guiId, Eligibility eligibility, Map<String, Object> attributes) {}
}