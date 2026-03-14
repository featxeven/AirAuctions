package com.ftxeven.airauctions.core.hook;

import com.ftxeven.airauctions.core.hook.impl.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HookManager {

    private final List<ItemHook> hooks = new ArrayList<>();

    public HookManager() {
        if (isId("Nexo")) {
            try { hooks.add(new NexoHook()); } catch (Throwable ignored) {}
        }
        if (isId("ItemsAdder")) {
            try { hooks.add(new ItemsAdderHook()); } catch (Throwable ignored) {}
        }
    }

    private boolean isId(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    public String match(ItemStack item, Set<String> targetIds, String prefix) {
        for (ItemHook hook : hooks) {
            if (!hook.getPrefix().equalsIgnoreCase(prefix)) continue;
            String id = hook.getItemId(item);
            if (id == null) continue;
            id = id.toLowerCase();
            if (targetIds.contains(id)) return id;
            if (id.contains(":")) {
                String bare = id.substring(id.indexOf(':') + 1);
                if (targetIds.contains(bare)) return bare;
            }
        }
        return null;
    }

    public ItemStack getItem(String id, String prefix) {
        for (ItemHook hook : hooks) {
            if (hook.getPrefix().equalsIgnoreCase(prefix)) {
                return hook.getItem(id);
            }
        }
        return null;
    }

    public List<ItemHook> getHooks() {
        return hooks;
    }
}