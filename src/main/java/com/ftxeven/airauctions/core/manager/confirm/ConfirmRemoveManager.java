package com.ftxeven.airauctions.core.manager.confirm;

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
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class ConfirmRemoveManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private boolean enabled;

    public ConfirmRemoveManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/confirm-remove.yml");
        if (!file.exists()) plugin.saveResource("guis/confirm-remove.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("remove-listing");
        if (section == null) section = cfg;

        this.enabled = section.getBoolean("enabled", true);

        List<Integer> slots = GuiDefinition.parseSlots(section.getStringList("auction-slot"));
        this.auctionSlotSet.clear();
        for (int s : slots) if (s >= 0) this.auctionSlotSet.set(s);

        ConfigurationSection itemSec = section.getConfigurationSection("auction-item");
        this.auctionItemTemplate = itemSec != null ? GuiItem.fromSection("auction-item", itemSec) : null;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(section.getString("title", "Remove Listing"), section.getInt("rows", 3), items, section);
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    public boolean isGuiEnabled() { return enabled; }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        int id = Integer.parseInt(ph.getOrDefault("id", "-1"));
        AuctionListing listing = plugin.core().auctions().getActiveListing(id);

        if (listing == null) return null;

        RemoveListingHolder holder = new RemoveListingHolder(plugin, listing, definition);
        Map<String, String> displayPh = holder.asContext();

        String title = PlaceholderUtil.apply(viewer, definition.title(), displayPh);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
        holder.setInventory(inv);

        GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh,
                Collections.singletonList(listing), auctionSlotSet, 0, auctionItemTemplate, null, Integer.MAX_VALUE);

        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof RemoveListingHolder holder)) return;

        GuiItem item = GuiItemFinder.find(holder.def(), event.getSlot(), viewer, holder.asContext(), null);
        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder.toPageable())) {
            if (item.key().equals("confirm")) {
                executeRemoval(viewer, holder.listing());
                viewer.closeInventory();
            } else if (item.key().equals("cancel")) {
                viewer.closeInventory();
            }
        }
    }

    private void executeRemoval(Player player, AuctionListing listing) {
        if (plugin.core().auctions().getActiveListing(listing.id()) == null) {
            MessageUtil.send(player, plugin.lang().get("auctions.remove.error.not-found"), Map.of("id", String.valueOf(listing.id())));
            return;
        }

        Map<String, String> ph = getMessagePlaceholders(listing);
        ph.put("player", player.getName());

        plugin.core().auctions().forceDeleteListing(listing);

        if (listing.sellerUuid().equals(player.getUniqueId())) {
            MessageUtil.send(player, plugin.lang().get("auctions.remove.removed"), ph);
        } else {
            MessageUtil.send(player, plugin.lang().get("auctions.remove.removed-for"), ph);
            Player seller = plugin.getServer().getPlayer(listing.sellerUuid());
            if (seller != null && seller.isOnline()) {
                MessageUtil.send(seller, plugin.lang().get("auctions.remove.removed-by"), ph);
            }
        }
    }

    private Map<String, String> getMessagePlaceholders(AuctionListing listing) {
        Map<String, String> ph = new HashMap<>();
        ph.put("id", String.valueOf(listing.id()));
        ph.put("amount", String.valueOf(listing.item().getAmount()));
        ph.put("item", plugin.itemTranslations().translate(listing.item().getType()));
        String sellerName = plugin.database().records().getNameFromUuid(listing.sellerUuid());
        ph.put("seller", sellerName != null ? sellerName : "Unknown");
        return ph;
    }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof RemoveListingHolder; }

    public static final class RemoveListingHolder implements InventoryHolder {
        private final AirAuctions plugin;
        private final AuctionListing listing;
        private final GuiDefinition def;
        private Inventory inventory;

        public RemoveListingHolder(AirAuctions plugin, AuctionListing listing, GuiDefinition def) {
            this.plugin = plugin;
            this.listing = listing;
            this.def = def;
        }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public AuctionListing listing() { return listing; }
        public GuiDefinition def() { return def; }

        @Override public @NotNull Inventory getInventory() { return inventory; }

        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            String sellerName = plugin.database().records().getNameFromUuid(listing.sellerUuid());

            ctx.put("id", String.valueOf(listing.id()));
            ctx.put("target", sellerName != null ? sellerName : "Unknown");
            ctx.put("amount", String.valueOf(listing.item().getAmount()));
            ctx.put("price", plugin.core().economy().formats().format(listing.price(), listing.currencyId()));
            return ctx;
        }

        public PageableHolder toPageable() {
            return new PageableHolder() {
                @Override public int page() { return 0; }
                @Override public int totalPages() { return 1; }
                @Override public String filter() { return "all"; }
                @Override public String sort() { return "none"; }
                @Override public Map<Integer, Integer> displayedListings() { return Map.of(); }
                @Override public Map<String, String> asContext() { return RemoveListingHolder.this.asContext(); }
                @Override public @NotNull Inventory getInventory() { return RemoveListingHolder.this.getInventory(); }
            };
        }
    }
}