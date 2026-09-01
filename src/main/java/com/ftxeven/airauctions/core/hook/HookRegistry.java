package com.ftxeven.airauctions.core.hook;

import com.ftxeven.airauctions.core.gui.render.MaterialResolver;
import com.ftxeven.airauctions.core.hook.impl.CraftEngineHook;
import com.ftxeven.airauctions.core.hook.impl.ItemsAdderHook;
import com.ftxeven.airauctions.core.hook.impl.NexoHook;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class HookRegistry implements MaterialResolver.HookResolver {

    private final JavaPlugin plugin;
    private volatile Map<String, ItemHook> hooks = Map.of();

    public HookRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        Map<String, ItemHook> found = new LinkedHashMap<>();
        register(found, "Nexo", NexoHook::new);
        register(found, "ItemsAdder", ItemsAdderHook::new);
        register(found, "CraftEngine", CraftEngineHook::new);
        hooks = Collections.unmodifiableMap(found);
    }

    public Map<String, ItemHook> hooks() {
        return hooks;
    }

    public @Nullable ItemStack resolve(String id) {
        int split = id.indexOf(':');
        if (split < 0) {
            return null;
        }
        ItemHook hook = hooks.get(id.substring(0, split).toLowerCase(Locale.ROOT));
        if (hook == null) {
            return null;
        }
        try {
            return hook.buildItem(id.substring(split + 1));
        } catch (Throwable e) {
            plugin.getLogger().warning("Hook '" + hook.prefix() + "' threw while building item '" + id + "': " + e.getMessage());
            return null;
        }
    }

    public @Nullable String identify(ItemStack item) {
        for (Map.Entry<String, ItemHook> entry : hooks.entrySet()) {
            try {
                String rawId = entry.getValue().rawId(item);
                if (rawId != null) {
                    return entry.getKey() + ":" + rawId;
                }
            } catch (Throwable e) {
                plugin.getLogger().warning("Hook '" + entry.getKey() + "' threw while identifying an item, skipping: " + e.getMessage());
            }
        }
        return null;
    }

    private void register(Map<String, ItemHook> target, String pluginName, Supplier<ItemHook> factory) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(pluginName)) {
            return;
        }
        try {
            ItemHook hook = factory.get();
            target.put(hook.prefix(), hook);
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to hook into " + pluginName + ": " + e.getMessage());
        }
    }
}