package com.ftxeven.airauctions.core.gui.util;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.GuiDefinition;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.core.gui.ItemComponent;
import com.ftxeven.airauctions.core.gui.PageableHolder;
import com.ftxeven.airauctions.core.model.AuctionHistory;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class GuiSlotMapper {
    private static final Map<UUID, String> NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> COMPONENT_CACHE = new ConcurrentHashMap<>();

    private static final Pattern EXPIRE_PAT = Pattern.compile("%expire%");
    private static final Pattern PURGE_PAT = Pattern.compile("%purge%");

    public static void fill(AirAuctions plugin, Inventory inv, GuiDefinition def, Player viewer,
                            Map<String, String> ph, List<AuctionListing> auctions, BitSet slotSet, int page,
                            GuiItem auctionTemplate, GuiItem availableTemplate, int limit) {

        ConfigurationSection btnSec = def.config().getConfigurationSection("buttons");
        ConfigurationSection itemSec = def.config().getConfigurationSection("items");
        InventoryHolder holder = inv.getHolder();

        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            if (slotSet.get(i)) continue;

            ItemStack finalStack = null;
            GuiItem buttonItem = findItem(btnSec, def, i, viewer, ph);

            if (buttonItem != null && !isButtonHidden(buttonItem.key(), holder, def)) {
                finalStack = switch (buttonItem.key()) {
                    case "filter-by" -> buildFilterButton(plugin, btnSec.getConfigurationSection("filter-by"), buttonItem, viewer, ph);
                    case "sort-by" -> buildSortButton(plugin, btnSec.getConfigurationSection("sort-by"), buttonItem, viewer, ph);
                    default -> buttonItem.buildStack(viewer, ph, plugin);
                };
            }

            if (finalStack == null) {
                GuiItem customItem = findItem(itemSec, def, i, viewer, ph);
                if (customItem != null) finalStack = customItem.buildStack(viewer, ph, plugin);
            }
            inv.setItem(i, finalStack);
        }

        populateAuctions(plugin, inv, viewer, auctions, slotSet, page, ph, auctionTemplate, availableTemplate, limit, itemSec, def);
    }

    public static void invalidateCache() {
        NAME_CACHE.clear();
        COMPONENT_CACHE.clear();
    }

    public static boolean isButtonHidden(String key, InventoryHolder holder, GuiDefinition def) {
        if (!key.equals("next-page") && !key.equals("previous-page")) return false;
        if (def.config().getBoolean("always-show-buttons", false)) return false;

        if (holder instanceof PageableHolder pageable) {
            return key.equals("next-page") ? pageable.page() >= pageable.totalPages() - 1 : pageable.page() <= 0;
        }
        return false;
    }

    private static void populateAuctions(AirAuctions plugin, Inventory inv, Player viewer, List<AuctionListing> auctions,
                                         BitSet slotSet, int page, Map<String, String> ph, GuiItem template,
                                         GuiItem availableTemplate, int limit, ConfigurationSection itemSec, GuiDefinition def) {

        int slotIdx = 0;
        int start = page * slotSet.cardinality();
        long now = System.currentTimeMillis();

        for (int slot = slotSet.nextSetBit(0); slot >= 0; slot = slotSet.nextSetBit(slot + 1)) {
            int idx = start + (slotIdx++);

            if (idx < auctions.size()) {
                AuctionListing listing = auctions.get(idx);
                ItemStack icon = buildAuctionStack(plugin, viewer, listing, ph, template);
                updateTimeRemaining(icon, listing, now, plugin);
                inv.setItem(slot, icon);
            } else if (idx < limit && availableTemplate != null) {
                inv.setItem(slot, availableTemplate.buildStack(viewer, ph, plugin));
            } else {
                GuiItem fallbackItem = findItem(itemSec, def, slot, viewer, ph);
                inv.setItem(slot, fallbackItem != null ? fallbackItem.buildStack(viewer, ph, plugin) : null);
            }
        }
    }

    private static void updateTimeRemaining(ItemStack icon, AuctionListing listing, long now, AirAuctions plugin) {
        ItemMeta meta = icon.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<Component> lore = meta.lore();
        if (lore == null) return;

        long expDiff = Math.max(0, (listing.expiryAt() - now) / 1000L) + 1;
        String expireStr = TimeUtil.formatSeconds(plugin, expDiff);

        int purgeSeconds = plugin.config().purgeTime();
        String purgeStr;
        if (purgeSeconds <= 0) {
            purgeStr = plugin.lang().get("placeholders.purge-never");
        } else {
            long pDiff = Math.max(0, (listing.expiryAt() + (purgeSeconds * 1000L) - now) / 1000L) + 1;
            purgeStr = TimeUtil.formatSeconds(plugin, pDiff);
        }

        Component expireComp = getCachedMini(expireStr);
        Component purgeComp = getCachedMini(purgeStr);

        List<Component> updatedLore = new ArrayList<>();
        for (Component line : lore) {
            updatedLore.add(line.replaceText(t -> t.match(EXPIRE_PAT).replacement(expireComp))
                    .replaceText(t -> t.match(PURGE_PAT).replacement(purgeComp)));
        }

        meta.lore(updatedLore);
        icon.setItemMeta(meta);
    }

    private static GuiItem findItem(ConfigurationSection sec, GuiDefinition def, int slot, Player viewer, Map<String, String> ph) {
        if (sec == null) return null;
        GuiItem bestMatch = null;
        int bestPriorityValue = Integer.MAX_VALUE;

        for (String key : sec.getKeys(false)) {
            GuiItem i = def.items().get(key);
            if (i == null || !i.slots().contains(slot)) continue;

            Map<Integer, com.ftxeven.airauctions.core.gui.ItemPriority> priorities = i.priorities();
            if (priorities.isEmpty()) {
                if (bestMatch == null) bestMatch = i;
                continue;
            }

            for (var entry : priorities.entrySet()) {
                if (entry.getKey() < bestPriorityValue && entry.getValue().matches(viewer, ph)) {
                    bestPriorityValue = entry.getKey();
                    bestMatch = i;
                }
            }
        }
        return bestMatch;
    }

    private static ItemStack buildAuctionStack(AirAuctions plugin, Player viewer, AuctionListing listing, Map<String, String> globalPh, GuiItem template) {
        ItemComponent builder = new ItemComponent(listing.item());

        var sellerProfile = plugin.core().profiles().get(listing.sellerUuid());
        var skinData = sellerProfile != null ? sellerProfile.getSkinData() : null;

        if (template != null) {
            builder.customModelData(template.customModelData())
                    .damage(template.damage())
                    .glow(template.glow())
                    .flags(template.flags())
                    .hideTooltip(template.hideTooltip())
                    .tooltipStyle(template.tooltipStyle())
                    .itemModel(template.itemModel());

            if (template.headOwner() != null) {
                builder.skullOwner(template.headOwner(), viewer, skinData);
            }
            if (template.enchants() != null && !template.enchants().isEmpty()) builder.enchants(template.enchants());
        }

        var provider = plugin.economy().getProvider(listing.currencyId());

        String seller = NAME_CACHE.computeIfAbsent(listing.sellerUuid(), uuid -> plugin.core().profiles().getName(uuid));
        String price = plugin.core().economy().formats().format(listing.price(), listing.currencyId());
        String amountStr = String.valueOf(listing.item().getAmount());
        String idStr = String.valueOf(listing.id());

        String itemFilterKey = plugin.filters().getCategory(listing.item());
        String itemFilterDisplayName = plugin.filters().getDisplayName(itemFilterKey);

        List<Component> finalLore = new ArrayList<>();
        if (template != null && template.rawLore() != null) {
            double playerBalance = provider.getBalance(viewer);
            boolean isShulker = listing.item().getType().name().endsWith("SHULKER_BOX");

            ItemMeta sourceMeta = listing.item().getItemMeta();

            for (String line : template.rawLore()) {
                if (line.contains("%lore%")) {
                    if (sourceMeta != null && sourceMeta.hasLore()) {
                        finalLore.addAll(Objects.requireNonNull(sourceMeta.lore()));
                    }
                } else if (line.startsWith("buy:")) {
                    if (playerBalance >= listing.price()) {
                        finalLore.add(processLine(line.substring(4).trim(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer));
                    }
                } else if (line.startsWith("preview:")) {
                    if (isShulker) finalLore.add(processLine(line.substring(8).trim(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer));
                } else {
                    finalLore.add(processLine(line, seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer));
                }
            }
        }

        builder.lore(finalLore);

        if (listing.item().getItemMeta() != null && !listing.item().getItemMeta().hasDisplayName()) {
            if (template != null && template.rawName() != null) {
                builder.name(processLine(template.rawName(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer));
            }
        }

        return builder.build();
    }

    public static ItemStack buildHistoryStack(AirAuctions plugin, Player viewer, AuctionHistory log, Map<String, String> globalPh, GuiItem template) {
        ItemComponent builder = new ItemComponent(log.item());

        var sellerProfile = plugin.core().profiles().get(log.sellerUuid());
        var skinData = sellerProfile != null ? sellerProfile.getSkinData() : null;

        if (template != null) {
            builder.customModelData(template.customModelData())
                    .damage(template.damage())
                    .glow(template.glow())
                    .flags(template.flags())
                    .hideTooltip(template.hideTooltip())
                    .tooltipStyle(template.tooltipStyle())
                    .itemModel(template.itemModel());

            if (template.headOwner() != null) {
                builder.skullOwner(template.headOwner(), viewer, skinData);
            }
            if (template.enchants() != null && !template.enchants().isEmpty()) builder.enchants(template.enchants());
        }

        String seller = plugin.core().profiles().getName(log.sellerUuid());
        String buyer = plugin.core().profiles().getName(log.buyerUuid());
        String price = plugin.core().economy().formats().format(log.price(), log.currencyId());
        String amountStr = String.valueOf(log.item().getAmount());
        String idStr = String.valueOf(log.id());

        String dateStr = TimeUtil.formatDate(plugin, log.soldAt());
        String timeStr = Instant.ofEpochMilli(log.soldAt())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"));

        String itemFilterKey = plugin.filters().getCategory(log.item());
        String itemFilterDisplayName = plugin.filters().getDisplayName(itemFilterKey);

        List<Component> finalLore = new ArrayList<>();
        if (template != null && template.rawLore() != null) {
            boolean isShulker = log.item().getType().name().endsWith("SHULKER_BOX");
            ItemMeta sourceMeta = log.item().getItemMeta();

            for (String line : template.rawLore()) {
                if (line.contains("%lore%")) {
                    if (sourceMeta != null && sourceMeta.hasLore()) finalLore.addAll(Objects.requireNonNull(sourceMeta.lore()));
                } else if (line.startsWith("preview:")) {
                    if (isShulker) finalLore.add(processHistoryLine(line.substring(8).trim(), seller, buyer, price, idStr, amountStr, dateStr, timeStr, itemFilterDisplayName, globalPh, viewer));
                } else {
                    finalLore.add(processHistoryLine(line, seller, buyer, price, idStr, amountStr, dateStr, timeStr, itemFilterDisplayName, globalPh, viewer));
                }
            }
        }

        builder.lore(finalLore);

        if (log.item().getItemMeta() != null && !log.item().getItemMeta().hasDisplayName()) {
            if (template != null && template.rawName() != null) {
                builder.name(processHistoryLine(template.rawName(), seller, buyer, price, idStr, amountStr, dateStr, timeStr, itemFilterDisplayName, globalPh, viewer));
            }
        }

        return builder.build();
    }

    private static Component processLine(String line, String seller, String price, String id, String amount, String itemFilter, Map<String, String> ph, Player viewer) {
        String processed = line.replace("%filter%", itemFilter)
                .replace("%seller%", seller)
                .replace("%price%", price)
                .replace("%id%", id)
                .replace("%amount%", amount);

        for (Map.Entry<String, String> entry : ph.entrySet()) {
            String key = "%" + entry.getKey() + "%";
            if (processed.contains(key)) {
                processed = processed.replace(key, entry.getValue());
            }
        }

        return MessageUtil.mini(viewer, "<!italic>" + processed, ph);
    }

    private static Component processHistoryLine(String line, String seller, String buyer, String price, String id, String amount, String date, String time, String itemFilter, Map<String, String> ph, Player viewer) {
        String processed = line.replace("%filter%", itemFilter)
                .replace("%seller%", seller)
                .replace("%buyer%", buyer)
                .replace("%price%", price)
                .replace("%id%", id)
                .replace("%amount%", amount)
                .replace("%date%", date)
                .replace("%time%", time);

        return MessageUtil.mini(viewer, "<!italic>" + processed, ph);
    }

    private static Component getCachedMini(String text) {
        return COMPONENT_CACHE.computeIfAbsent(text, t -> MessageUtil.mini(null, "<!italic>" + t, Map.of()));
    }

    private static ItemStack buildFilterButton(AirAuctions plugin, ConfigurationSection sec, GuiItem item, Player viewer, Map<String, String> ph) {
        ItemStack stack = item.buildStack(viewer, ph, plugin);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String current = ph.getOrDefault("filter", "all");
        boolean showList = sec != null && sec.getBoolean("show-list", true);
        String selFmt = sec != null ? sec.getString("format.selected", "<green>⏵ %name%") : "<green>⏵ %name%";
        String unselFmt = sec != null ? sec.getString("format.unselected", "<gray>  %name%") : "<gray>  %name%";

        List<Component> newLore = new ArrayList<>();
        for (String line : item.rawLore()) {
            if (line.contains("%filter%")) {
                if (showList) {
                    for (String key : plugin.filters().getOrderedCategoryKeys()) {
                        String name = plugin.filters().getDisplayName(key);
                        boolean isSelected = key.equalsIgnoreCase(current);
                        newLore.add(MessageUtil.mini(viewer, "<!italic>" + (isSelected ? selFmt : unselFmt).replace("%name%", name), ph));
                    }
                } else {
                    String currentDisplayName = plugin.filters().getDisplayName(current);
                    newLore.add(MessageUtil.mini(viewer, "<!italic>" + line.replace("%filter%", currentDisplayName), ph));
                }
            } else {
                newLore.add(MessageUtil.mini(viewer, "<!italic>" + line, ph));
            }
        }
        meta.lore(newLore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack buildSortButton(AirAuctions plugin, ConfigurationSection btnSec, GuiItem item, Player viewer, Map<String, String> ph) {
        ItemStack stack = item.buildStack(viewer, ph, plugin);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String current = ph.getOrDefault("sort", plugin.config().getDefaultSortingKey());
        boolean showList = btnSec != null && btnSec.getBoolean("show-list", true);
        String selFmt = btnSec != null ? btnSec.getString("format.selected", "<green>⏵ %name%") : "<green>⏵ %name%";
        String unselFmt = btnSec != null ? btnSec.getString("format.unselected", "<gray>  %name%") : "<gray>  %name%";

        List<Component> newLore = new ArrayList<>();
        for (String line : item.rawLore()) {
            if (line.contains("%sort%")) {
                if (showList) {
                    for (String key : plugin.config().getSortingKeys()) {
                        String name = plugin.config().getSortingDisplayName(key);
                        boolean isSelected = key.equalsIgnoreCase(current);
                        newLore.add(MessageUtil.mini(viewer, "<!italic>" + (isSelected ? selFmt : unselFmt).replace("%name%", name), ph));
                    }
                } else {
                    String currentDisplayName = plugin.config().getSortingDisplayName(current);
                    newLore.add(MessageUtil.mini(viewer, "<!italic>" + line.replace("%sort%", currentDisplayName), ph));
                }
            } else {
                newLore.add(MessageUtil.mini(viewer, "<!italic>" + line, ph));
            }
        }
        meta.lore(newLore);
        stack.setItemMeta(meta);
        return stack;
    }
}