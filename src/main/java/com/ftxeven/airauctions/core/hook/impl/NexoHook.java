package com.ftxeven.airauctions.core.hook.impl;

import com.ftxeven.airauctions.core.hook.ItemHook;
import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class NexoHook implements ItemHook {

    @Override
    public String prefix() { return "nexo"; }

    @Override
    public @Nullable String rawId(ItemStack item) {
        return NexoItems.idFromItem(item);
    }

    @Override
    public @Nullable ItemStack buildItem(String id) {
        var builder = NexoItems.itemFromId(id);
        return builder != null ? builder.build() : null;
    }
}