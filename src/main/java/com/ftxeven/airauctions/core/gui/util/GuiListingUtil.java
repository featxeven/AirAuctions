package com.ftxeven.airauctions.core.gui.util;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.GuiDefinition;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.gui.PageableHolder;
import com.ftxeven.airauctions.core.manager.main.AuctionHouseManager;
import com.ftxeven.airauctions.core.manager.main.AuctionSearchManager;
import com.ftxeven.airauctions.core.manager.player.ExpiredListingsManager;
import com.ftxeven.airauctions.core.manager.player.ListingHistoryManager;
import com.ftxeven.airauctions.core.manager.player.PlayerListingsManager;
import com.ftxeven.airauctions.core.manager.target.TargetHistoryManager;
import com.ftxeven.airauctions.core.manager.target.TargetListingsManager;
import com.ftxeven.airauctions.core.model.AuctionListing;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class GuiListingUtil {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public static void refresh(
            AirAuctions plugin, Player viewer, PageableHolder holder, Inventory inv,
            GuiDefinition def, List<Integer> auctionSlots, GuiItem template,
            Supplier<List<AuctionListing>> dataSupplier, BiConsumer<Player, Map<String, String>> reopenLogic
    ) {
        refresh(plugin, viewer, holder, inv, def, auctionSlots, template, null, Integer.MAX_VALUE, dataSupplier, reopenLogic);
    }

    public static void refresh(
            AirAuctions plugin, Player viewer, PageableHolder holder, Inventory inv,
            GuiDefinition def, List<Integer> auctionSlots, GuiItem template,
            GuiItem availableTemplate, int limit,
            Supplier<List<AuctionListing>> dataSupplier, BiConsumer<Player, Map<String, String>> reopenLogic
    ) {
        List<AuctionListing> auctions = dataSupplier.get();
        int slotSize = Math.max(1, auctionSlots.size());
        int newTotalPages = Math.max(1, (auctions.size() + slotSize - 1) / slotSize);
        int validatedPage = Math.min(Math.max(0, holder.page()), newTotalPages - 1);

        if (newTotalPages != holder.totalPages() || validatedPage != holder.page()) {
            Map<String, String> ctx = holder.asContext();
            ctx.put("page", String.valueOf(validatedPage + 1));
            reopenLogic.accept(viewer, ctx);
        } else {
            performSmoothUpdate(plugin, viewer, holder, inv, def, auctionSlots, auctions, template, availableTemplate, limit, validatedPage, newTotalPages);
        }
    }

    private static void performSmoothUpdate(AirAuctions plugin, Player viewer, PageableHolder holder,
                                            Inventory inv, GuiDefinition def, List<Integer> slots,
                                            List<AuctionListing> auctions, GuiItem template,
                                            GuiItem availableTemplate, int limit, int page, int totalPages) {
        if (holder instanceof ExpiredListingsManager.ExpiredHolder expiredHolder) {
            expiredHolder.setPage(page);
            expiredHolder.setTotalPages(totalPages);
        } else if (holder instanceof AuctionHouseManager.Holder ahHolder) {
            ahHolder.setPage(page);
            ahHolder.setTotalPages(totalPages);
        } else if (holder instanceof AuctionSearchManager.SearchHolder searchHolder) {
            searchHolder.setPage(page);
            searchHolder.setTotalPages(totalPages);
        } else if (holder instanceof PlayerListingsManager.PlayerHolder playerHolder) {
            playerHolder.setPage(page);
            playerHolder.setTotalPages(totalPages);
        } else if (holder instanceof TargetListingsManager.TargetHolder targetHolder) {
            targetHolder.setPage(page);
            targetHolder.setTotalPages(totalPages);
        }
        else if (holder instanceof ListingHistoryManager.HistoryHolder historyHolder) {
            historyHolder.setPage(page);
            historyHolder.setTotalPages(totalPages);
        } else if (holder instanceof TargetHistoryManager.TargetHistoryHolder targetHistoryHolder) {
            targetHistoryHolder.setPage(page);
            targetHistoryHolder.setTotalPages(totalPages);
        }

        holder.displayedListings().clear();
        int startIndex = page * slots.size();

        BitSet slotSet = new BitSet();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (slot >= 0) {
                slotSet.set(slot);
                int absIdx = startIndex + i;
                if (absIdx < auctions.size()) {
                    holder.displayedListings().put(slot, auctions.get(absIdx).id());
                }
            }
        }

        Map<String, String> ph = holder.asContext();
        ph.put("page", String.valueOf(page + 1));
        ph.put("pages", String.valueOf(totalPages));
        ph.put("filter", holder.filter());
        ph.put("sort", holder.sort());

        GuiSlotMapper.fill(plugin, inv, def, viewer, ph, auctions, slotSet, page, template, availableTemplate, limit);
        viewer.updateInventory();
    }

    public static void applySorting(AirAuctions plugin, List<AuctionListing> auctions, String key) {
        if (auctions == null || auctions.size() < 2) return;

        String activeKey = (key == null || key.equalsIgnoreCase("none") || key.isEmpty())
                ? plugin.config().getDefaultSortingKey()
                : key;

        switch (activeKey.toLowerCase(Locale.ROOT)) {
            case "oldest-date" -> auctions.sort(Comparator.comparingLong(AuctionListing::createdAt));
            case "highest-price" -> auctions.sort((a, b) -> Double.compare(b.price(), a.price()));
            case "lowest-price" -> auctions.sort(Comparator.comparingDouble(AuctionListing::price));
            case "alphabetical" -> {
                IdentityHashMap<ItemStack, String> nameCache = new IdentityHashMap<>(auctions.size());
                auctions.sort((a, b) -> {
                    String nameA = nameCache.computeIfAbsent(a.item(), GuiListingUtil::getSortName);
                    String nameB = nameCache.computeIfAbsent(b.item(), GuiListingUtil::getSortName);
                    return nameA.compareToIgnoreCase(nameB);
                });
            }
            case "amount" -> auctions.sort((a, b) -> Integer.compare(b.item().getAmount(), a.item().getAmount()));
            default -> auctions.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        }
    }

    private static String getSortName(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return PLAIN.serialize(Objects.requireNonNull(meta.displayName()));
            }
        }
        return item != null ? item.getType().name() : "";
    }

    public static String cycle(List<String> options, String current, boolean forward) {
        if (options == null || options.isEmpty()) return current;
        int i = -1;
        for (int j = 0; j < options.size(); j++) {
            if (options.get(j).equalsIgnoreCase(current)) {
                i = j;
                break;
            }
        }
        if (i == -1) i = 0;
        int nextIdx = forward ? (i + 1) % options.size() : (i - 1 + options.size()) % options.size();
        return options.get(nextIdx);
    }

    public static boolean handleItemAction(AirAuctions plugin, GuiItem item, Player viewer, ClickType click, PageableHolder holder) {
        if (item == null) return false;

        if (plugin.core().gui().cooldown().isOnCooldown(viewer, item)) {
            plugin.core().gui().cooldown().sendCooldownMessage(viewer, item);
            return false;
        }
        plugin.core().gui().cooldown().applyCooldown(viewer, item);

        Map<String, String> context = holder.asContext();
        List<String> actions = item.getActionsForClick(click, viewer, context);
        if (actions != null && !actions.isEmpty()) {
            plugin.core().gui().action().executeAll(actions, viewer, context);
            return true;
        }

        return false;
    }
}