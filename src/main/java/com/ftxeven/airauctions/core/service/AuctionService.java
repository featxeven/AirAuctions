package com.ftxeven.airauctions.core.service;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.core.model.PendingListing;
import com.ftxeven.airauctions.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AuctionService {
    private final AirAuctions plugin;
    private final Map<Integer, AuctionListing> activeListings = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> sellerMap = new ConcurrentHashMap<>();
    private final Map<UUID, PendingListing> pendingChatConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingRemoveConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastListingTimes = new ConcurrentHashMap<>();
    public enum PurchaseResult { SUCCESS, NOT_FOUND, CANNOT_AFFORD, OWN_ITEM, INVENTORY_FULL }

    public AuctionService(AirAuctions plugin) { this.plugin = plugin; }

    public void start() {
        activeListings.clear();
        sellerMap.clear();
        plugin.database().listings().getAllActive(listings -> {
            for (AuctionListing l : listings) {
                activeListings.put(l.id(), l);
                sellerMap.computeIfAbsent(l.sellerUuid(), k -> ConcurrentHashMap.newKeySet()).add(l.id());
            }
        });
    }

    public void clearPlayerData(UUID uuid) {
        pendingChatConfirmations.remove(uuid);
        lastListingTimes.remove(uuid);
        pendingRemoveConfirmations.remove(uuid);
    }

    public void processExpiration(AuctionListing listing) {
        activeListings.remove(listing.id());
        Set<Integer> set = sellerMap.get(listing.sellerUuid());
        if (set != null) set.remove(listing.id());
        plugin.scheduler().runAsync(() -> plugin.database().listings().markAsExpired(listing.id()));
    }

    public void forceDeleteListing(AuctionListing listing) {
        if (listing == null) return;

        activeListings.remove(listing.id());
        Set<Integer> ids = sellerMap.get(listing.sellerUuid());
        if (ids != null) {
            ids.remove(listing.id());
        }

        plugin.scheduler().runAsync(() -> plugin.database().listings().deleteListingSync(listing.id()));
    }

    public List<AuctionListing> getActive(String filter) {
        String target = (filter == null || filter.isBlank()) ? "All" : filter;
        long now = System.currentTimeMillis();

        return activeListings.values().stream()
                .filter(l -> {
                    if (l.expiryAt() != -1 && l.expiryAt() < now) {
                        processExpiration(l);
                        return false;
                    }

                    return target.equalsIgnoreCase("All") ||
                            plugin.filters().getCategory(l.item()).equalsIgnoreCase(target);
                })
                .sorted(Comparator.comparingInt(AuctionListing::id).reversed())
                .collect(Collectors.toList());
    }

    public List<AuctionListing> getBySeller(UUID sellerUuid) {
        Set<Integer> ids = sellerMap.get(sellerUuid);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        long now = System.currentTimeMillis();
        List<AuctionListing> listings = new ArrayList<>();

        for (int id : ids) {
            AuctionListing l = activeListings.get(id);
            if (l == null) continue;

            if (l.expiryAt() != -1 && l.expiryAt() < now) {
                processExpiration(l);
                continue;
            }
            listings.add(l);
        }
        return listings;
    }

    public void checkExpirations(UUID sellerUuid) {
        Set<Integer> ids = sellerMap.get(sellerUuid);
        if (ids == null || ids.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (int id : ids) {
            AuctionListing l = activeListings.get(id);
            if (l != null && l.expiryAt() != -1 && l.expiryAt() < now) {
                processExpiration(l);
            }
        }
    }

    public PurchaseResult buyEntry(Player buyer, int id) {
        AuctionListing listing = activeListings.get(id);
        if (listing == null) return PurchaseResult.NOT_FOUND;
        if (listing.isTimedOut()) { processExpiration(listing); return PurchaseResult.NOT_FOUND; }

        if (!plugin.config().purchaseOwn() && listing.sellerUuid().equals(buyer.getUniqueId())) return PurchaseResult.OWN_ITEM;
        if (!plugin.core().economy().canAfford(buyer, listing.currencyId(), listing.price())) return PurchaseResult.CANNOT_AFFORD;
        if (!plugin.config().dropItemsWhenFull() && buyer.getInventory().firstEmpty() == -1) return PurchaseResult.INVENTORY_FULL;

        activeListings.remove(id);
        Set<Integer> set = sellerMap.get(listing.sellerUuid());
        if (set != null) set.remove(id);
        plugin.database().listings().processPurchase(id, buyer.getUniqueId(), listing.sellerUuid(), listing.item(), listing.price(), listing.currencyId());
        return PurchaseResult.SUCCESS;
    }

    public void listEntry(Player seller, ItemStack item, double price, int amount, long expiry, String currencyId) {
        ItemStack toStore = item.clone();
        toStore.setAmount(amount);

        item.setAmount(item.getAmount() - amount);

        UUID uuid = seller.getUniqueId();
        long now = System.currentTimeMillis();
        lastListingTimes.put(uuid, now);

        plugin.scheduler().runAsync(() -> {
            int id = plugin.database().listings().createListingSync(uuid, toStore, price, expiry, currencyId);
            if (id != -1) {
                AuctionListing l = new AuctionListing(id, uuid, toStore, price, currencyId, now, expiry);
                activeListings.put(id, l);
                sellerMap.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(id);

                plugin.scheduler().runTask(() -> {
                    String broadcastMsg = plugin.lang().get("auctions.sell.success-broadcast");
                    if (broadcastMsg.isEmpty()) return;

                    Map<String, String> ph = Map.of(
                            "seller", seller.getName(),
                            "amount", String.valueOf(amount),
                            "item", plugin.itemTranslations().translate(toStore.getType()),
                            "price", plugin.core().economy().formats().format(price, currencyId)
                    );

                    boolean broadcastToSelf = plugin.config().broadcastToSelf();

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!broadcastToSelf && online.getUniqueId().equals(uuid)) {
                            continue;
                        }
                        MessageUtil.send(online, broadcastMsg, ph);
                    }
                });
            }
        });
    }

    public AuctionListing getActiveListing(int id) {
        AuctionListing l = activeListings.get(id);
        if (l != null && l.isTimedOut()) {
            processExpiration(l);
            return null;
        }
        return l;
    }

    public int getListingCount(UUID uuid) {
        Set<Integer> set = sellerMap.get(uuid);
        return set == null ? 0 : set.size();
    }

    public boolean cancelEntry(Player player, int id, boolean deletePermanently) {
        AuctionListing l = activeListings.get(id);
        if (l == null || !l.sellerUuid().equals(player.getUniqueId())) return false;

        activeListings.remove(id);
        Set<Integer> set = sellerMap.get(l.sellerUuid());
        if (set != null) set.remove(id);

        plugin.scheduler().runAsync(() -> {
            if (deletePermanently) {
                plugin.database().listings().deleteListingSync(id);
            } else {
                plugin.database().listings().markAsExpired(id);
            }
        });
        return true;
    }

    public void finalizePurchase(Player buyer, AuctionListing listing) {
        double profit = plugin.core().economy().processTransaction(buyer, listing.sellerUuid(), listing.price(), listing.currencyId());

        Map<String, String> ph = Map.of(
                "amount", String.valueOf(listing.item().getAmount()),
                "item", plugin.itemTranslations().translate(listing.item().getType()),
                "price", plugin.core().economy().formats().format(listing.price(), listing.currencyId()),
                "profit", plugin.core().economy().formats().format(profit, listing.currencyId()),
                "buyer", buyer.getName()
        );

        MessageUtil.send(buyer, plugin.lang().get("auctions.buy.success"), ph);

        Map<Integer, org.bukkit.inventory.ItemStack> overflow = buyer.getInventory().addItem(listing.item().clone());
        if (!overflow.isEmpty()) {
            overflow.values().forEach(item -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), item));
            MessageUtil.send(buyer, plugin.lang().get("auctions.buy.inventory-full-dropped"), ph);
        }

        Player seller = org.bukkit.Bukkit.getPlayer(listing.sellerUuid());
        if (seller != null && seller.isOnline()) {
            MessageUtil.send(seller, plugin.lang().get("auctions.sell.sold"), ph);
        }
    }

    public void finalizeCancellation(Player viewer, AuctionListing listing) {
        Map<String, String> ph = Map.of(
                "amount", String.valueOf(listing.item().getAmount()),
                "item", plugin.itemTranslations().translate(listing.item().getType())
        );

        Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(listing.item().clone());
        if (!overflow.isEmpty()) {
            overflow.values().forEach(i -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), i));
            MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.inventory-full-dropped"), ph);
        }

        MessageUtil.send(viewer, plugin.lang().get("auctions.cancel.success"), ph);
    }

    public AuctionListing getExpiredListing(int id) {
        return plugin.database().listings().getExpiredByIdSync(id);
    }

    public boolean collectExpired(Player player, int id) {
        AuctionListing listing = getExpiredListing(id);
        if (listing == null || !listing.sellerUuid().equals(player.getUniqueId())) {
            return false;
        }

        return plugin.database().listings().deleteListingSync(id);
    }

    public List<AuctionListing> getExpiredBySeller(UUID sellerUuid) {
        int purgeSeconds = plugin.config().purgeTime();
        if (purgeSeconds > 0) {
            plugin.database().listings().deletePurgedSync(sellerUuid, purgeSeconds);
        }

        List<AuctionListing> list = new ArrayList<>(plugin.database().listings().getExpiredSync(sellerUuid));

        list.sort(Comparator.comparingLong(AuctionListing::expiryAt).reversed());

        return list;
    }

    public int getExpiredCount(UUID uuid) {
        return plugin.database().listings().getExpiredCountSync(uuid);
    }
    public int getTotalActiveCount() { return activeListings.size(); }

    public Map<UUID, PendingListing> getPendingChatConfirmations() { return pendingChatConfirmations; }
    public Map<UUID, Integer> getPendingRemoveConfirmations() { return pendingRemoveConfirmations; }
    public Map<UUID, Long> getLastListingTimes() { return lastListingTimes; }
    public Collection<AuctionListing> getCache() { return activeListings.values(); }
}