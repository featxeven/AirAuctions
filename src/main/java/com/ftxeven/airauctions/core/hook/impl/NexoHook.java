package com.ftxeven.airauctions.core.hook.impl;

import com.ftxeven.airauctions.core.hook.ItemHook;
import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;

public class NexoHook implements ItemHook {
    @Override
    public String getItemId(ItemStack item) {
        return NexoItems.idFromItem(item);
    }

    @Override
    public ItemStack getItem(String id) {
        var builder = NexoItems.itemFromId(id);
        return builder != null ? builder.build() : null;
    }

    @Override
    public String getPrefix() { return "nexo"; }
}