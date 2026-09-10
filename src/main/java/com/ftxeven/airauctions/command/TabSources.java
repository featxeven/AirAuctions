package com.ftxeven.airauctions.command;

import com.ftxeven.airauctions.core.command.DurationUnits;
import com.ftxeven.airauctions.core.command.tabcomplete.TabSource;
import com.ftxeven.airauctions.core.command.tabcomplete.TabSourceRegistry;
import com.ftxeven.airauctions.service.ServiceManager;

import java.util.List;

public final class TabSources {

    private TabSources() {
    }

    public static TabSourceRegistry build(ServiceManager services, DurationUnits durationUnits) {
        return TabSourceRegistry.withBuiltins(durationUnits)
                .register("ECONOMY_OPTIONS", (context, param) -> services.economy().economyKeys())
                .register("LISTING_IDS", (context, param) -> listingIds(services, context, param));
    }

    private static List<String> listingIds(ServiceManager services, TabSource.Context context, String param) {
        int cap = TabSourceRegistry.cap(param, 50);
        List<String> recent = services.listings().findRecentIds(500);
        return TabSourceRegistry.filterAndCap(recent, TabSourceRegistry.partial(context), cap);
    }
}