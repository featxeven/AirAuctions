package com.ftxeven.airauctions.core.manager.main;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import com.ftxeven.airauctions.core.gui.util.GuiItemFinder;
import com.ftxeven.airauctions.core.gui.util.GuiListingUtil;
import com.ftxeven.airauctions.core.gui.util.GuiSlotMapper;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class AuctionSearchManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private List<Integer> auctionSlotsList;
    public static final ThreadLocal<SearchHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public AuctionSearchManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/auction-search.yml");
        if (!file.exists()) plugin.saveResource("guis/auction-search.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("auction-search");
        if (section == null) section = cfg;

        this.auctionSlotsList = GuiDefinition.parseSlots(section.getStringList("auction-slots"));
        this.auctionSlotSet.clear();
        for (int s : auctionSlotsList) if (s >= 0) this.auctionSlotSet.set(s);

        this.auctionItemTemplate = GuiItem.fromSection("auction-item", Objects.requireNonNull(section.getConfigurationSection("auction-item")));

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(section.getString("title", "Search: %query%"), section.getInt("rows", 6), items, section);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String query = ph.getOrDefault("query", "");
        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();

        String defaultSort = plugin.config().getDefaultSortingKey();
        String sortId = ph.getOrDefault("sort", defaultSort);

        if (!plugin.config().getSortingKeys().contains(sortId)) {
            sortId = defaultSort;
        }

        List<AuctionListing> auctions = new ArrayList<>(plugin.core().search().search(query, filterId));
        GuiListingUtil.applySorting(plugin, auctions, sortId);

        int itemsPerPage = auctionSlotSet.cardinality();
        int totalPages = Math.max(1, (int) Math.ceil((double) auctions.size() / itemsPerPage));
        int inputPage = parseInt(ph.getOrDefault("page", "1"));
        int pageIndex = Math.max(0, Math.min(inputPage - 1, totalPages - 1));

        Map<Integer, Integer> slotMap = new HashMap<>();
        int startIndex = pageIndex * itemsPerPage;
        for (int i = 0; i < auctionSlotsList.size() && (startIndex + i) < auctions.size(); i++) {
            slotMap.put(auctionSlotsList.get(i), auctions.get(startIndex + i).id());
        }

        SearchHolder holder = new SearchHolder(pageIndex, totalPages, filterId, sortId, query, slotMap);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> displayPh = holder.asContext();

            Map<String, String> titlePh = new HashMap<>(displayPh);
            titlePh.put("filter", plugin.filters().getDisplayName(filterId));
            titlePh.put("sort", plugin.config().getSortingDisplayName(sortId));
            titlePh.put("query", query);

            String title = PlaceholderUtil.apply(viewer, definition.title(), titlePh);
            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh, auctions, auctionSlotSet, pageIndex, auctionItemTemplate, null, Integer.MAX_VALUE);
            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof SearchHolder holder) || event.getCurrentItem() == null) return;

        if (auctionSlotSet.get(event.getSlot())) {
            handleAuctionClick(event, viewer, holder);
            return;
        }

        GuiItem item = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder)) {
            executeLogic(item, viewer, holder, event);
        }
    }

    private void executeLogic(GuiItem item, Player viewer, SearchHolder holder, InventoryClickEvent event) {
        int humanPage = holder.page() + 1;

        switch (item.key()) {
            case "next-page" -> openMenu(viewer, holder.query(), holder.filter(), humanPage + 1, holder.sort());
            case "previous-page" -> openMenu(viewer, holder.query(), holder.filter(), humanPage - 1, holder.sort());
            case "refresh" -> refreshWithValidation(viewer, holder);
            case "filter-by" -> cycleFilter(viewer, holder, event.isLeftClick());
            case "sort-by" -> cycleSort(viewer, holder, event.isLeftClick());
        }
    }

    private void handleAuctionClick(InventoryClickEvent event, Player viewer, SearchHolder holder) {
        Integer listingId = holder.displayedListings().get(event.getSlot());

        if (listingId == null) {
            GuiItem customItem = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
            if (customItem != null) GuiListingUtil.handleItemAction(plugin, customItem, viewer, event.getClick(), holder);
            return;
        }

        AuctionListing listing = plugin.core().auctions().getActiveListing(listingId);
        if (listing == null) {
            MessageUtil.send(viewer, plugin.lang().get("errors.item-unavailable"), Map.of());
            refreshWithValidation(viewer, holder);
            return;
        }

        Map<String, String> ctx = new HashMap<>(holder.asContext());
        ctx.put("listing-id", String.valueOf(listing.id()));

        List<String> actions = auctionItemTemplate.getActionsForClick(event.getClick(), viewer, ctx);
        if (actions != null && !actions.isEmpty()) {
            plugin.core().gui().action().executeAll(actions, viewer, ctx);
        }
    }

    private void refreshWithValidation(Player viewer, SearchHolder holder) {
        GuiListingUtil.refresh(plugin, viewer, holder, viewer.getOpenInventory().getTopInventory(), definition, auctionSlotsList, auctionItemTemplate,
                () -> {
                    List<AuctionListing> list = new ArrayList<>(plugin.core().search().search(holder.query(), holder.filter()));
                    GuiListingUtil.applySorting(plugin, list, holder.sort());
                    return list;
                },
                (p, ctx) -> openMenu(p, holder.query(), holder.filter(), holder.page() + 1, holder.sort())
        );
    }

    private void cycleFilter(Player viewer, SearchHolder holder, boolean fwd) {
        List<String> opts = plugin.filters().getOrderedCategoryKeys();
        String next = GuiListingUtil.cycle(opts, holder.filter(), fwd);

        if (plugin.config().forceUpdateGui()) {
            openMenu(viewer, holder.query(), next, 1, holder.sort());
        } else {
            holder.setFilter(next);
            holder.setPage(0);
            refreshWithValidation(viewer, holder);
        }
    }

    private void cycleSort(Player viewer, SearchHolder holder, boolean fwd) {
        String next = fwd
                ? plugin.config().getNextSortingKey(holder.sort())
                : plugin.config().getPreviousSortingKey(holder.sort());

        if (plugin.config().forceUpdateGui()) {
            openMenu(viewer, holder.query(), holder.filter(), holder.page() + 1, next);
        } else {
            holder.setSort(next);
            refreshWithValidation(viewer, holder);
        }
    }

    private void openMenu(Player viewer, String query, String filter, int page, String sort) {
        plugin.core().gui().open("auction_search", viewer, Map.of(
                "query", query,
                "filter", filter,
                "page", String.valueOf(page),
                "sort", sort
        ));
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    public GuiDefinition getDefinition() {
        return definition;
    }

    public boolean isInvalidPurchase(Player viewer, AuctionListing listing) {
        if (!plugin.config().purchaseOwn() && listing.sellerUuid().equals(viewer.getUniqueId())) {
            MessageUtil.send(viewer, plugin.lang().get("auctions.buy.error.cannot-purchase-own"), Map.of());
            return true;
        }
        if (!plugin.core().economy().canAfford(viewer, listing.currencyId(), listing.price())) {
            double missing = listing.price() - plugin.core().economy().getBalance(viewer, listing.currencyId());
            MessageUtil.send(viewer, plugin.lang().get("auctions.buy.error.insufficient-funds"), Map.of("amount", plugin.core().economy().formats().format(missing, listing.currencyId())));
            return true;
        }
        if (!plugin.config().dropItemsWhenFull() && viewer.getInventory().firstEmpty() == -1) {
            MessageUtil.send(viewer, plugin.lang().get("auctions.buy.error.inventory-full"), Map.of());
            return true;
        }
        return false;
    }

    public void processPurchase(Player viewer, AuctionListing listing) {
        com.ftxeven.airauctions.core.service.AuctionService.PurchaseResult result = plugin.core().auctions().buyEntry(viewer, listing.id());
        if (result == com.ftxeven.airauctions.core.service.AuctionService.PurchaseResult.SUCCESS) {
            plugin.core().auctions().finalizePurchase(viewer, listing);
        } else {
            handlePurchaseError(viewer, result, listing);
        }

        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof SearchHolder holder) {
            refreshWithValidation(viewer, holder);
        }
    }

    private void handlePurchaseError(Player viewer, com.ftxeven.airauctions.core.service.AuctionService.PurchaseResult result, AuctionListing listing) {
        switch (result) {
            case NOT_FOUND -> MessageUtil.send(viewer, plugin.lang().get("errors.item-unavailable"), Map.of());
            case OWN_ITEM -> MessageUtil.send(viewer, plugin.lang().get("auctions.buy.error.cannot-purchase-own"), Map.of());
            case CANNOT_AFFORD -> {
                double missing = listing.price() - plugin.core().economy().getBalance(viewer, listing.currencyId());
                MessageUtil.send(viewer, plugin.lang().get("auctions.buy.error.insufficient-funds"), Map.of("amount", plugin.core().economy().formats().format(missing, listing.currencyId())));
            }
            case INVENTORY_FULL -> MessageUtil.send(viewer, plugin.lang().get("auctions.buy.error.inventory-full"), Map.of());
        }
    }

    private int parseInt(String s) { try { return s != null ? Integer.parseInt(s) : 1; } catch (Exception e) { return 1; } }
    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof SearchHolder; }

    public static final class SearchHolder implements PageableHolder {
        private int page, totalPages;
        private String filter, sort;
        private final String query;
        private final Map<Integer, Integer> displayedListings;
        private Inventory inventory;

        public SearchHolder(int page, int totalPages, String filter, String sort, String query, Map<Integer, Integer> displayedListings) {
            this.page = page;
            this.totalPages = totalPages;
            this.filter = filter;
            this.sort = sort;
            this.query = query;
            this.displayedListings = displayedListings;
        }

        public void setPage(int page) { this.page = page; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public void setFilter(String filter) { this.filter = filter; }
        public void setSort(String sort) { this.sort = sort; }

        @Override public int page() { return page; }
        @Override public int totalPages() { return totalPages; }
        @Override public String filter() { return filter; }
        @Override public String sort() { return sort; }
        public String query() { return query; }
        @Override public Map<Integer, Integer> displayedListings() { return displayedListings; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("filter", filter);
            ctx.put("sort", sort);
            ctx.put("query", query);
            ctx.put("page", String.valueOf(page + 1));
            ctx.put("pages", String.valueOf(totalPages));
            return ctx;
        }
    }
}