package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.service.ServiceManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class GlobalGui extends ListingGridGui {

    public static final String ID = "browsing/global";

    public GlobalGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis) {
        super(services, configs, guis);
    }

    @Override
    protected ListingScope scope() {
        return ListingScope.ACTIVE;
    }

    @Override
    protected @Nullable UUID owner(Player viewer, GuiSession session) {
        return null;
    }
}