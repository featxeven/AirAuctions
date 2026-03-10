package com.ftxeven.airauctions.core.manager.player;

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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class PlayerListingsManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private List<Integer> auctionSlotsList;
    private GuiItem availableTemplate;
    public static final ThreadLocal<PlayerHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public PlayerListingsManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public GuiDefinition getDefinition() { return definition; }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/player/player-listings.yml");
        if (!file.exists()) plugin.saveResource("guis/player/player-listings.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection guiSection = cfg.getConfigurationSection("player-listings");
        if (guiSection == null) guiSection = cfg;

        this.auctionSlotsList = GuiDefinition.parseSlots(guiSection.getStringList("auction-slots"));
        this.auctionSlotSet.clear();
        for (int slot : auctionSlotsList) if (slot >= 0) this.auctionSlotSet.set(slot);

        this.auctionItemTemplate = GuiItem.fromSection("auction-item", Objects.requireNonNull(guiSection.getConfigurationSection("auction-item")));

        ConfigurationSection availSec = guiSection.getConfigurationSection("slot-available");
        this.availableTemplate = (availSec != null && availSec.getBoolean("enabled", true))
                ? GuiItem.fromSection("slot-available", availSec) : null;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(guiSection.getConfigurationSection("buttons"), items);
        loadItems(guiSection.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(guiSection.getString("title", "My Listings"), guiSection.getInt("rows", 6), items, guiSection);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();
        String sortId = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());

        if (!plugin.config().getSortingKeys().contains(sortId)) {
            sortId = plugin.config().getDefaultSortingKey();
        }

        List<AuctionListing> auctions = new ArrayList<>(plugin.core().auctions().getBySeller(viewer.getUniqueId()));
        if (!filterId.equals("all")) {
            auctions.removeIf(l -> !plugin.filters().getCategory(l.item()).equalsIgnoreCase(filterId));
        }

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

        int limit = plugin.config().getPlayerLimit(viewer);
        PlayerHolder holder = new PlayerHolder(pageIndex, totalPages, filterId, sortId, slotMap, limit);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> displayPh = holder.asContext();

            Map<String, String> titlePh = new HashMap<>(displayPh);
            titlePh.put("filter", plugin.filters().getDisplayName(filterId));
            titlePh.put("sort", plugin.config().getSortingDisplayName(sortId));
            titlePh.put("player", viewer.getName());

            String title = PlaceholderUtil.apply(viewer, definition.title(), titlePh);
            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh, auctions, auctionSlotSet, pageIndex, auctionItemTemplate, availableTemplate, limit);
            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    private void refreshWithValidation(Player viewer, PlayerHolder holder) {
        GuiListingUtil.refresh(
                plugin, viewer, holder, viewer.getOpenInventory().getTopInventory(), definition, auctionSlotsList,
                auctionItemTemplate, availableTemplate, holder.limit(),
                () -> {
                    List<AuctionListing> auctions = new ArrayList<>(plugin.core().auctions().getBySeller(viewer.getUniqueId()));
                    if (!holder.filter().equals("all")) {
                        auctions.removeIf(l -> !plugin.filters().getCategory(l.item()).equalsIgnoreCase(holder.filter()));
                    }
                    GuiListingUtil.applySorting(plugin, auctions, holder.sort());
                    return auctions;
                },
                (p, ctx) -> openMenu(p, holder.filter(), holder.page() + 1, holder.sort())
        );
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof PlayerHolder holder) || event.getCurrentItem() == null) return;

        if (auctionSlotSet.get(event.getSlot())) {
            handleAuctionClick(event, viewer, holder);
            return;
        }

        GuiItem item = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder)) {
            executeLogic(item, viewer, holder, event);
        }
    }

    private void executeLogic(GuiItem item, Player viewer, PlayerHolder holder, InventoryClickEvent event) {
        int humanPage = holder.page() + 1;

        switch (item.key()) {
            case "next-page" -> openMenu(viewer, holder.filter(), humanPage + 1, holder.sort());
            case "previous-page" -> openMenu(viewer, holder.filter(), humanPage - 1, holder.sort());
            case "refresh" -> refreshWithValidation(viewer, holder);
            case "filter-by" -> cycleFilter(viewer, holder, event.isLeftClick());
            case "sort-by" -> cycleSort(viewer, holder, event.isLeftClick());
        }
    }

    private void handleAuctionClick(InventoryClickEvent event, Player viewer, PlayerHolder holder) {
        Integer listingId = holder.displayedListings().get(event.getSlot());

        if (listingId == null) {
            int itemsPerPage = auctionSlotSet.cardinality();
            int slotIdx = auctionSlotsList.indexOf(event.getSlot());
            int absIdx = (holder.page() * itemsPerPage) + slotIdx;

            if (absIdx < holder.limit() && availableTemplate != null) {
                GuiListingUtil.handleItemAction(plugin, availableTemplate, viewer, event.getClick(), holder);
            }
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
        ctx.put("seller", viewer.getName());
        ctx.put("price", String.valueOf(listing.price()));

        List<String> actions = auctionItemTemplate.getActionsForClick(event.getClick(), viewer, ctx);

        if (actions != null && !actions.isEmpty()) {
            plugin.core().gui().action().executeAll(actions, viewer, ctx);
        }
    }

    public void processCancellation(Player viewer, AuctionListing listing) {
        boolean cancelCollect = definition.config().getBoolean("cancel-collect", false);

        if (plugin.core().auctions().cancelEntry(viewer, listing.id(), cancelCollect)) {
            Map<String, String> ph = Map.of(
                    "amount", String.valueOf(listing.item().getAmount()),
                    "item", plugin.itemTranslations().translate(listing.item().getType())
            );

            if (cancelCollect) {
                Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(listing.item().clone());
                if (!overflow.isEmpty()) {
                    overflow.values().forEach(i -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), i));
                    MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.inventory-full-dropped"), ph);
                } else {
                    MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.success"), ph);
                }
            } else {
                MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.moved-to-expired"), ph);
            }
        } else {
            MessageUtil.send(viewer, plugin.lang().get("errors.item-unavailable"), Map.of());
        }

        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof PlayerHolder holder) {
            refreshWithValidation(viewer, holder);
        }
    }

    private void cycleFilter(Player viewer, PlayerHolder holder, boolean fwd) {
        List<String> opts = plugin.filters().getOrderedCategoryKeys();
        String next = GuiListingUtil.cycle(opts, holder.filter(), fwd);

        if (plugin.config().forceUpdateGui()) {
            openMenu(viewer, next, 1, holder.sort());
        } else {
            holder.setFilter(next);
            holder.setPage(0);
            refreshWithValidation(viewer, holder);
        }
    }

    private void cycleSort(Player viewer, PlayerHolder holder, boolean fwd) {
        String next = fwd
                ? plugin.config().getNextSortingKey(holder.sort())
                : plugin.config().getPreviousSortingKey(holder.sort());

        if (plugin.config().forceUpdateGui()) {
            openMenu(viewer, holder.filter(), holder.page() + 1, next);
        } else {
            holder.setSort(next);
            refreshWithValidation(viewer, holder);
        }
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(key);
            if (sub != null) items.putIfAbsent(key, GuiItem.fromSection(key, sub));
        }
    }

    private void openMenu(Player viewer, String filter, int page, String sort) {
        plugin.core().gui().open("player_listings", viewer, Map.of("filter", filter, "page", String.valueOf(page), "sort", sort));
    }

    private int parseInt(String s) { try { return s != null ? Integer.parseInt(s) : 1; } catch (Exception e) { return 1; } }
    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof PlayerHolder; }

    public static final class PlayerHolder implements PageableHolder {
        private int page, totalPages;
        private String filter, sort;
        private final int limit;
        private final Map<Integer, Integer> displayedListings;
        private Inventory inventory;

        public PlayerHolder(int page, int totalPages, String filter, String sort, Map<Integer, Integer> displayedListings, int limit) {
            this.page = page;
            this.totalPages = totalPages;
            this.filter = filter;
            this.sort = sort;
            this.displayedListings = displayedListings;
            this.limit = limit;
        }

        public void setPage(int page) { this.page = page; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public void setFilter(String filter) { this.filter = filter; }
        public void setSort(String sort) { this.sort = sort; }

        @Override public int page() { return page; }
        @Override public int totalPages() { return totalPages; }
        @Override public String filter() { return filter; }
        @Override public String sort() { return sort; }
        @Override public Map<Integer, Integer> displayedListings() { return displayedListings; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        public int limit() { return limit; }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("filter", filter);
            ctx.put("sort", sort);
            ctx.put("page", String.valueOf(page + 1));
            ctx.put("pages", String.valueOf(totalPages));
            ctx.put("limit", String.valueOf(limit));
            return ctx;
        }
    }
}