package com.ftxeven.airauctions.listener;

import com.ftxeven.airauctions.core.gui.GuiManager;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class GuiListener implements Listener {

    private final GuiManager guis;

    public GuiListener(GuiManager guis) {
        this.guis = guis;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            guis.handleClick(event, player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            guis.handleClose(player, event.getInventory());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        guis.input().handleChat(event);
    }

    @EventHandler
    public void onSign(UncheckedSignChangeEvent event) {
        guis.input().handleSign(event);
    }
}