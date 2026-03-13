package com.ftxeven.airauctions.core.manager.target;

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

public final class TargetListingsManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private List<Integer> auctionSlotsList;
    private GuiItem availableTemplate;
    public static final ThreadLocal<TargetHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public TargetListingsManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/target/target-listings.yml");
        if (!file.exists()) plugin.saveResource("guis/target/target-listings.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("target-listings");
        if (section == null) section = cfg;

        this.auctionSlotsList = GuiDefinition.parseSlots(section.getStringList("auction-slots"));
        this.auctionSlotSet.clear();
        for (int slot : auctionSlotsList) if (slot >= 0) this.auctionSlotSet.set(slot);

        this.auctionItemTemplate = GuiItem.fromSection("auction-item", Objects.requireNonNull(section.getConfigurationSection("auction-item")));

        ConfigurationSection availSec = section.getConfigurationSection("slot-available");
        this.availableTemplate = (availSec != null && availSec.getBoolean("enabled", true))
                ? GuiItem.fromSection("slot-available", availSec) : null;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);
        this.definition = new GuiDefinition(section.getString("title", "Listings"), section.getInt("rows", 6), items, section);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String inputName = ph.get("target");
        UUID targetUuid = plugin.database().records().uuidFromName(inputName);
        if (targetUuid == null) return null;

        String realTargetName = plugin.database().records().getNameFromUuid(targetUuid);
        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();

        String defaultSort = plugin.config().getDefaultSortingKey();
        String sortId = ph.getOrDefault("sort", defaultSort);

        if (!plugin.config().getSortingKeys().contains(sortId)) {
            sortId = defaultSort;
        }

        List<AuctionListing> auctions = new ArrayList<>(plugin.core().auctions().getActive(filterId));
        auctions.removeIf(l -> !l.sellerUuid().equals(targetUuid));
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

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        int targetLimit = (onlineTarget != null) ? plugin.config().getPlayerLimit(onlineTarget) : plugin.config().getDefaultLimit();

        TargetHolder holder = new TargetHolder(realTargetName, targetUuid, pageIndex, totalPages, filterId, sortId, slotMap, targetLimit);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> displayPh = holder.asContext();

            Map<String, String> titlePh = new HashMap<>(displayPh);
            titlePh.put("filter", plugin.filters().getDisplayName(filterId));
            titlePh.put("sort", plugin.config().getSortingDisplayName(sortId));
            titlePh.put("player", viewer.getName());
            titlePh.put("target", realTargetName);

            String title = PlaceholderUtil.apply(viewer, definition.title(), titlePh);
            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh, auctions, auctionSlotSet, pageIndex, auctionItemTemplate, availableTemplate, targetLimit);
            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    private void refreshWithValidation(Player viewer, TargetHolder holder) {
        GuiListingUtil.refresh(
                plugin, viewer, holder, viewer.getOpenInventory().getTopInventory(), definition, auctionSlotsList,
                auctionItemTemplate, availableTemplate, holder.limit(),
                () -> {
                    List<AuctionListing> auctions = new ArrayList<>(plugin.core().auctions().getActive(holder.filter()));
                    auctions.removeIf(l -> !l.sellerUuid().equals(holder.targetUuid()));
                    GuiListingUtil.applySorting(plugin, auctions, holder.sort());
                    return auctions;
                },
                (p, ctx) -> openMenu(p, holder.targetName(), holder.filter(), holder.page() + 1, holder.sort())
        );
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof TargetHolder holder) || event.getCurrentItem() == null) return;

        if (auctionSlotSet.get(event.getSlot())) {
            handleAuctionClick(event, viewer, holder);
            return;
        }

        GuiItem item = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder)) {
            executeLogic(item, viewer, holder, event);
        }
    }

    private void executeLogic(GuiItem item, Player viewer, TargetHolder holder, InventoryClickEvent event) {
        int humanPage = holder.page() + 1;

        switch (item.key()) {
            case "next-page" -> openMenu(viewer, holder.targetName(), holder.filter(), humanPage + 1, holder.sort());
            case "previous-page" -> openMenu(viewer, holder.targetName(), holder.filter(), humanPage - 1, holder.sort());
            case "refresh" -> refreshWithValidation(viewer, holder);
            case "filter-by" -> cycleFilter(viewer, holder, event.isLeftClick());
            case "sort-by" -> cycleSort(viewer, holder, event.isLeftClick());
        }
    }

    private void handleAuctionClick(InventoryClickEvent event, Player viewer, TargetHolder holder) {
        Integer listingId = holder.displayedListings().get(event.getSlot());

        if (listingId == null) {
            int itemsPerPage = auctionSlotSet.cardinality();
            int slotIdx = auctionSlotsList.indexOf(event.getSlot());
            int absIdx = (holder.page() * itemsPerPage) + slotIdx;

            if (absIdx < holder.limit() && availableTemplate != null) {
                GuiListingUtil.handleItemAction(plugin, availableTemplate, viewer, event.getClick(), holder);
            } else {
                GuiItem custom = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
                if (custom != null) GuiListingUtil.handleItemAction(plugin, custom, viewer, event.getClick(), holder);
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
        ctx.put("seller", holder.targetName());
        ctx.put("price", String.valueOf(listing.price()));

        List<String> actions = auctionItemTemplate.getActionsForClick(event.getClick(), viewer, ctx);

        if (actions != null && !actions.isEmpty()) {
            plugin.core().gui().action().executeAll(actions, viewer, ctx);
        }
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    private void openMenu(Player viewer, String target, String filter, int page, String sort) {
        plugin.core().gui().open("target_listings", viewer, Map.of("target", target, "filter", filter, "page", String.valueOf(page), "sort", sort));
    }

    private void cycleFilter(Player viewer, TargetHolder holder, boolean fwd) {
        List<String> opts = plugin.filters().getOrderedCategoryKeys();
        String next = GuiListingUtil.cycle(opts, holder.filter(), fwd);

        if (plugin.config().forceUpdateGui()) {
            openMenu(viewer, holder.targetName(), next, 1, holder.sort());
        } else {
            holder.setFilter(next);
            holder.setPage(0);
            refreshWithValidation(viewer, holder);
        }
    }

    private void cycleSort(Player viewer, TargetHolder holder, boolean fwd) {
        String next = fwd
                ? plugin.config().getNextSortingKey(holder.sort())
                : plugin.config().getPreviousSortingKey(holder.sort());

        if (plugin.config().forceUpdateGui()) {
            openMenu(viewer, holder.targetName(), holder.filter(), holder.page() + 1, next);
        } else {
            holder.setSort(next);
            refreshWithValidation(viewer, holder);
        }
    }

    private int parseInt(String s) { try { return s != null ? Integer.parseInt(s) : 1; } catch (Exception e) { return 1; } }
    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof TargetHolder; }

    public static final class TargetHolder implements PageableHolder {
        private final String targetName;
        private final UUID targetUuid;
        private int page, totalPages;
        private final int limit;
        private String filter, sort;
        private final Map<Integer, Integer> displayedListings;
        private Inventory inventory;

        public TargetHolder(String targetName, UUID targetUuid, int page, int totalPages, String filter, String sort, Map<Integer, Integer> displayedListings, int limit) {
            this.targetName = targetName;
            this.targetUuid = targetUuid;
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

        public String targetName() { return targetName; }
        public UUID targetUuid() { return targetUuid; }
        public int limit() { return limit; }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("target", targetName);
            ctx.put("filter", filter);
            ctx.put("sort", sort);
            ctx.put("page", String.valueOf(page + 1));
            ctx.put("pages", String.valueOf(totalPages));
            return ctx;
        }
    }

    public GuiDefinition getDefinition() { return definition; }
}