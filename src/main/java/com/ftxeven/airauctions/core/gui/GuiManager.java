package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.manager.main.CategoryManager;
import com.ftxeven.airauctions.core.manager.confirm.*;
import com.ftxeven.airauctions.core.manager.player.CancelListingManager;
import com.ftxeven.airauctions.core.manager.player.ExpiredListingsManager;
import com.ftxeven.airauctions.core.manager.player.ListingHistoryManager;
import com.ftxeven.airauctions.core.manager.main.AuctionHouseManager;
import com.ftxeven.airauctions.core.manager.player.PlayerListingsManager;
import com.ftxeven.airauctions.core.manager.main.PreviewShulkerManager;
import com.ftxeven.airauctions.core.gui.util.GuiSlotMapper;
import com.ftxeven.airauctions.core.manager.main.AuctionSearchManager;
import com.ftxeven.airauctions.core.manager.target.TargetHistoryManager;
import com.ftxeven.airauctions.core.manager.target.TargetListingsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

public final class GuiManager {
    private final AirAuctions plugin;
    private final ItemAction itemAction;
    private final ItemCooldownHandler itemCooldownHandler;
    private final Map<String, CustomGuiManager> managers = new HashMap<>();
    private boolean reloading = false;

    public GuiManager(AirAuctions plugin) {
        this.plugin = plugin;
        this.itemAction = new ItemAction(plugin);
        this.itemCooldownHandler = new ItemCooldownHandler(plugin);
        loadAll();
    }

    public void reload() {
        reloading = true;
        plugin.getServer().getOnlinePlayers().forEach(p -> {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (getManagerByInv(top) != null) p.closeInventory();
        });

        GuiSlotMapper.invalidateCache();

        itemCooldownHandler.clear();
        managers.values().forEach(CustomGuiManager::cleanup);
        managers.clear();
        loadAll();
        reloading = false;
    }

    public void loadAll() {
        reg("auction_house", new AuctionHouseManager(plugin));
        reg("auction_search", new AuctionSearchManager(plugin));
        reg("confirm_purchase", new ConfirmPurchaseManager(plugin));
        reg("confirm_purchase_shulker", new ConfirmPurchaseShulkerManager(plugin));
        reg("confirm_listing", new ConfirmListingManager(plugin));
        reg("categories", new CategoryManager(plugin));
        reg("preview_shulker", new PreviewShulkerManager(plugin));
        reg("player_listings", new PlayerListingsManager(plugin));
        reg("player_history", new ListingHistoryManager(plugin));
        reg("player_expired", new ExpiredListingsManager(plugin));
        reg("cancel_listing", new CancelListingManager(plugin));
        reg("target_listings", new TargetListingsManager(plugin));
        reg("target_history", new TargetHistoryManager(plugin));
    }

    private CustomGuiManager getManagerByInv(Inventory inv) {
        for (CustomGuiManager m : managers.values()) if (m.owns(inv)) return m;
        return null;
    }

    public void reg(String id, CustomGuiManager m) { managers.put(id.toLowerCase(), m); }

    public void handleClick(InventoryClickEvent e) {
        if (reloading || !(e.getWhoClicked() instanceof Player p)) return;
        CustomGuiManager m = getManagerByInv(e.getInventory());
        if (m != null) m.handleClick(e, p);
    }

    public void refresh(Player p, Inventory inv, Map<String, String> ph) {
        if (reloading) return;
        CustomGuiManager m = getManagerByInv(inv);
        if (m != null) m.refresh(inv, p, ph);
    }

    public void open(String id, Player p, Map<String, String> ph) {
        if (reloading) return;
        CustomGuiManager m = managers.get(id.toLowerCase());
        if (m != null) {
            Inventory inv = m.build(p, ph);
            if (inv != null) plugin.scheduler().runEntityTask(p, () -> p.openInventory(inv));
        }
    }

    public CustomGuiManager get(String id) { return managers.get(id.toLowerCase()); }

    @SuppressWarnings("unchecked")
    public <T extends CustomGuiManager> T get(String id, Class<T> clazz) {
        CustomGuiManager m = managers.get(id.toLowerCase());
        return clazz.isInstance(m) ? (T) m : null;
    }

    public ItemAction action() { return itemAction; }
    public ItemCooldownHandler cooldown() { return itemCooldownHandler; }

    public interface CustomGuiManager {
        Inventory build(Player viewer, Map<String, String> placeholders);
        void handleClick(InventoryClickEvent event, Player viewer);
        boolean owns(Inventory inv);
        default void cleanup() {}
        default void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {}
    }
}