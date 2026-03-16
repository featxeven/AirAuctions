package com.ftxeven.airauctions.core.manager.player;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import com.ftxeven.airauctions.util.TimeUtil;
import com.ftxeven.airauctions.core.gui.util.GuiItemFinder;
import com.ftxeven.airauctions.core.gui.util.GuiListingUtil;
import com.ftxeven.airauctions.core.gui.util.GuiSlotMapper;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class CancelListingManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();

    public CancelListingManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/player/cancel-listing.yml");
        if (!file.exists()) plugin.saveResource("guis/player/cancel-listing.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        List<Integer> slots = GuiDefinition.parseSlots(cfg.getStringList("auction-slot"));
        this.auctionSlotSet.clear();
        for (int s : slots) if (s >= 0) this.auctionSlotSet.set(s);

        ConfigurationSection auctionSec = cfg.getConfigurationSection("auction-item");
        this.auctionItemTemplate = auctionSec != null ? GuiItem.fromSection("auction-item", auctionSec) : null;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(cfg.getConfigurationSection("buttons"), items);
        loadItems(cfg.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(cfg.getString("title", "Confirm Cancellation"), cfg.getInt("rows", 3), items, cfg);
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String idStr = ph.get("listing-id");
        if (idStr == null) return null;

        int listingId = Integer.parseInt(idStr);
        AuctionListing listing = plugin.core().auctions().getActiveListing(listingId);
        if (listing == null) return null;

        CancelListingHolder holder = new CancelListingHolder(listing, ph, definition);

        Map<String, String> displayPh = holder.asContext();

        Map<String, String> visualPh = createVisualPlaceholders(listing, displayPh, ph);

        String title = PlaceholderUtil.apply(viewer, definition.title(), visualPh);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
        holder.setInventory(inv);

        GuiSlotMapper.fill(plugin, inv, definition, viewer, visualPh,
                Collections.singletonList(listing), auctionSlotSet, 0, auctionItemTemplate, null, Integer.MAX_VALUE);

        return inv;
    }

    private Map<String, String> createVisualPlaceholders(AuctionListing listing, Map<String, String> internalCtx, Map<String, String> rawIncoming) {
        Map<String, String> ph = new HashMap<>(internalCtx);

        ph.put("price", plugin.core().economy().formats().format(listing.price(), listing.currencyId()));
        ph.put("amount", String.valueOf(listing.item().getAmount()));
        ph.put("seller", plugin.database().records().getNameFromUuid(listing.sellerUuid()));
        ph.put("expire", TimeUtil.formatSeconds(plugin, Math.max(0, (listing.expiryAt() - System.currentTimeMillis()) / 1000L)));

        String sortId = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());
        ph.put("sort", plugin.config().getSortingDisplayName(sortId));

        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();
        ph.put("filter", plugin.filters().getDisplayName(filterId));

        ph.put("pages", rawIncoming.getOrDefault("pages", "1"));

        return ph;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof CancelListingHolder holder) || event.getCurrentItem() == null) return;

        GuiItem item = GuiItemFinder.find(holder.def(), event.getSlot(), viewer, holder.asContext(), null);
        if (item == null) return;

        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder.toPageable())) {
            executeLogic(item, viewer, holder);
        }
    }

    private void executeLogic(GuiItem item, Player viewer, CancelListingHolder holder) {
        List<String> actions = item.getActionsForClick(ClickType.LEFT, viewer, holder.asContext());
        boolean hasClose = actions != null && actions.stream().anyMatch(a -> a.equalsIgnoreCase("[close]"));

        if (item.key().equals("confirm")) {
            processFinalCancellation(viewer, holder.listing(), holder.asContext(), hasClose);
        } else if (item.key().equals("cancel") && !hasClose) {
            plugin.core().gui().open("player_listings", viewer, holder.asContext());
        }
    }

    private void processFinalCancellation(Player viewer, AuctionListing listing, Map<String, String> ctx, boolean hasClose) {
        PlayerListingsManager pm = plugin.core().gui().get("player_listings", PlayerListingsManager.class);
        boolean cancelCollect = pm != null && pm.getDefinition().config().getBoolean("cancel-collect", false);

        if (cancelCollect && !plugin.config().dropItemsWhenFull() && viewer.getInventory().firstEmpty() == -1) {
            MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.error.inventory-full"), Map.of());
            plugin.core().gui().open("player_listings", viewer, ctx);
            return;
        }

        if (plugin.core().auctions().cancelEntry(viewer, listing.id(), cancelCollect)) {
            if (cancelCollect) {
                plugin.core().auctions().finalizeCancellation(viewer, listing);
            } else {
                Map<String, String> ph = Map.of(
                        "amount", String.valueOf(listing.item().getAmount()),
                        "item", plugin.itemTranslations().getName(listing.item())
                );
                MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.moved-to-expired"), ph);
            }

            if (hasClose) viewer.closeInventory();
            else plugin.core().gui().open("player_listings", viewer, ctx);
        } else {
            MessageUtil.send(viewer, plugin.lang().get("errors.item-unavailable"), Map.of());
            plugin.core().gui().open("player_listings", viewer, ctx);
        }
    }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof CancelListingHolder; }

    public static final class CancelListingHolder implements InventoryHolder {
        private final AuctionListing listing;
        private final String page, filter, sort;
        private final GuiDefinition def;
        private Inventory inventory;

        public CancelListingHolder(AuctionListing listing, Map<String, String> ph, GuiDefinition def) {
            this.listing = listing;
            this.def = def;
            this.filter = ph.getOrDefault("filter", "all");
            this.sort = ph.getOrDefault("sort", "newest-date");
            this.page = ph.getOrDefault("page", "1");
        }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public AuctionListing listing() { return listing; }
        public GuiDefinition def() { return def; }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory != null ? inventory : Bukkit.createInventory(this, 9);
        }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("listing-id", String.valueOf(listing.id()));
            ctx.put("page", page);
            ctx.put("filter", filter);
            ctx.put("sort", sort);
            return ctx;
        }

        public PageableHolder toPageable() {
            return new PageableHolder() {
                @Override public int page() {
                    try { return Math.max(0, Integer.parseInt(page) - 1); }
                    catch (Exception e) { return 0; }
                }
                @Override public int totalPages() { return 1; }
                @Override public String filter() { return filter; }
                @Override public String sort() { return sort; }
                @Override public Map<Integer, Integer> displayedListings() { return Map.of(); }
                @Override public Map<String, String> asContext() { return CancelListingHolder.this.asContext(); }
                @Override public @NotNull Inventory getInventory() { return CancelListingHolder.this.getInventory(); }
            };
        }
    }
}