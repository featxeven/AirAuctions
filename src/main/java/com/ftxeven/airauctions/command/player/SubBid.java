package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.MainConfig;
import com.ftxeven.airauctions.core.command.CommandDispatch;
import com.ftxeven.airauctions.core.command.DurationUnits;
import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.impl.ListingDraft;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.service.listing.workflow.BidService;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.TimeFormatter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.*;

public final class SubBid extends DraftSubcommand<Integer> {

    private final DurationUnits durationUnits;

    public SubBid(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis,
                  TabCompleteEngine tabCompleteEngine, DurationUnits durationUnits) {
        super(configs, messenger, services, guis, tabCompleteEngine, "bid", 1, "bids.create");
        this.durationUnits = durationUnits;
    }

    @Override
    protected MainConfig.ListingFlow flow() {
        return configs.main().bids().flow();
    }

    @Override
    protected CommandDispatch.Availability extraAvailability() {
        return CommandDispatch.Availability.OPTIONAL;
    }

    @Override
    protected Optional<Integer> parseLeadingExtra(Player seller, Optional<String> token) {
        BidService.DurationBounds bounds = services.bids().resolveDurationBounds(seller);

        if (token.isEmpty()) {
            return Optional.of(clamp(configs.main().bids().defaultDuration(), bounds));
        }

        Integer seconds = parseSeconds(token.get());
        boolean valid = seconds != null && seconds >= bounds.min() && (bounds.max() < 0 || seconds <= bounds.max());
        if (!valid) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("min", TimeFormatter.duration(Duration.ofSeconds(bounds.min()), configs.main().formatting(), configs.lang()));
            placeholders.put("max", TimeFormatter.durationOrUnlimited(bounds.max(), configs.main().formatting(), configs.lang()));
            messenger.send(seller, configs.lang().get("bids.create.errors.invalid-duration"), placeholders);
            return Optional.empty();
        }
        return Optional.of(seconds);
    }

    @Override
    protected Map<String, String> previewPlaceholders(Player seller, Integer durationSeconds) {
        return Map.of("duration", TimeFormatter.duration(Duration.ofSeconds(durationSeconds), configs.main().formatting(), configs.lang()));
    }

    @Override
    protected Optional<ListingService.Prepared> prepareListing(Player seller, ItemStack item, int amount, double price, EconomyProvider provider, Integer durationSeconds) {
        return services.bids().prepare(seller, item, amount, price, durationSeconds, provider);
    }

    @Override
    protected ListingDraft buildDraft(ItemStack itemSnapshot, int slot, int amount, double price, EconomyProvider provider, Integer durationSeconds) {
        return ListingDraft.bid(itemSnapshot, slot, amount, durationSeconds, price, provider.id());
    }

    @Override
    protected void announceCreated(Player seller, ListingService.Prepared prepared) {
        services.bids().announceCreated(seller, prepared);
    }

    // a bare "300" or a chain of unit segments like "2h45m"
    private Integer parseSeconds(String raw) {
        OptionalLong parsedTicks = durationUnits.parse(raw, durationFilter());
        if (parsedTicks.isEmpty()) {
            return null;
        }
        OptionalInt seconds = DurationUnits.wholeSeconds(parsedTicks.getAsLong());
        return seconds.isPresent() ? seconds.getAsInt() : null;
    }

    private static int clamp(int seconds, BidService.DurationBounds bounds) {
        return bounds.max() < 0 ? Math.max(seconds, bounds.min()) : Math.clamp(seconds, bounds.min(), bounds.max());
    }

    private String durationFilter() {
        return DurationUnits.filterFor(config().tabComplete(), 2);
    }
}