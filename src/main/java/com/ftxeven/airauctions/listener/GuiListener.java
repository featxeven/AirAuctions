package com.ftxeven.airauctions.listener;

import com.ftxeven.airauctions.AirAuctions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class GuiListener implements Listener {

    private final AirAuctions plugin;

    public GuiListener(AirAuctions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        plugin.core().gui().handleClick(event);
    }
}