package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.MainConfig;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.impl.ListingDraft;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class SubSell extends DraftSubcommand<DraftSubcommand.NoExtra> {

    public SubSell(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis, TabCompleteEngine tabCompleteEngine) {
        super(configs, messenger, services, guis, tabCompleteEngine, "sell", 1, "auctions.sell");
    }

    @Override
    protected MainConfig.ListingFlow flow() {
        return configs.main().auctions().flow();
    }

    @Override
    protected Optional<DraftSubcommand.NoExtra> parseLeadingExtra(Player seller, Optional<String> token) {
        return Optional.of(new NoExtra());
    }

    @Override
    protected Map<String, String> previewPlaceholders(Player seller, NoExtra extra) {
        return Map.of("expires", services.auctions().previewExpires(seller));
    }

    @Override
    protected Optional<ListingService.Prepared> prepareListing(Player seller, ItemStack item, int amount, double price, EconomyProvider provider, DraftSubcommand.NoExtra extra) {
        return services.auctions().prepare(seller, item, amount, price, provider);
    }

    @Override
    protected ListingDraft buildDraft(ItemStack itemSnapshot, int slot, int amount, double price, EconomyProvider provider, NoExtra extra) {
        return ListingDraft.auction(itemSnapshot, slot, amount, price, provider.id());
    }

    @Override
    protected void announceCreated(Player seller, ListingService.Prepared prepared) {
        services.auctions().announceCreated(seller, prepared);
    }
}