package com.ftxeven.airauctions.core.manager.player;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.model.AuctionHistory;
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

public final class ListingHistoryManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem itemBoughtTemplate;
    private GuiItem itemSoldTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private List<Integer> auctionSlotsList;
    public static final ThreadLocal<HistoryHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public ListingHistoryManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/player/player-history.yml");
        if (!file.exists()) plugin.saveResource("guis/player/player-history.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("listing-history");
        if (section == null) section = cfg;

        this.auctionSlotsList = GuiDefinition.parseSlots(section.getStringList("auction-slots"));
        this.auctionSlotSet.clear();
        for (int s : auctionSlotsList) if (s >= 0) this.auctionSlotSet.set(s);

        this.itemBoughtTemplate = GuiItem.fromSection("auction-item-bought", Objects.requireNonNull(section.getConfigurationSection("auction-item-bought")));
        this.itemSoldTemplate = GuiItem.fromSection("auction-item-sold", Objects.requireNonNull(section.getConfigurationSection("auction-item-sold")));

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(section.getString("title", "Sales History"), section.getInt("rows", 6), items, section);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();
        String sortId = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());

        List<AuctionHistory> history = plugin.database().history().getHistorySync(viewer.getUniqueId(), plugin.config().historyLimit());

        int itemsPerPage = auctionSlotSet.cardinality();
        int totalPages = Math.max(1, (int) Math.ceil((double) history.size() / itemsPerPage));
        int inputPage = parseInt(ph.getOrDefault("page", "1"));
        int page = Math.max(0, Math.min(inputPage - 1, totalPages - 1));

        HistoryHolder holder = new HistoryHolder(page, totalPages, filterId, sortId, history);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> displayPh = holder.asContext();
            Map<String, String> visualPh = new HashMap<>(displayPh);
            visualPh.put("filter", plugin.filters().getDisplayName(filterId));
            visualPh.put("sort", plugin.config().getSortingDisplayName(sortId));

            String title = PlaceholderUtil.apply(viewer, definition.title(), visualPh);
            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh, Collections.emptyList(), auctionSlotSet, page, null, null, 0);

            int startIndex = page * itemsPerPage;
            for (int i = 0; i < auctionSlotsList.size() && (startIndex + i) < history.size(); i++) {
                AuctionHistory log = history.get(startIndex + i);
                int slot = auctionSlotsList.get(i);

                boolean isSold = log.sellerUuid().equals(viewer.getUniqueId());
                GuiItem template = isSold ? itemSoldTemplate : itemBoughtTemplate;

                ItemStack historyStack = GuiSlotMapper.buildHistoryStack(plugin, viewer, log, displayPh, template);
                inv.setItem(slot, historyStack);
            }
            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof HistoryHolder holder) || event.getCurrentItem() == null) return;

        if (auctionSlotSet.get(event.getSlot())) {
            handleHistoryClick(event, viewer, holder);
            return;
        }

        GuiItem item = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);
        if (item == null) return;

        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder)) {
            executeLogic(item, viewer, holder);
        }
    }

    private void handleHistoryClick(InventoryClickEvent event, Player viewer, HistoryHolder holder) {
        int slotIndex = auctionSlotsList.indexOf(event.getSlot());
        if (slotIndex == -1) return;

        int itemsPerPage = auctionSlotSet.cardinality();
        int historyIndex = (holder.page() * itemsPerPage) + slotIndex;

        List<AuctionHistory> history = holder.getCachedHistory();
        if (historyIndex < 0 || historyIndex >= history.size()) return;

        AuctionHistory log = history.get(historyIndex);

        Map<String, String> ctx = new HashMap<>(holder.asContext());
        ctx.put("listing-id", String.valueOf(log.id()));
        ctx.put("price", String.valueOf(log.price()));
        ctx.put("seller", log.sellerUuid().toString());
        ctx.put("buyer", log.buyerUuid().toString());

        boolean isSold = log.sellerUuid().equals(viewer.getUniqueId());
        GuiItem template = isSold ? itemSoldTemplate : itemBoughtTemplate;

        List<String> actions = template.getActionsForClick(event.getClick(), viewer, ctx);

        if (actions != null && !actions.isEmpty()) {
            plugin.core().gui().action().executeAll(actions, viewer, ctx);
        }
    }

    private void executeLogic(GuiItem item, Player viewer, HistoryHolder holder) {
        int humanPage = holder.page() + 1;
        switch (item.key()) {
            case "next-page" -> openMenu(viewer, holder.filter(), humanPage + 1, holder.sort());
            case "previous-page" -> openMenu(viewer, holder.filter(), humanPage - 1, holder.sort());
            case "refresh" -> openMenu(viewer, holder.filter(), humanPage, holder.sort());
        }
    }

    private void openMenu(Player viewer, String filter, int page, String sort) {
        plugin.core().gui().open("player_history", viewer, Map.of(
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

    private int parseInt(String s) {
        try { return s != null ? Integer.parseInt(s) : 1; } catch (Exception e) { return 1; }
    }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof HistoryHolder; }

    public static final class HistoryHolder implements PageableHolder {
        private int page, totalPages;
        private final String filter, sort;
        private final List<AuctionHistory> cachedHistory;
        private Inventory inventory;

        public HistoryHolder(int page, int totalPages, String filter, String sort, List<AuctionHistory> history) {
            this.page = page;
            this.totalPages = totalPages;
            this.filter = filter;
            this.sort = sort;
            this.cachedHistory = history;
        }

        public void setPage(int page) { this.page = page; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

        @Override public int page() { return page; }
        @Override public int totalPages() { return totalPages; }
        @Override public String filter() { return filter; }
        @Override public String sort() { return sort; }
        @Override public Map<Integer, Integer> displayedListings() { return Map.of(); }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public List<AuctionHistory> getCachedHistory() { return cachedHistory; }

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