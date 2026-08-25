package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.common.command.DynamicCommand;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.common.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.common.gui.GuiManager;
import com.ftxeven.airauctions.common.gui.OpenOptions;
import com.ftxeven.airauctions.gui.impl.ConfirmGui;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.model.ListingScope;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.ItemDisplay;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.TimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SubDelete implements SubCommand {

    private static final String KEY = "delete";
    private static final DynamicCommand DISABLED = new DynamicCommand(false, KEY, List.of(), "", "", Map.of(), Map.of());

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final GuiManager guis;
    private final TabCompleteEngine tabComplete;

    public SubDelete(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis, TabCompleteEngine tabComplete) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.guis = guis;
        this.tabComplete = tabComplete;
    }

    @Override
    public String name() { return config().name(); }

    @Override
    public List<String> aliases() { return config().aliases(); }

    @Override
    public boolean enabled() { return config().enabled(); }

    @Override
    public String usage() { return config().usage(); }

    @Override
    public String permission() { return Permissions.command(KEY); }

    @Override
    public boolean playerOnly() { return false; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return 1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (sender instanceof Player player) {
            Optional<PendingDelete> pending = services.confirmations().confirm(player.getUniqueId(), KEY);
            if (pending.isPresent() && pending.get().args().equals(List.of(args))) {
                finalizeDelete(player, pending.get());
                return;
            }
        }

        requestNew(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return tabComplete.complete(sender, config(), args);
    }

    // Fresh request

    private void requestNew(CommandSender sender, String[] args) {
        String id = args[0];
        Optional<Listing> listing = services.listings().find(id);
        if (listing.isEmpty()) {
            messenger.send(sender, configs.lang().get("listings.delete.errors.not-found"), Map.of("id", id));
            return;
        }

        if (sender instanceof Player player && requireConfirmation()) {
            if (guiConfirmationEnabled()) {
                openConfirmGui(player, listing.get());
            } else {
                requestChatConfirmation(player, args, listing.get());
            }
            return;
        }

        applyDelete(sender, listing.get());
    }

    private void openConfirmGui(Player player, Listing listing) {
        ListingScope scope = services.listings().resolveScope(listing).orElse(ListingScope.ACTIVE);
        Map<String, Object> attributes = Map.of(ConfirmGui.ATTR_LISTING_ID, listing.info().id());
        guis.open(player, ConfirmGui.DELETE, new HashMap<>(), OpenOptions.entry(attributes, ancestorChainFor(scope)));
    }

    private List<String> ancestorChainFor(ListingScope scope) {
        return switch (scope) {
            case ACTIVE -> List.of("target/active");
            case EXPIRED -> List.of("target/expired");
            case STORAGE -> List.of("target/storage");
        };
    }

    private void requestChatConfirmation(Player player, String[] args, Listing listing) {
        PendingDelete context = new PendingDelete(List.of(args), listing);
        services.confirmations().request(player, KEY, confirmationTimeout(), context, () -> sendExpired(player, context));
        sendConfirmationRequest(player, context);
    }

    private void applyDelete(CommandSender sender, Listing listing) {
        switch (listing) {
            case Listing.Auction auction -> services.auctions().delete(auction, sender);
            case Listing.Bid bid -> services.bids().delete(bid, sender);
        }
    }

    // Confirmed finalization (chat path only)

    private void finalizeDelete(Player player, PendingDelete context) {
        // re-checked against the cache rather than trusting the snapshot
        String id = context.snapshot().info().id();
        Optional<Listing> current = services.listings().find(id);
        if (current.isEmpty()) {
            messenger.send(player, configs.lang().get("listings.delete.errors.not-found"), Map.of("id", id));
            return;
        }

        applyDelete(player, current.get());
    }

    // Messaging

    private void sendConfirmationRequest(Player player, PendingDelete context) {
        Map<String, String> placeholders = new HashMap<>(placeholders(context.snapshot()));
        placeholders.put("timeout", formatTimeout(confirmationTimeout()));
        messenger.send(player, configs.lang().get("listings.delete.confirmation.request"), placeholders);
    }

    private void sendExpired(Player player, PendingDelete context) {
        messenger.send(player, configs.lang().get("listings.delete.confirmation.expired"), placeholders(context.snapshot()));
    }

    private Map<String, String> placeholders(Listing listing) {
        Listing.Info info = listing.info();
        double price = switch (listing) {
            case Listing.Auction auction -> auction.price();
            case Listing.Bid bid -> bid.currentPrice();
        };

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", info.id());
        placeholders.put("seller", services.players().name(info.seller()));
        placeholders.put("amount", String.valueOf(info.amount()));
        ItemDisplay.formatInto(placeholders, "item", info.item(), configs.lang());
        services.economy().formatInto(placeholders, "price", info.economy(), price);
        return placeholders;
    }

    private String formatTimeout(int timeoutSeconds) {
        return timeoutSeconds < 0
                ? configs.lang().get("placeholders.never").getFirst()
                : TimeFormatter.duration(Duration.ofSeconds(timeoutSeconds), configs.main().formatting(), configs.lang());
    }

    // Config

    private boolean requireConfirmation() {
        return configs.main().listings().requireDeleteConfirmation();
    }

    private int confirmationTimeout() {
        return configs.main().listings().deleteConfirmationTimeout();
    }

    private boolean guiConfirmationEnabled() {
        return guis.definition(ConfirmGui.DELETE).map(gui -> gui.settings().enabled()).orElse(false);
    }

    private DynamicCommand config() {
        DynamicCommand command = configs.commands().subcommands().get(KEY);
        return command != null ? command : DISABLED;
    }

    // Internal types

    private record PendingDelete(List<String> args, Listing snapshot) {}
}