package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.manager.confirm.ConfirmRemoveManager;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class RemoveSubCommand {

    private final AirAuctions plugin;

    public RemoveSubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String label, String[] args) {
        if (!player.hasPermission("airauctions.command.remove")) {
            MessageUtil.send(player, plugin.lang().get("errors.no-permission"), Map.of());
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, plugin.lang().get("errors.incorrect-usage"),
                    Map.of("usage", plugin.config().getSubcommandUsage("remove", label)));
            return;
        }

        if (plugin.config().errorOnExcessArgs() && args.length > 2) {
            MessageUtil.send(player, plugin.lang().get("errors.too-many-arguments"),
                    Map.of("usage", plugin.config().getSubcommandUsage("remove", label)));
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendNotFound(player, args[1]);
            return;
        }

        UUID uuid = player.getUniqueId();

        if (plugin.core().auctions().getPendingRemoveConfirmations().containsKey(uuid)) {
            int pendingId = plugin.core().auctions().getPendingRemoveConfirmations().get(uuid);
            if (pendingId == id) {
                plugin.core().auctions().getPendingRemoveConfirmations().remove(uuid);
                performRemoval(player, id);
                return;
            }
        }

        AuctionListing listing = plugin.core().auctions().getActiveListing(id);
        if (listing == null) {
            sendNotFound(player, String.valueOf(id));
            return;
        }

        var removeManager = plugin.core().gui().get("remove_listing", ConfirmRemoveManager.class);

        if (removeManager != null && removeManager.isGuiEnabled()) {
            plugin.core().gui().open("remove_listing", player, Map.of("id", String.valueOf(id)));
        } else {
            if (!plugin.config().confirmRemove()) {
                performRemoval(player, id);
            } else {
                startConfirmation(player, listing);
            }
        }
    }

    private void startConfirmation(Player player, AuctionListing listing) {
        UUID uuid = player.getUniqueId();
        int id = listing.id();

        plugin.core().auctions().getPendingRemoveConfirmations().put(uuid, id);
        Map<String, String> ph = getPlaceholders(listing);

        MessageUtil.send(player, plugin.lang().get("auctions.remove.confirmation.request"), ph);

        int expiry = plugin.config().removeExpireTime();
        if (expiry > 0) {
            plugin.scheduler().runDelayed(() -> {
                Integer currentPending = plugin.core().auctions().getPendingRemoveConfirmations().get(uuid);
                if (currentPending != null && currentPending == id) {
                    plugin.core().auctions().getPendingRemoveConfirmations().remove(uuid);
                    MessageUtil.send(player, plugin.lang().get("auctions.remove.confirmation.expired"), ph);
                }
            }, expiry * 20L);
        }
    }

    private void performRemoval(Player player, int id) {
        AuctionListing listing = plugin.core().auctions().getActiveListing(id);
        if (listing == null) {
            sendNotFound(player, String.valueOf(id));
            return;
        }

        Map<String, String> ph = getPlaceholders(listing);
        ph.put("player", player.getName());

        plugin.core().auctions().forceDeleteListing(listing);

        if (listing.sellerUuid().equals(player.getUniqueId())) {
            MessageUtil.send(player, plugin.lang().get("auctions.remove.removed"), ph);
        } else {
            MessageUtil.send(player, plugin.lang().get("auctions.remove.removed-for"), ph);

            Player seller = plugin.getServer().getPlayer(listing.sellerUuid());
            if (seller != null && seller.isOnline()) {
                MessageUtil.send(seller, plugin.lang().get("auctions.remove.removed-by"), ph);
            }
        }
    }

    private void sendNotFound(Player player, String id) {
        MessageUtil.send(player, plugin.lang().get("auctions.remove.error.not-found"), Map.of("id", id));
    }

    private Map<String, String> getPlaceholders(AuctionListing listing) {
        Map<String, String> ph = new HashMap<>();
        ph.put("id", String.valueOf(listing.id()));
        ph.put("amount", String.valueOf(listing.item().getAmount()));
        ph.put("item", getItemName(listing));

        String sellerName = plugin.database().records().getNameFromUuid(listing.sellerUuid());
        ph.put("seller", sellerName != null ? sellerName : "Unknown");

        return ph;
    }

    private String getItemName(AuctionListing listing) {
        if (listing.item().hasItemMeta() && listing.item().getItemMeta().hasDisplayName()) {
            return MiniMessage.miniMessage().serialize(Objects.requireNonNull(listing.item().getItemMeta().displayName()));
        }
        return plugin.itemTranslations().translate(listing.item().getType());
    }
}