package com.ftxeven.airauctions.command;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.MainConfig;
import com.ftxeven.airauctions.core.command.DurationUnits;
import com.ftxeven.airauctions.core.command.tabcomplete.TabSource;
import com.ftxeven.airauctions.core.command.tabcomplete.TabSourceRegistry;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.service.ServiceManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TabSources {

    private TabSources() {
    }

    public static TabSourceRegistry build(ServiceManager services, ConfigManager configs, GuiManager guis, DurationUnits durationUnits) {
        return TabSourceRegistry.withBuiltins(durationUnits)
                .register("ECONOMY_OPTIONS", (context, param) -> services.economy().economyKeys())
                .register("LISTING_IDS", (context, param) -> listingIds(services, context, param))
                .register("GUI_IDS", (context, param) -> List.copyOf(guis.ids()))
                .register("FILTER_CATEGORIES", (context, param) -> List.copyOf(configs.filter().categories().keySet()))
                .register("FILTER_TYPES", (context, param) -> List.copyOf(configs.main().listings().filterTypes().keySet()))
                .register("FILTER_ECONOMIES", (context, param) -> enabledEconomyKeys(configs))
                .register("SORT", (context, param) -> allSortKeys(configs));
    }

    private static List<String> listingIds(ServiceManager services, TabSource.Context context, String param) {
        int cap = TabSourceRegistry.cap(param, 50);
        List<String> recent = services.listings().findRecentIds(500);
        return TabSourceRegistry.filterAndCap(recent, TabSourceRegistry.partial(context), cap);
    }

    private static List<String> enabledEconomyKeys(ConfigManager configs) {
        List<String> keys = new ArrayList<>();
        configs.expansions().economy().providers().forEach((key, provider) -> {
            if (provider.enabled()) {
                keys.add(key);
            }
        });
        return keys;
    }

    // every sort key across active/unclaimed/history
    private static List<String> allSortKeys(ConfigManager configs) {
        MainConfig.Sort sort = configs.main().listings().sort();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(sort.active().keySet());
        keys.addAll(sort.unclaimed().keySet());
        keys.addAll(sort.history().keySet());
        return List.copyOf(keys);
    }
}