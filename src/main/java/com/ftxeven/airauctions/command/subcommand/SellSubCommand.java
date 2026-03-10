package com.ftxeven.airauctions.command.subcommand;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.manager.confirm.ConfirmListingManager;
import com.ftxeven.airauctions.core.model.BlacklistResult;
import com.ftxeven.airauctions.core.model.PendingListing;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.TimeUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SellSubCommand {

    private final AirAuctions plugin;

    public SellSubCommand(AirAuctions plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String label, String[] args) {
        UUID uuid = player.getUniqueId();
        String currencyId = plugin.config().economyDefaultProvider();

        if (args.length < 2) {
            MessageUtil.send(player, plugin.lang().get("errors.incorrect-usage"),
                    Map.of("usage", plugin.config().getSubcommandUsage("sell", label)));
            return;
        }

        if (plugin.config().errorOnExcessArgs()) {
            String amountType = plugin.config().amountType();
            int maxArgs = amountType.equals("DISABLED") ? 2 : 3;

            if (args.length > maxArgs) {
                MessageUtil.send(player, plugin.lang().get("errors.too-many-arguments"),
                        Map.of("usage", plugin.config().getSubcommandUsage("sell", label)));
                return;
            }
        }

        if (plugin.core().auctions().getPendingChatConfirmations().containsKey(uuid)) {
            handleChatConfirmation(player, uuid, currencyId);
            return;
        }

        if (!player.hasPermission("airauctions.bypass.gamemodes")) {
            if (plugin.config().blockedGamemodes().contains(player.getGameMode().name())) {
                MessageUtil.send(player, plugin.lang().get("auctions.sell.error.blocked-gamemode"), Map.of());
                return;
            }
        }

        if (!player.hasPermission("airauctions.bypass.worlds")) {
            if (plugin.config().disabledWorlds().contains(player.getWorld().getName())) {
                MessageUtil.send(player, plugin.lang().get("auctions.sell.error.disabled-world"),
                        Map.of("world", player.getWorld().getName()));
                return;
            }
        }

        if (isLimitReached(player, uuid) || isExpiredLimitReached(player, uuid) || isStillOnCooldown(player, uuid)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.holding-air"), Map.of());
            return;
        }

        if (!player.hasPermission("airauctions.bypass.blacklist")) {
            BlacklistResult res = plugin.core().blacklist().check(item);
            if (res.blocked()) {
                String key = res.reason().equals("not-in-whitelist") ?
                        "auctions.sell.error.not-whitelisted" : "auctions.sell.error.blacklisted";
                MessageUtil.send(player, plugin.lang().get(key), Map.of());
                return;
            }
        }

        if (!plugin.config().allowDamagedItems() && !player.hasPermission("airauctions.bypass.damageditems")) {
            if (item.getItemMeta() instanceof Damageable d && d.hasDamage()) {
                MessageUtil.send(player, plugin.lang().get("auctions.sell.error.damaged-item"), Map.of());
                return;
            }
        }

        Double price = plugin.core().economy().formats().parseAmount(args[1], currencyId);
        if (price == null || price <= 0) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.invalid-price"), Map.of());
            return;
        }

        if (price < plugin.config().minAuctionPrice() || (plugin.config().maxAuctionPrice() != -1 && price > plugin.config().maxAuctionPrice())) {
            double bound = price < plugin.config().minAuctionPrice() ? plugin.config().minAuctionPrice() : plugin.config().maxAuctionPrice();
            String key = price < plugin.config().minAuctionPrice() ? "auctions.sell.error.min-price" : "auctions.sell.error.max-price";
            MessageUtil.send(player, plugin.lang().get(key), Map.of(
                    "min", plugin.core().economy().formats().format(bound, currencyId),
                    "max", plugin.core().economy().formats().format(bound, currencyId),
                    "bound", plugin.core().economy().formats().format(bound, currencyId)
            ));
            return;
        }

        int amount = calculateAmount(player, args, item);
        if (amount == -1) return;

        double fee = plugin.core().economy().calculateListingFee(player, price, currencyId);
        if (fee > 0 && !plugin.core().economy().canAfford(player, currencyId, fee)) {
            MessageUtil.send(player, plugin.lang().get("errors.cannot-afford-fee"),
                    Map.of("fee", plugin.core().economy().formats().format(fee, currencyId)));
            return;
        }

        String itemName = getItemName(item);
        if (!plugin.config().confirmAuction()) {
            processListing(player, item, price, amount, itemName, currencyId, fee);
        } else {
            startConfirmation(player, item, price, amount, itemName, currencyId, fee, uuid);
        }
    }

    private void handleChatConfirmation(Player player, UUID uuid, String currencyId) {
        PendingListing pending = plugin.core().auctions().getPendingChatConfirmations().remove(uuid);
        if (pending == null) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != pending.item().getType() || item.getAmount() < pending.amount()) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.insufficient-item"), Map.of("holding", String.valueOf(item.getAmount())));
            return;
        }

        if (isLimitReached(player, uuid) || isExpiredLimitReached(player, uuid)) return;

        double fee = plugin.core().economy().calculateListingFee(player, pending.price(), currencyId);
        processListing(player, item, pending.price(), pending.amount(), getItemName(item), currencyId, fee);
    }

    private void startConfirmation(Player player, ItemStack item, double price, int amount, String itemName, String currencyId, double fee, UUID uuid) {
        ConfirmListingManager guiManager = plugin.core().gui().get("confirm_listing", ConfirmListingManager.class);

        java.util.Map<String, String> ph = new java.util.HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("item", itemName);
        ph.put("price", plugin.core().economy().formats().format(price, currencyId));
        ph.put("fee", plugin.core().economy().formats().format(fee, currencyId));

        ph.put("raw-price", String.valueOf(price));
        ph.put("currency", currencyId);

        if (guiManager != null && guiManager.isGuiEnabled()) {
            plugin.core().gui().open("confirm_listing", player, ph);
        } else {
            long timestamp = System.currentTimeMillis();
            plugin.core().auctions().getPendingChatConfirmations().put(uuid, new PendingListing(item.clone(), price, amount, timestamp));
            MessageUtil.send(player, plugin.lang().get("auctions.sell.confirmation.request"), ph);

            int expiry = plugin.config().confirmationExpireTime();
            if (expiry > 0) {
                plugin.scheduler().runDelayed(() -> {
                    PendingListing current = plugin.core().auctions().getPendingChatConfirmations().get(uuid);
                    if (current != null && current.timestamp() == timestamp) {
                        plugin.core().auctions().getPendingChatConfirmations().remove(uuid);
                        MessageUtil.send(player, plugin.lang().get("auctions.sell.confirmation.expired"), ph);
                    }
                }, expiry * 20L);
            }
        }
    }

    private void processListing(Player player, ItemStack item, double price, int amount, String itemName, String currencyId, double fee) {
        if (fee > 0) {
            plugin.economy().getProvider(currencyId).withdraw(player, fee);
        }

        int expireSeconds = plugin.config().getExpireTime(player);
        long expiryTime = System.currentTimeMillis() + (expireSeconds * 1000L);

        plugin.core().auctions().listEntry(player, item, price, amount, expiryTime, currencyId);

        MessageUtil.send(player, plugin.lang().get("auctions.sell.success"), Map.of(
                "amount", String.valueOf(amount),
                "item", itemName,
                "price", plugin.core().economy().formats().format(price, currencyId),
                "fee", plugin.core().economy().formats().format(fee, currencyId)
        ));
    }

    private int calculateAmount(Player player, String[] args, ItemStack item) {
        String type = plugin.config().amountType();
        if (type.equals("DISABLED")) return item.getAmount();
        if (args.length >= 3) {
            try {
                int val = Integer.parseInt(args[2]);
                if (val > 0 && val <= item.getAmount()) return val;
                MessageUtil.send(player, plugin.lang().get("auctions.sell.error.insufficient-item"), Map.of("holding", String.valueOf(item.getAmount())));
                return -1;
            } catch (NumberFormatException e) {
                MessageUtil.send(player, plugin.lang().get("auctions.sell.error.invalid-amount"), Map.of());
                return -1;
            }
        }
        if (type.equals("REQUIRED")) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.amount-required"), Map.of());
            return -1;
        }
        return item.getAmount();
    }

    private boolean isLimitReached(Player player, UUID uuid) {
        int limit = plugin.config().getPlayerLimit(player);
        if (limit == Integer.MAX_VALUE) return false;
        if (plugin.core().auctions().getListingCount(uuid) >= limit) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.limit-reached"), Map.of("limit", String.valueOf(limit)));
            return true;
        }
        return false;
    }

    private boolean isExpiredLimitReached(Player player, UUID uuid) {
        int limit = plugin.config().expiredLimit();
        if (limit <= 0) return false;
        if (plugin.core().auctions().getExpiredCount(uuid) >= limit) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.expired-limit-reached"), Map.of("limit", String.valueOf(limit)));
            return true;
        }
        return false;
    }

    private boolean isStillOnCooldown(Player player, UUID uuid) {
        int cooldown = plugin.config().auctionCooldown();
        if (cooldown <= 0) return false;
        Long last = plugin.core().auctions().getLastListingTimes().get(uuid);
        if (last == null) return false;
        long remaining = (long) cooldown - ((System.currentTimeMillis() - last) / 1000L);
        if (remaining > 0) {
            MessageUtil.send(player, plugin.lang().get("auctions.sell.error.cooldown"), Map.of("time", TimeUtil.formatSeconds(plugin, remaining)));
            return true;
        }
        return false;
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return MiniMessage.miniMessage().serialize(Objects.requireNonNull(item.getItemMeta().displayName()));
        }
        return plugin.itemTranslations().translate(item.getType());
    }
}