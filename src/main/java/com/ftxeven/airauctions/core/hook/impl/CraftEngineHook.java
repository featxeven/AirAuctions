package com.ftxeven.airauctions.core.hook.impl;

import com.ftxeven.airauctions.core.hook.ItemHook;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class CraftEngineHook implements ItemHook {

    @Override
    public String prefix() { return "craftengine"; }

    @Override
    public @Nullable String rawId(ItemStack item) {
        return CraftEngine.instance().itemManager()
                .wrap(item)
                .customId()
                .map(Key::asString)
                .orElse(null);
    }

    @Override
    public @Nullable ItemStack buildItem(String id) {
        return CraftEngine.instance().itemManager()
                .getBuildableItem(Key.of(id))
                .map(buildable -> (ItemStack) buildable.buildItem((net.momirealms.craftengine.core.entity.player.Player) null).platformItem())
                .orElse(null);
    }
}