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

public final class ExpiredListingsManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private List<Integer> auctionSlotsList;
    public static final ThreadLocal<ExpiredHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public ExpiredListingsManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/player/player-expired.yml");
        if (!file.exists()) plugin.saveResource("guis/player/player-expired.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("expired-listings");
        if (section == null) section = cfg;

        this.auctionSlotsList = GuiDefinition.parseSlots(section.getStringList("auction-slots"));
        this.auctionSlotSet.clear();
        for (int s : auctionSlotsList) if (s >= 0) this.auctionSlotSet.set(s);

        ConfigurationSection itemSec = section.getConfigurationSection("auction-item");
        this.auctionItemTemplate = itemSec != null ? GuiItem.fromSection("auction-item", itemSec) : null;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(section.getString("title", "Expired Auctions"), section.getInt("rows", 6), items, section);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();
        String sortId = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());

        int purgeSeconds = plugin.config().purgeTime();
        if (purgeSeconds > 0) {
            plugin.database().listings().deletePurgedSync(viewer.getUniqueId(), purgeSeconds);
        }

        List<AuctionListing> expired = plugin.core().auctions().getExpiredBySeller(viewer.getUniqueId());

        int itemsPerPage = auctionSlotSet.cardinality();
        int totalPages = Math.max(1, (int) Math.ceil((double) expired.size() / itemsPerPage));
        int inputPage = parseInt(ph.getOrDefault("page", "1"));
        int page = Math.max(0, Math.min(inputPage - 1, totalPages - 1));

        Map<Integer, Integer> slotMap = new HashMap<>();
        int startIndex = page * itemsPerPage;
        for (int i = 0; i < auctionSlotsList.size() && (startIndex + i) < expired.size(); i++) {
            slotMap.put(auctionSlotsList.get(i), expired.get(startIndex + i).id());
        }

        ExpiredHolder holder = new ExpiredHolder(page, totalPages, filterId, sortId, slotMap);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> displayPh = holder.asContext();

            Map<String, String> visualPh = new HashMap<>(displayPh);
            visualPh.put("filter", plugin.filters().getDisplayName(filterId));
            visualPh.put("sort", plugin.config().getSortingDisplayName(sortId));

            String title = PlaceholderUtil.apply(viewer, definition.title(), visualPh);
            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            GuiSlotMapper.fill(plugin, inv, definition, viewer, visualPh, expired, auctionSlotSet, page, auctionItemTemplate, null, expired.size());

            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof ExpiredHolder holder) || event.getCurrentItem() == null) return;

        if (auctionSlotSet.get(event.getSlot())) {
            handleExpiredClick(event, viewer, holder);
            return;
        }

        GuiItem item = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
        if (item == null) return;

        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder)) {
            executeLogic(item, viewer, holder);
        }
    }

    private void handleExpiredClick(InventoryClickEvent event, Player viewer, ExpiredHolder holder) {
        Integer listingId = holder.displayedListings().get(event.getSlot());
        if (listingId == null) return;

        AuctionListing listing = plugin.core().auctions().getExpiredListing(listingId);
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

    public void processCollection(Player viewer, AuctionListing listing) {
        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof ExpiredHolder holder) {
            processCollection(viewer, listing, holder);
        } else {
            handleCollectionLogic(viewer, listing, null);
        }
    }

    public void processCollection(Player viewer, AuctionListing listing, ExpiredHolder holder) {
        handleCollectionLogic(viewer, listing, holder);
    }

    private void handleCollectionLogic(Player viewer, AuctionListing listing, ExpiredHolder holder) {
        boolean dropEnabled = plugin.config().dropItemsWhenFull();
        boolean invFull = viewer.getInventory().firstEmpty() == -1;

        if (invFull && !dropEnabled) {
            MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.error.inventory-full"), Map.of());
            return;
        }

        if (plugin.core().auctions().collectExpired(viewer, listing.id())) {
            Map<String, String> ph = Map.of(
                    "item", plugin.itemTranslations().getName(listing.item()),
                    "amount", String.valueOf(listing.item().getAmount())
            );

            MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.success"), ph);

            if (invFull) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), listing.item().clone());
                MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.inventory-full-dropped"), ph);
            } else {
                viewer.getInventory().addItem(listing.item());
            }

            if (holder != null) {
                refreshWithValidation(viewer, holder);
            }
        }
    }

    private void executeLogic(GuiItem item, Player viewer, ExpiredHolder holder) {
        int humanPage = holder.page() + 1;
        switch (item.key()) {
            case "next-page" -> openMenu(viewer, holder.filter(), humanPage + 1, holder.sort());
            case "previous-page" -> openMenu(viewer, holder.filter(), humanPage - 1, holder.sort());
            case "refresh" -> plugin.scheduler().runTask(() -> refreshWithValidation(viewer, holder));
            case "collect-all" -> handleCollectAll(viewer, holder);
        }
    }

    private void refreshWithValidation(Player viewer, ExpiredHolder holder) {
        GuiListingUtil.refresh(
                plugin, viewer, holder, viewer.getOpenInventory().getTopInventory(), definition, auctionSlotsList, auctionItemTemplate,
                () -> {
                    plugin.core().auctions().checkExpirations(viewer.getUniqueId());
                    return plugin.core().auctions().getExpiredBySeller(viewer.getUniqueId());
                },
                (p, ctx) -> openMenu(p, holder.filter(), Integer.parseInt(ctx.getOrDefault("page", "1")), holder.sort())
        );
    }

    private void handleCollectAll(Player viewer, ExpiredHolder holder) {
        List<AuctionListing> expired = plugin.core().auctions().getExpiredBySeller(viewer.getUniqueId());

        if (expired.isEmpty()) {
            MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.error.nothing-to-reclaim"), Map.of());
            return;
        }

        boolean dropEnabled = plugin.config().dropItemsWhenFull();

        if (!dropEnabled) {
            int emptySlots = 0;
            for (ItemStack is : viewer.getInventory().getStorageContents()) {
                if (is == null || is.getType().isAir()) emptySlots++;
            }

            if (expired.size() > emptySlots) {
                MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.error.inventory-full-all"), Map.of());
                return;
            }
        }

        int collectedCount = 0;
        boolean droppedAny = false;

        for (AuctionListing listing : expired) {
            if (plugin.core().auctions().collectExpired(viewer, listing.id())) {
                collectedCount++;

                Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(listing.item());

                if (!overflow.isEmpty()) {
                    if (dropEnabled) {
                        overflow.values().forEach(item ->
                                viewer.getWorld().dropItemNaturally(viewer.getLocation(), item));
                    } else {
                        overflow.values().forEach(item ->
                                viewer.getWorld().dropItemNaturally(viewer.getLocation(), item));
                    }
                    droppedAny = true;
                }
            }
        }

        if (collectedCount > 0) {
            MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.success-all"),
                    Map.of("total", String.valueOf(collectedCount)));

            if (droppedAny) {
                MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.inventory-full-dropped-all"), Map.of());
            }

            refreshWithValidation(viewer, holder);
        }
    }

    private void openMenu(Player viewer, String filter, int page, String sort) {
        plugin.core().gui().open("player_expired", viewer, Map.of("filter", filter, "page", String.valueOf(page), "sort", sort));
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(key);
            if (sub != null) items.putIfAbsent(key, GuiItem.fromSection(key, sub));
        }
    }

    private int parseInt(String s) { try { return s != null ? Integer.parseInt(s) : 1; } catch (Exception e) { return 1; } }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof ExpiredHolder; }

    public static final class ExpiredHolder implements PageableHolder {
        private int page, totalPages;
        private final String filter, sort;
        private final Map<Integer, Integer> displayedListings;
        private Inventory inventory;

        public ExpiredHolder(int page, int totalPages, String filter, String sort, Map<Integer, Integer> displayedListings) {
            this.page = page;
            this.totalPages = totalPages;
            this.filter = filter;
            this.sort = sort;
            this.displayedListings = displayedListings;
        }

        public void setPage(int page) { this.page = page; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

        @Override public int page() { return page; }
        @Override public int totalPages() { return totalPages; }
        @Override public String filter() { return filter; }
        @Override public String sort() { return sort; }
        @Override public Map<Integer, Integer> displayedListings() { return displayedListings; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("filter", filter);
            ctx.put("sort", sort);
            ctx.put("page", String.valueOf(page + 1));
            ctx.put("pages", String.valueOf(totalPages));
            return ctx;
        }
    }
}