package com.ftxeven.airauctions.core.manager.main;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.core.model.AuctionHistory;
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

public final class PreviewShulkerManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration config;

    public PreviewShulkerManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/preview-shulker.yml");
        if (!file.exists()) plugin.saveResource("guis/preview-shulker.yml", false);
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        int listingId = Integer.parseInt(ph.get("listing-id"));

        AuctionListing listing = plugin.core().auctions().getActiveListing(listingId);
        AuctionHistory history = (listing == null) ? plugin.database().history().getHistoryByIdSync(listingId) : null;

        if (listing == null && history == null) return null;

        ItemStack previewItem = (listing != null) ? listing.item() : history.item();
        String source = detectSource(viewer, ph);
        String layerKey = source.replace("_", "-");

        ConfigurationSection section = config.getConfigurationSection(layerKey);
        if (section == null) section = config;

        BitSet reservedSlots = new BitSet();
        List<Integer> auctionSlots = GuiDefinition.parseSlots(section.getStringList("auction-slot"));
        List<Integer> previewSlots = GuiDefinition.parseSlots(section.getStringList("preview-slots"));

        for (int s : auctionSlots) if (s >= 0) reservedSlots.set(s);
        for (int s : previewSlots) if (s >= 0) reservedSlots.set(s);

        ConfigurationSection templateSec;
        if ((source.equals("player_history") || source.equals("target_history")) && history != null) {
            UUID ownerUuid;
            if (source.equals("player_history")) {
                ownerUuid = viewer.getUniqueId();
            } else {
                String uuidStr = ph.get("target_uuid");
                ownerUuid = (uuidStr != null) ? UUID.fromString(uuidStr) : null;
            }

            boolean isSold = history.sellerUuid().equals(ownerUuid);
            String typeKey = isSold ? "auction-item-sold" : "auction-item-bought";
            templateSec = section.getConfigurationSection(typeKey);
        } else {
            templateSec = section.getConfigurationSection("auction-item");
        }

        GuiItem template = templateSec != null ? GuiItem.fromSection("template", templateSec) : null;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        GuiDefinition def = new GuiDefinition(section.getString("title", "Shulker Preview"), section.getInt("rows", 6), items, section);

        PreviewShulkerHolder holder = new PreviewShulkerHolder(new HashMap<>(ph), def, source);
        Map<String, String> displayPh = createDisplayPlaceholders(listing, history, holder.asContext());

        String title = PlaceholderUtil.apply(viewer, def.title(), displayPh);
        Inventory inv = Bukkit.createInventory(holder, def.rows() * 9, mm.deserialize("<!italic>" + title));
        holder.setInventory(inv);

        if (!auctionSlots.isEmpty() && template != null) {
            if (listing != null) {
                GuiSlotMapper.fill(plugin,
                        inv,
                        def,
                        viewer,
                        displayPh,
                        Collections.singletonList(listing),
                        reservedSlots,
                        0,
                        template,
                        null,
                        1);
            } else {
                GuiSlotMapper.fill(plugin,
                        inv,
                        def,
                        viewer,
                        displayPh,
                        Collections.emptyList(),
                        reservedSlots,
                        0,
                        null,
                        null,
                        0);
                int slot = auctionSlots.getFirst();
                ItemStack historyIcon = GuiSlotMapper.buildHistoryStack(plugin, viewer, history, displayPh, template);
                inv.setItem(slot, historyIcon);
            }
        } else {
            GuiSlotMapper.fill(plugin, inv, def, viewer, displayPh, new ArrayList<>(), reservedSlots, 0, null, null, 0);
        }

        if (!previewSlots.isEmpty()) fillShulkerContents(inv, previewItem, previewSlots);

        return inv;
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof PreviewShulkerHolder holder) || event.getCurrentItem() == null) return;

        GuiItem item = GuiItemFinder.find(holder.def(), event.getSlot(), viewer, holder.asContext(), null);
        if (item == null) return;

        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder.toPageable())) {
            if (item.key().equals("back")) {
                plugin.core().gui().open(holder.sourceGui(), viewer, holder.asContext());
            }
        }
    }

    private String detectSource(Player viewer, Map<String, String> ph) {
        if (ph.containsKey("query")) return "auction_search";

        Inventory top = viewer.getOpenInventory().getTopInventory();

        if (plugin.core().gui().get("player_history").owns(top)) return "player_history";
        if (plugin.core().gui().get("target_history").owns(top)) return "target_history";
        if (plugin.core().gui().get("auction_search").owns(top)) return "auction_search";
        if (plugin.core().gui().get("player_listings").owns(top)) return "player_listings";
        if (plugin.core().gui().get("target_listings").owns(top)) return "target_listings";
        if (top.getHolder() instanceof PreviewShulkerHolder prev) return prev.sourceGui();

        return "auction_house";
    }

    private void fillShulkerContents(Inventory inv, ItemStack shulker, List<Integer> slots) {
        if (shulker.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            ItemStack[] contents = box.getInventory().getContents();
            for (int i = 0; i < slots.size() && i < contents.length; i++) {
                if (contents[i] != null) inv.setItem(slots.get(i), contents[i]);
            }
        }
    }

    private Map<String, String> createDisplayPlaceholders(AuctionListing listing, AuctionHistory history, Map<String, String> ph) {
        double price = (listing != null) ? listing.price() : history.price();
        String currencyId = (listing != null) ? listing.currencyId() : history.currencyId();
        UUID seller = (listing != null) ? listing.sellerUuid() : history.sellerUuid();
        int id = (listing != null) ? listing.id() : history.id();
        ItemStack item = (listing != null) ? listing.item() : history.item();

        ph.put("price", plugin.core().economy().formats().format(price, currencyId));
        ph.put("seller", plugin.database().records().getNameFromUuid(seller));
        ph.put("amount", String.valueOf(item.getAmount()));
        ph.put("id", String.valueOf(id));
        ph.put("buyer", (history != null) ? plugin.database().records().getNameFromUuid(history.buyerUuid()) : "None");

        String categoryKey = plugin.filters().getCategory(item);
        ph.put("filter", plugin.filters().getDisplayName(categoryKey));
        ph.put("target", ph.getOrDefault("target", "Unknown"));
        ph.put("query", ph.getOrDefault("query", ""));

        String sortId = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());
        ph.put("sort", plugin.config().getSortingDisplayName(sortId));

        return ph;
    }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof PreviewShulkerHolder; }

    public static final class PreviewShulkerHolder implements InventoryHolder {
        private final Map<String, String> displayContext;
        private final GuiDefinition def;
        private final String sourceGui;
        private final String pageStr;
        private Inventory inventory;

        public PreviewShulkerHolder(Map<String, String> displayContext, GuiDefinition def, String sourceGui) {
            this.displayContext = displayContext;
            this.def = def;
            this.sourceGui = sourceGui;
            this.pageStr = displayContext.getOrDefault("page", "1");
        }

        public void setInventory(Inventory inv) { this.inventory = inv; }
        public GuiDefinition def() { return def; }
        public String sourceGui() { return sourceGui; }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory != null ? inventory : Bukkit.createInventory(this, 9);
        }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>(displayContext);
            ctx.put("page", pageStr);
            ctx.putIfAbsent("filter", "all");
            ctx.putIfAbsent("sort", "newest-date");
            return ctx;
        }

        public PageableHolder toPageable() {
            return new PageableHolder() {
                @Override public int page() {
                    try { return Math.max(0, Integer.parseInt(pageStr) - 1); }
                    catch(Exception e) { return 0; }
                }
                @Override public int totalPages() { return 1; }
                @Override public String filter() { return asContext().get("filter"); }
                @Override public String sort() { return asContext().get("sort"); }
                @Override public Map<Integer, Integer> displayedListings() { return Map.of(); }
                @Override public Map<String, String> asContext() { return PreviewShulkerHolder.this.asContext(); }
                @Override public @NotNull Inventory getInventory() { return PreviewShulkerHolder.this.getInventory(); }
            };
        }
    }
}