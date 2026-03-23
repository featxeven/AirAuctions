package com.ftxeven.airauctions.core.manager.confirm;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.core.model.PendingListing;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import com.ftxeven.airauctions.core.gui.util.GuiItemFinder;
import com.ftxeven.airauctions.core.gui.util.GuiListingUtil;
import com.ftxeven.airauctions.core.gui.util.GuiSlotMapper;
import com.ftxeven.airauctions.util.TimeUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class ConfirmListingManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private GuiItem auctionItemTemplate;
    private final BitSet auctionSlotSet = new BitSet();
    private boolean enabled;

    public ConfirmListingManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/confirm/confirm-auction.yml");
        if (!file.exists()) plugin.saveResource("guis/confirm/confirm-auction.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("confirm-listing");
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

        this.definition = new GuiDefinition(section.getString("title", "Confirm Auction"), section.getInt("rows", 3), items, section);
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
        String currencyId = ph.getOrDefault("currency", plugin.config().economyDefaultProvider());

        int amount = Integer.parseInt(ph.getOrDefault("amount", "1"));

        double price;
        if (ph.containsKey("raw-price")) {
            try {
                price = Double.parseDouble(ph.get("raw-price"));
            } catch (NumberFormatException e) {
                price = 0.0;
            }
        } else {
            Double parsed = plugin.core().economy().formats().parseAmount(ph.get("price"), currencyId);
            price = (parsed != null) ? parsed : 0.0;
        }

        String itemName = ph.getOrDefault("item", "Item");
        double fee = plugin.core().economy().calculateListingFee(viewer, price, currencyId);

        PendingListing pending = new PendingListing(
                viewer.getInventory().getItemInMainHand().clone(),
                price,
                amount,
                System.currentTimeMillis()
        );

        ConfirmListingHolder holder = new ConfirmListingHolder(plugin, pending, itemName, definition, fee, currencyId);
        Map<String, String> displayPh = holder.asContext(viewer);
        displayPh.put("seller", viewer.getName());

        String title = PlaceholderUtil.apply(viewer, definition.title(), displayPh);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
        holder.setInventory(inv);

        ItemStack previewItem = pending.item().clone();
        previewItem.setAmount(pending.amount());

        int expireSeconds = plugin.config().getExpireTime(viewer);
        long previewExpiry = (expireSeconds == -1) ? -1L : pending.timestamp() + (expireSeconds * 1000L);

        AuctionListing previewListing = new AuctionListing(
                -1,
                viewer.getUniqueId(),
                previewItem,
                pending.price(),
                currencyId,
                pending.timestamp(),
                previewExpiry
        );

        GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh,
                Collections.singletonList(previewListing), auctionSlotSet, 0, auctionItemTemplate, null, Integer.MAX_VALUE);

        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof ConfirmListingHolder holder) || event.getCurrentItem() == null) return;

        GuiItem item = GuiItemFinder.find(holder.def(), event.getSlot(), viewer, holder.asContext(viewer), null);
        if (GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder.toPageable())) {
            if (item.key().equals("confirm")) {
                executeFinalSell(viewer, holder);
                viewer.closeInventory();
            } else if (item.key().equals("cancel")) {
                viewer.closeInventory();
            }
        }
    }

    private void executeFinalSell(Player player, ConfirmListingHolder holder) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        PendingListing pending = holder.pending();
        double fee = holder.fee();
        String currencyId = holder.currencyId();

        if (itemInHand.getType().isAir() || itemInHand.getType() != pending.item().getType()) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.holding-air"), Map.of());
            return;
        }

        if (itemInHand.getAmount() < pending.amount()) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.insufficient-item"),
                    Map.of("holding", String.valueOf(itemInHand.getAmount())));
            return;
        }

        if (isLimitReached(player) || isExpiredLimitReached(player) || isStillOnCooldown(player)) {
            player.closeInventory();
            return;
        }

        if (fee > 0) {
            if (!plugin.core().economy().canAfford(player, currencyId, fee)) {
                MessageUtil.send(player, plugin.lang().get("errors.cannot-afford-fee"), Map.of(
                        "fee", plugin.core().economy().formats().format(fee, currencyId)
                ));
                return;
            }
            plugin.economy().getProvider(currencyId).withdraw(player, fee);
        }

        int expireSeconds = plugin.config().getExpireTime(player);
        long expiryTime = (expireSeconds == -1) ? -1L : System.currentTimeMillis() + (expireSeconds * 1000L);

        plugin.core().auctions().listEntry(player, itemInHand, pending.price(), pending.amount(), expiryTime, currencyId);

        MessageUtil.send(player, plugin.lang().get("auctions.sell.success"), Map.of(
                "amount", String.valueOf(pending.amount()),
                "item", holder.itemName(),
                "price", plugin.core().economy().formats().format(pending.price(), currencyId),
                "fee", plugin.core().economy().formats().format(fee, currencyId)
        ));
    }

    private boolean isStillOnCooldown(Player player) {
        int cooldown = plugin.config().auctionCooldown();
        if (cooldown <= 0) return false;

        Long last = plugin.core().auctions().getLastListingTimes().get(player.getUniqueId());
        if (last == null) return false;

        long remaining = (long) cooldown - ((System.currentTimeMillis() - last) / 1000L);
        if (remaining > 0) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.cooldown"),
                    Map.of("time", TimeUtil.formatSeconds(plugin, remaining)));
            return true;
        }
        return false;
    }

    private boolean isLimitReached(Player player) {
        int limit = plugin.config().getPlayerLimit(player);
        if (limit == Integer.MAX_VALUE) return false;
        int currentActive = plugin.core().auctions().getListingCount(player.getUniqueId());
        if (currentActive >= limit) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.limit-reached"),
                    Map.of("limit", String.valueOf(limit)));
            return true;
        }
        return false;
    }

    private boolean isExpiredLimitReached(Player player) {
        int expiredLimit = plugin.config().expiredLimit();
        if (expiredLimit <= 0) return false;
        int currentExpired = plugin.core().auctions().getExpiredCount(player.getUniqueId());
        if (currentExpired >= expiredLimit) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.expired-limit-reached"),
                    Map.of("limit", String.valueOf(expiredLimit)));
            return true;
        }
        return false;
    }

    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof ConfirmListingHolder; }

    public static final class ConfirmListingHolder implements InventoryHolder {
        private final AirAuctions plugin;
        private final PendingListing pending;
        private final String itemName;
        private final GuiDefinition def;
        private final double fee;
        private final String currencyId;
        private Inventory inventory;

        public ConfirmListingHolder(AirAuctions plugin, PendingListing pending, String itemName, GuiDefinition def, double fee, String currencyId) {
            this.plugin = plugin;
            this.pending = pending;
            this.itemName = itemName;
            this.def = def;
            this.fee = fee;
            this.currencyId = currencyId;
        }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public PendingListing pending() { return pending; }
        public String itemName() { return itemName; }
        public GuiDefinition def() { return def; }
        public double fee() { return fee; }
        public String currencyId() { return currencyId; }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory != null ? inventory : Bukkit.createInventory(this, 9);
        }

        public Map<String, String> asContext(Player viewer) {
            Map<String, String> ctx = new HashMap<>();
            double price = pending.price();
            double tax = plugin.core().economy().calculateSalesTax(viewer.getUniqueId(), price, plugin.economy().getProvider(currencyId));
            double profit = price - tax;

            ctx.put("price", plugin.core().economy().formats().format(price, currencyId));
            ctx.put("fee", plugin.core().economy().formats().format(fee, currencyId));
            ctx.put("tax", plugin.core().economy().formats().format(tax, currencyId));
            ctx.put("profit", plugin.core().economy().formats().format(profit, currencyId));
            ctx.put("amount", String.valueOf(pending.amount()));
            ctx.put("item", itemName);
            return ctx;
        }

        public PageableHolder toPageable() {
            return new PageableHolder() {
                @Override public int page() { return 0; }
                @Override public int totalPages() { return 1; }
                @Override public String filter() { return "all"; }
                @Override public String sort() { return "none"; }
                @Override public Map<Integer, Integer> displayedListings() { return Map.of(); }
                @Override public Map<String, String> asContext() {
                    Player viewer = (Player) inventory.getViewers().getFirst();
                    return ConfirmListingHolder.this.asContext(viewer);
                }
                @Override public @NotNull Inventory getInventory() { return ConfirmListingHolder.this.getInventory(); }
            };
        }
    }
}