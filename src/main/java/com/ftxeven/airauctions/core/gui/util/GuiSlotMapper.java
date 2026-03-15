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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
                if (fallbackItem != null) {
                    inv.setItem(slot, fallbackItem.buildStack(viewer, ph, plugin));
                } else {
                    inv.setItem(slot, null);
                }
            }
        }
    }

    private static void updateTimeRemaining(ItemStack icon, AuctionListing listing, long now, AirAuctions plugin) {
        ItemMeta meta = icon.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<Component> lore = meta.lore();
        if (lore == null) return;

        String expireStr;
        if (listing.expiryAt() == -1L) {
            expireStr = plugin.lang().get("placeholders.expire-never");
        } else {
            long expDiff = Math.max(0, (listing.expiryAt() - now) / 1000L) + 1;
            expireStr = TimeUtil.formatSeconds(plugin, expDiff);
        }

        int purgeSeconds = plugin.config().purgeTime();
        String purgeStr;
        if (purgeSeconds <= 0 || listing.expiryAt() == -1L) {
            purgeStr = plugin.lang().get("placeholders.purge-never");
        } else {
            long pDiff = Math.max(0, (listing.expiryAt() + (purgeSeconds * 1000L) - now) / 1000L) + 1;
            purgeStr = TimeUtil.formatSeconds(plugin, pDiff);
        }

        Component expireComp = getCachedMini(expireStr);
        Component purgeComp = getCachedMini(purgeStr);

        List<Component> updatedLore = new ArrayList<>(lore.size());
        for (Component line : lore) {
            updatedLore.add(line.replaceText(t -> t.match(EXPIRE_PAT).replacement(expireComp))
                    .replaceText(t -> t.match(PURGE_PAT).replacement(purgeComp)));
        }

        meta.lore(updatedLore);
        icon.setItemMeta(meta);
    }

    private static GuiItem findItem(ConfigurationSection sec, GuiDefinition def, int slot, Player viewer, Map<String, String> ph) {
        if (sec == null) return null;

        for (String key : sec.getKeys(false)) {
            GuiItem i = def.items().get(key);
            if (i == null || !i.slots().contains(slot)) continue;

            if (i.priorities().isEmpty()) {
                return i;
            }

            for (var priority : i.priorities().values()) {
                if (priority.matches(viewer, ph)) {
                    return i;
                }
            }

            if (i.materialStr() != null) {
                return i;
            }
        }
        return null;
    }

    private static ItemStack buildAuctionStack(AirAuctions plugin, Player viewer, AuctionListing listing, Map<String, String> globalPh, GuiItem template) {
        ItemStack original = listing.item();
        ItemComponent builder = new ItemComponent(original);

        var sellerProfile = plugin.core().profiles().get(listing.sellerUuid());
        var skinData = sellerProfile != null ? sellerProfile.getSkinData() : null;

        if (template != null) {
            if (template.customModelData() != null && template.customModelData() > 0) {
                builder.customModelData(template.customModelData());
            }

            if (template.damage() != null && template.damage() > 0) {
                builder.damage(template.damage());
            }

            if (template.itemModel() != null && !template.itemModel().isEmpty()) {
                builder.itemModel(template.itemModel());
            }

            builder.glow(template.glow())
                    .flags(template.flags())
                    .hideTooltip(template.hideTooltip())
                    .tooltipStyle(template.tooltipStyle());

            if (template.headOwner() != null) builder.skullOwner(template.headOwner(), viewer, skinData);
            if (template.enchants() != null && !template.enchants().isEmpty()) builder.enchants(template.enchants());
        }

        var provider = plugin.economy().getProvider(listing.currencyId());
        String seller = NAME_CACHE.computeIfAbsent(listing.sellerUuid(), uuid -> plugin.core().profiles().getName(uuid));
        String price = plugin.core().economy().formats().format(listing.price(), listing.currencyId());
        String amountStr = String.valueOf(original.getAmount());
        String idStr = String.valueOf(listing.id());
        String itemFilterKey = plugin.filters().getCategory(original);
        String itemFilterDisplayName = plugin.filters().getDisplayName(itemFilterKey);

        List<Component> finalLore = new ArrayList<>();
        boolean skip = plugin.config().skipEmptyLines();
        boolean pendingEmpty = false;

        if (template != null && template.rawLore() != null) {
            double playerBalance = provider.getBalance(viewer);
            boolean isShulker = original.getType().name().endsWith("SHULKER_BOX");
            boolean isOwner = viewer.getUniqueId().equals(listing.sellerUuid());
            ItemMeta sourceMeta = original.getItemMeta();

            for (String line : template.rawLore()) {
                if (line.contains("%lore%")) {
                    if (sourceMeta != null && sourceMeta.hasLore()) {
                        List<Component> sourceLore = Objects.requireNonNull(sourceMeta.lore());
                        if (!sourceLore.isEmpty()) {
                            if (pendingEmpty && !finalLore.isEmpty()) finalLore.add(Component.empty());
                            finalLore.addAll(sourceLore);
                            pendingEmpty = false;
                        }
                    }
                    continue;
                }

                Component comp = null;
                if (line.startsWith("buy:")) {
                    if (playerBalance >= listing.price()) comp = processLine(line.substring(4).trim(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer);
                } else if (line.startsWith("view-player:")) {
                    if (!isOwner) comp = processLine(line.substring(12).trim(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer);
                } else if (line.startsWith("preview:")) {
                    if (isShulker) comp = processLine(line.substring(8).trim(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer);
                } else {
                    comp = processLine(line, seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer);
                }

                if (comp != null) {
                    if (skip && isPlainEmpty(comp)) {
                        pendingEmpty = true;
                    } else {
                        if (pendingEmpty && !finalLore.isEmpty()) finalLore.add(Component.empty());
                        finalLore.add(comp);
                        pendingEmpty = false;
                    }
                }
            }
        }

        builder.lore(finalLore);

        ItemMeta meta = original.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            if (template != null && template.rawName() != null) {
                builder.name(processLine(template.rawName(), seller, price, idStr, amountStr, itemFilterDisplayName, globalPh, viewer));
            }
        } else {
            builder.name(meta.displayName());
        }

        return builder.build();
    }

    public static ItemStack buildHistoryStack(AirAuctions plugin, Player viewer, AuctionHistory log, Map<String, String> globalPh, GuiItem template) {
        ItemStack original = log.item();
        ItemComponent builder = new ItemComponent(original);

        var sellerProfile = plugin.core().profiles().get(log.sellerUuid());
        var skinData = sellerProfile != null ? sellerProfile.getSkinData() : null;

        if (template != null) {
            if (template.customModelData() != null && template.customModelData() > 0) {
                builder.customModelData(template.customModelData());
            }

            if (template.damage() != null && template.damage() > 0) {
                builder.damage(template.damage());
            }

            if (template.itemModel() != null && !template.itemModel().isEmpty()) {
                builder.itemModel(template.itemModel());
            }

            builder.glow(template.glow())
                    .flags(template.flags())
                    .hideTooltip(template.hideTooltip())
                    .tooltipStyle(template.tooltipStyle());

            if (template.headOwner() != null) builder.skullOwner(template.headOwner(), viewer, skinData);
            if (template.enchants() != null && !template.enchants().isEmpty()) builder.enchants(template.enchants());
        }

        String seller = plugin.core().profiles().getName(log.sellerUuid());
        String buyer = plugin.core().profiles().getName(log.buyerUuid());
        String price = plugin.core().economy().formats().format(log.price(), log.currencyId());
        String amountStr = String.valueOf(original.getAmount());
        String idStr = String.valueOf(log.id());
        String dateStr = TimeUtil.formatDate(plugin, log.soldAt());
        String timeStr = Instant.ofEpochMilli(log.soldAt()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        String itemFilterKey = plugin.filters().getCategory(original);
        String itemFilterDisplayName = plugin.filters().getDisplayName(itemFilterKey);

        List<Component> finalLore = new ArrayList<>();
        boolean skip = plugin.config().skipEmptyLines();
        boolean pendingEmpty = false;

        if (template != null && template.rawLore() != null) {
            boolean isShulker = original.getType().name().endsWith("SHULKER_BOX");
            ItemMeta sourceMeta = original.getItemMeta();

            for (String line : template.rawLore()) {
                if (line.contains("%lore%")) {
                    if (sourceMeta != null && sourceMeta.hasLore()) {
                        List<Component> sourceLore = Objects.requireNonNull(sourceMeta.lore());
                        if (!sourceLore.isEmpty()) {
                            if (pendingEmpty && !finalLore.isEmpty()) finalLore.add(Component.empty());
                            finalLore.addAll(sourceLore);
                            pendingEmpty = false;
                        }
                    }
                    continue;
                }

                Component comp = null;
                if (line.startsWith("preview:")) {
                    if (isShulker) comp = processHistoryLine(line.substring(8).trim(), seller, buyer, price, idStr, amountStr, dateStr, timeStr, itemFilterDisplayName, globalPh, viewer);
                } else {
                    comp = processHistoryLine(line, seller, buyer, price, idStr, amountStr, dateStr, timeStr, itemFilterDisplayName, globalPh, viewer);
                }

                if (comp != null) {
                    if (skip && isPlainEmpty(comp)) {
                        pendingEmpty = true;
                    } else {
                        if (pendingEmpty && !finalLore.isEmpty()) finalLore.add(Component.empty());
                        finalLore.add(comp);
                        pendingEmpty = false;
                    }
                }
            }
        }

        builder.lore(finalLore);

        ItemMeta meta = original.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            if (template != null && template.rawName() != null) {
                builder.name(processHistoryLine(template.rawName(), seller, buyer, price, idStr, amountStr, dateStr, timeStr, itemFilterDisplayName, globalPh, viewer));
            }
        } else {
            builder.name(meta.displayName());
        }

        return builder.build();
    }

    private static boolean isPlainEmpty(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).isEmpty();
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