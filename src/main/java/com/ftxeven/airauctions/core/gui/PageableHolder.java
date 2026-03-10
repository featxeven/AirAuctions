package com.ftxeven.airauctions.core.gui;

import org.bukkit.inventory.InventoryHolder;
import java.util.Map;

public interface PageableHolder extends InventoryHolder {
    int page();
    int totalPages();
    String filter();
    String sort();
    Map<Integer, Integer> displayedListings();
    Map<String, String> asContext();
}