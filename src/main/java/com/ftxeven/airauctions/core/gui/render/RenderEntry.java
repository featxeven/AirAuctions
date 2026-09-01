package com.ftxeven.airauctions.core.gui.render;

import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public record RenderEntry(
        ItemConfig.Template template,
        @Nullable ItemStack baseItem,
        Map<String, String> placeholders,
        Function<String, String> flags
) {}