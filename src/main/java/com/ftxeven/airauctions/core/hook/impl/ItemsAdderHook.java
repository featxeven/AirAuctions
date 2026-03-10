package com.ftxeven.airauctions.core.hook.impl;

import com.ftxeven.airauctions.core.hook.ItemHook;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderHook implements ItemHook {
    @Override
    public String getItemId(ItemStack item) {
        var stack = CustomStack.byItemStack(item);
        return stack != null ? stack.getNamespacedID() : null;
    }

    @Override
    public ItemStack getItem(String id) {
        CustomStack stack = CustomStack.getInstance(id);
        return stack != null ? stack.getItemStack() : null;
    }

    @Override
    public String getPrefix() { return "itemsadder"; }
}