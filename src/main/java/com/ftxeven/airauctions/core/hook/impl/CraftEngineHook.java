package com.ftxeven.airauctions.core.hook.impl;

import com.ftxeven.airauctions.core.hook.ItemHook;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class CraftEngineHook implements ItemHook {

    private Object ceItemManager;
    private MethodHandle ceIdMethod;
    private MethodHandle ceGetMethod;

    public CraftEngineHook() {
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) return;
        try {
            Object engine = CraftEngine.instance();
            ceItemManager = engine.getClass().getMethod("itemManager").invoke(engine);
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            for (Method m : ceItemManager.getClass().getMethods()) {
                if (ceIdMethod == null && m.getParameterCount() == 1 && (m.getReturnType() == String.class || m.getReturnType().getSimpleName().equals("Key"))) {
                    Class<?> param = m.getParameterTypes()[0];
                    if (param.isAssignableFrom(ItemStack.class) || param == Object.class) {
                        ceIdMethod = lookup.unreflect(m);
                    }
                }
                if (ceGetMethod == null && m.getParameterCount() == 1 && m.getReturnType().isAssignableFrom(ItemStack.class)) {
                    if (m.getParameterTypes()[0] == String.class || m.getParameterTypes()[0].getSimpleName().equals("Key")) {
                        ceGetMethod = lookup.unreflect(m);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public String getItemId(ItemStack item) {
        if (ceIdMethod == null || ceItemManager == null) return null;
        try {
            Object res = ceIdMethod.invoke(ceItemManager, item);
            return res != null ? res.toString().toLowerCase() : null;
        } catch (Throwable t) { return null; }
    }

    @Override
    public ItemStack getItem(String id) {
        if (ceGetMethod == null || ceItemManager == null) return null;
        try {
            Object res = ceGetMethod.invoke(ceItemManager, id);
            return res instanceof ItemStack is ? is : null;
        } catch (Throwable t) { return null; }
    }

    @Override
    public String getPrefix() { return "craftengine"; }
}