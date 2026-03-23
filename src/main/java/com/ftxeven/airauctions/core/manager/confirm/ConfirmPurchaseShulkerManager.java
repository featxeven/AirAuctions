package com.ftxeven.airauctions.core.manager.confirm;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.manager.main.AuctionHouseManager;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import com.ftxeven.airauctions.core.gui.util.GuiItemFinder;
import com.ftxeven.airauctions.core.gui.util.GuiListingUtil;
import com.ftxeven.airauctions.core.gui.util.GuiSlotMapper;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class ConfirmPurchaseShulkerManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration config;

    public ConfirmPurchaseShulkerManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/confirm/confirm-purchase-shulker.yml");
        if (!file.exists()) plugin.saveResource("guis/confirm/confirm-purchase-shulker.yml", false);
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration getConfig() {
        return this.config;
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        String idStr = ph.get("listing-id");
        if (idStr == null) return null;

        int listingId = Integer.parseInt(idStr);
        AuctionListing listing = plugin.core().auctions().getActiveListing(listingId);
        if (listing == null) return null;

        String source = getSource(viewer, ph);
        String layerKey = source.replace("_", "-");
        ConfigurationSection section = config.getConfigurationSection(layerKey);
        if (section == null) section = config;

        BitSet auctionSlotSet = new BitSet();
        List<Integer> slots = GuiDefinition.parseSlots(section.getStringList("auction-slot"));
        for (int s : slots) if (s >= 0) auctionSlotSet.set(s);

        List<Integer> previewSlots = GuiDefinition.parseSlots(section.getStringList("preview-slots"));
        GuiItem template = GuiItem.fromSection("auction-item", Objects.requireNonNull(section.getConfigurationSection("auction-item")));

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        GuiDefinition def = new GuiDefinition(section.getString("title", "Confirm Shulker Purchase"), section.getInt("rows", 6), items, section);

        ConfirmShulkerHolder holder = new ConfirmShulkerHolder(listing, ph, def, source);
        Map<String, String> displayPh = createDisplayPlaceholders(listing, holder.asContext());

        String title = PlaceholderUtil.apply(viewer, def.title(), displayPh);
        Inventory inv = Bukkit.createInventory(holder, def.rows() * 9, mm.deserialize("<!italic>" + title));
        holder.setInventory(inv);

        GuiSlotMapper.fill(plugin, inv, def, viewer, displayPh, Collections.singletonList(listing), auctionSlotSet, 0, template, null, Integer.MAX_VALUE);
        fillShulkerContents(inv, listing.item(), previewSlots);

        return inv;
    }

    private String getSource(Player viewer, Map<String, String> ph) {
        if (ph.containsKey("query")) return "auction_search";

        Inventory top = viewer.getOpenInventory().getTopInventory();

        if (plugin.core().gui().get("target_listings").owns(top)) return "target_listings";
        if (plugin.core().gui().get("auction_search").owns(top)) return "auction_search";
        if (top.getHolder() instanceof ConfirmShulkerHolder prev) return prev.sourceGui();

        return "auction_house";
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    private void fillShulkerContents(Inventory inv, ItemStack shulker, List<Integer> slots) {
        if (shulker.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            ItemStack[] contents = box.getInventory().getContents();
            for (int i = 0; i < slots.size() && i < contents.length; i++) {
                if (contents[i] != null) inv.setItem(slots.get(i), contents[i]);
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof ConfirmShulkerHolder holder) || event.getCurrentItem() == null) return;

        GuiItem item = GuiItemFinder.find(holder.def(), event.getSlot(), viewer, holder.asContext(), null);
        if (item == null) return;

        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder.toPageable())) {
            if (item.key().equals("confirm")) {
                AuctionHouseManager am = plugin.core().gui().get("auction_house", AuctionHouseManager.class);
                if (am != null) am.processPurchase(viewer, holder.listing());
                plugin.core().gui().open(holder.sourceGui(), viewer, holder.asContext());
            } else if (item.key().equals("cancel")) {
                plugin.core().gui().open(holder.sourceGui(), viewer, holder.asContext());
            }
        }
    }

    private Map<String, String> createDisplayPlaceholders(AuctionListing listing, Map<String, String> ph) {
        double price = listing.price();
        String currencyId = listing.currencyId();

        double taxAmount = plugin.core().economy().calculateSalesTax(
                listing.sellerUuid(),
                price,
                plugin.economy().getProvider(currencyId)
        );
        double profit = Math.max(0, price - taxAmount);

        ph.put("price", plugin.core().economy().formats().format(price, currencyId));
        ph.put("tax", plugin.core().economy().formats().format(taxAmount, currencyId));
        ph.put("profit", plugin.core().economy().formats().format(profit, currencyId));
        ph.put("seller", plugin.database().records().getNameFromUuid(listing.sellerUuid()));
        ph.put("target", ph.getOrDefault("target", "Unknown"));
        ph.put("query", ph.getOrDefault("query", ""));

        String filterId = ph.getOrDefault("filter", plugin.filters().getDefaultFilter()).toLowerCase();
        ph.put("filter", plugin.filters().getDisplayName(filterId));

        String sortId = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());
        ph.put("sort", plugin.config().getSortingDisplayName(sortId));

        return ph;
    }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof ConfirmShulkerHolder; }

    public static final class ConfirmShulkerHolder implements InventoryHolder {
        private final AuctionListing listing;
        private final GuiDefinition def;
        private final String sourceGui;
        private final String page, filter, sort, target, totalPages, query;
        private Inventory inventory;

        public ConfirmShulkerHolder(AuctionListing listing, Map<String, String> ph, GuiDefinition def, String sourceGui) {
            this.listing = listing;
            this.def = def;
            this.sourceGui = sourceGui;
            this.filter = ph.getOrDefault("filter", "all");
            this.sort = ph.getOrDefault("sort", "newest-date");
            this.target = ph.get("target");
            this.query = ph.get("query");
            this.totalPages = ph.getOrDefault("pages", "1");
            this.page = ph.getOrDefault("page", "1");
        }

        public void setInventory(Inventory inv) { this.inventory = inv; }
        public AuctionListing listing() { return listing; }
        public GuiDefinition def() { return def; }
        public String sourceGui() { return sourceGui; }

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
            ctx.put("pages", totalPages);
            if (target != null) ctx.put("target", target);
            if (query != null) ctx.put("query", query);
            return ctx;
        }

        public PageableHolder toPageable() {
            return new PageableHolder() {
                @Override public int page() {
                    try { return Math.max(0, Integer.parseInt(page) - 1); } catch (Exception e) { return 0; }
                }
                @Override public int totalPages() {
                    try { return Integer.parseInt(totalPages); } catch (Exception e) { return 1; }
                }
                @Override public String filter() { return filter; }
                @Override public String sort() { return sort; }
                @Override public Map<Integer, Integer> displayedListings() { return Map.of(); }
                @Override public Map<String, String> asContext() { return ConfirmShulkerHolder.this.asContext(); }
                @Override public @NotNull Inventory getInventory() { return ConfirmShulkerHolder.this.getInventory(); }
            };
        }
    }
}