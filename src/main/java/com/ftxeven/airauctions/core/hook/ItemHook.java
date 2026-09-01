package com.ftxeven.airauctions.core.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface ItemHook {

    String prefix();

    @Nullable String rawId(ItemStack item);

    @Nullable ItemStack buildItem(String id);
}