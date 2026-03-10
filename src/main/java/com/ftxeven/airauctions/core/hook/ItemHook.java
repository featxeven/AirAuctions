package com.ftxeven.airauctions.core.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface ItemHook {
    @Nullable String getItemId(ItemStack item);
    @Nullable ItemStack getItem(String id);
    String getPrefix();
}