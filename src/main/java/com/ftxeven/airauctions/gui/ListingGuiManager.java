package com.ftxeven.airauctions.gui;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.animation.AnimationManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.config.GuiConfig;
import com.ftxeven.airauctions.core.gui.input.InputRegistry;
import com.ftxeven.airauctions.gui.action.*;
import com.ftxeven.airauctions.gui.action.ConfirmGate;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.gui.impl.*;
import com.ftxeven.airauctions.gui.render.ListingFlags;
import com.ftxeven.airauctions.gui.render.ListingPlaceholders;
import com.ftxeven.airauctions.gui.render.PlayerHeadResolver;
import com.ftxeven.airauctions.core.hook.HookRegistry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.PlayerData;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.workflow.ReclaimService;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.TimeFormatter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class ListingGuiManager {

    private static final Set<String> RESERVED_PATHS = Set.of(InputRegistry.INPUT_FOLDER);

    private final GuiManager guis;
    private final LayoutGuiRegistry registry;
    private final ListingFlags flags;
    private final ListingPlaceholders placeholders;
    private final Messenger messenger;

    private ListingGuiManager(GuiManager guis, LayoutGuiRegistry registry, ListingFlags flags,
                              ListingPlaceholders placeholders, Messenger messenger) {
        this.guis = guis;
        this.registry = registry;
        this.flags = flags;
        this.placeholders = placeholders;
        this.messenger = messenger;
    }

    public static @Nullable ListingGuiManager create(JavaPlugin plugin, Messenger messenger, ConfigManager configs,
                                                     HookRegistry hooks, ServiceManager services, AnimationManager animations) {
        GuiManager guis = GuiManager.builder(plugin, messenger, animations)
                .hooks(hooks)
                .heads(new PlayerHeadResolver(services.players()))
                .players(name -> services.players().findByName(name).map(PlayerData::uuid).orElse(null))
                .cooldownFormatter(seconds -> TimeFormatter.duration(
                        Duration.ofSeconds(Math.max(1L, (long) Math.ceil(seconds))),
                        configs.main().formatting(),
                        configs.lang()))
                .reservedPaths(RESERVED_PATHS)
                .layoutReplaceKeys(LayoutConfig.TEMPLATE_BLOCKS)
                .build();
        if (guis == null) {
            return null;
        }
        guis.onStaleClick((viewer, session) ->
                messenger.send(viewer, configs.lang().get("errors.item.unavailable"), session.placeholders()));

        LayoutGuiRegistry registry = new LayoutGuiRegistry(plugin, guis);
        registry.load();

        ListingGuiManager manager = new ListingGuiManager(guis, registry, new ListingFlags(configs, services, guis),
                new ListingPlaceholders(configs, services), messenger);
        manager.registerActions(services, configs);
        manager.registerGuis(services, configs);
        return manager;
    }

    public GuiManager guis() {
        return guis;
    }

    public LayoutConfig layout(GuiConfig config) {
        return registry.layout(config);
    }

    public ListingFlags flags() {
        return flags;
    }

    public ListingPlaceholders placeholders() {
        return placeholders;
    }

    private void registerActions(ServiceManager services, ConfigManager configs) {
        guis.actions().register("filter", new FilterAction(configs, this));
        guis.actions().register("sort", new SortAction(configs, this));
        guis.actions().register("search", new SearchAction(services));
        guis.actions().register("amount", new AmountAction(configs));
        guis.actions().register("view_shulker", new ViewShulkerAction(services, configs));
        guis.actions().register("view_bid", new ViewBidAction(services, configs));
        guis.actions().register("list", new ListAction(services, configs));
        guis.actions().register("place_bid", new PlaceBidAction(services, configs, guis));
        guis.actions().register("bid", new BidAction(services, configs));

        guis.actions().registerConfirmable("buy",
                new BuyAction(services, configs),
                ConfirmGate.forBuy(services, configs, guis,
                        (viewer, listing) -> listing instanceof Listing.Auction auction
                                ? services.auctions().eligibleToPurchase(viewer, auction)
                                : Eligibility.denied("errors.item.unavailable"),
                        (viewer, listing) -> listing instanceof Listing.Auction auction
                                ? services.auctions().eligibleToOpenBuyAmount(viewer, auction)
                                : Eligibility.denied("errors.item.unavailable")));
        guis.actions().registerConfirmable("cancel",
                new CancelAction(services, configs),
                ConfirmGate.forListing(services, configs, ConfirmGui.CANCEL,
                        (viewer, listing) -> services.listings().eligibleToCancel(viewer, listing)));
        guis.actions().registerConfirmable("delete",
                new DeleteAction(services, configs),
                ConfirmGate.forListing(services, configs, ConfirmGui.DELETE,
                        (viewer, listing) -> DeleteAction.eligibleToDelete(viewer)));
        guis.actions().registerConfirmable("claim",
                new ReclaimAction(services, configs, ReclaimService.ReclaimKind.CLAIM),
                ConfirmGate.forListing(services, configs, ReclaimService.ReclaimKind.CLAIM.confirmGuiId(),
                        (viewer, listing) -> services.reclaims().eligibleToReclaim(ReclaimService.ReclaimKind.CLAIM, viewer, listing)));
        guis.actions().registerConfirmable("collect",
                new ReclaimAction(services, configs, ReclaimService.ReclaimKind.COLLECT),
                ConfirmGate.forListing(services, configs, ReclaimService.ReclaimKind.COLLECT.confirmGuiId(),
                        (viewer, listing) -> services.reclaims().eligibleToReclaim(ReclaimService.ReclaimKind.COLLECT, viewer, listing)));
        guis.actions().registerConfirmable("claim_all",
                new ReclaimAllAction(services, ReclaimService.ReclaimKind.CLAIM),
                ConfirmGate.forBulkReclaim(services, configs, ReclaimService.ReclaimKind.CLAIM));
        guis.actions().registerConfirmable("collect_all",
                new ReclaimAllAction(services, ReclaimService.ReclaimKind.COLLECT),
                ConfirmGate.forBulkReclaim(services, configs, ReclaimService.ReclaimKind.COLLECT));
    }

    private void registerGuis(ServiceManager services, ConfigManager configs) {
        guis.dynamicRenderer(GlobalGui.ID, new GlobalGui(services, configs, this));
        guis.dynamicRenderer(SearchGui.ID, new SearchGui(services, configs, this));

        registerBoth(new ActiveGui(services, configs, this), ActiveGui.SELF_ID,  ActiveGui.TARGET_ID);
        registerBoth(new ExpiredGui(services, configs, this), ExpiredGui.SELF_ID, ExpiredGui.TARGET_ID);
        registerBoth(new StorageGui(services, configs, this), StorageGui.SELF_ID, StorageGui.TARGET_ID);
        registerBoth(new HistoryGui(services, configs, this), HistoryGui.SELF_ID, HistoryGui.TARGET_ID);

        guis.dynamicRenderer(ViewShulkerGui.ID, new ViewShulkerGui(services, configs, this));
        guis.dynamicRenderer(ViewBidGui.ID, new ViewBidGui(services, configs, this, messenger));

        DraftGui create = new DraftGui(services, configs, this, messenger);
        guis.dynamicRenderer(DraftGui.AUCTION_ID, create);
        guis.dynamicRenderer(DraftGui.BID_ID, create);

        guis.dynamicRenderer(BuyAmountGui.ID, new BuyAmountGui(services, configs, this, messenger));
        guis.dynamicRenderer(PlaceBidGui.ID, new PlaceBidGui(services, configs, this, messenger));

        ConfirmGui confirm = new ConfirmGui(services, configs, this);
        List.of(
                ConfirmGui.BUY, ConfirmGui.BUY_SHULKER, ConfirmGui.CANCEL, ConfirmGui.DELETE,
                ReclaimService.ReclaimKind.CLAIM.confirmGuiId(),
                ReclaimService.ReclaimKind.COLLECT.confirmGuiId(),
                ReclaimService.ReclaimKind.CLAIM.confirmAllGuiId(),
                ReclaimService.ReclaimKind.COLLECT.confirmAllGuiId()
        ).forEach(id -> guis.dynamicRenderer(id, confirm));
    }

    private void registerBoth(BaseGui renderer, String selfId, String targetId) {
        guis.dynamicRenderer(selfId, renderer);
        guis.dynamicRenderer(targetId, renderer);
    }

    public boolean reload() {
        boolean coreOk = guis.reload();
        registry.reload();
        return coreOk;
    }

    public void shutdown() {
        guis.shutdown();
    }
}