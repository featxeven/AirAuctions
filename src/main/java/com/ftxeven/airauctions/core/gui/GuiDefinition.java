package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.model.PlayerAuctionProfile;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public record GuiDefinition(String title, int rows, Map<String, GuiItem> items, ConfigurationSection config) {

    public static List<Integer> parseSlots(List<String> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isEmpty()) continue;
            int dash = s.indexOf('-');
            if (dash != -1) {
                try {
                    int start = Integer.parseInt(s.substring(0, dash).trim());
                    int end = Integer.parseInt(s.substring(dash + 1).trim());
                    for (int i = start; i <= end; i++) result.add(i);
                } catch (NumberFormatException ignored) {}
            } else {
                try { result.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    public record GuiItem(String key, List<Integer> slots, String materialStr, String rawName, List<String> rawLore,
                          boolean glow, String itemModel, List<String> actions, List<String> leftActions, List<String> rightActions,
                          List<String> shiftActions, List<String> shiftLeftActions, List<String> shiftRightActions,
                          int amount, Integer customModelData, Integer damage, Map<String, Integer> enchants,
                          ItemFlag[] flags, String headOwner, boolean hideTooltip, String tooltipStyle,
                          double cooldown, String cooldownMessage,
                          TreeMap<Integer, ItemPriority> priorities) {

        public static GuiItem fromSection(String key, ConfigurationSection sec) {
            List<Integer> slots = parseSlots(sec.getStringList("slots"));

            String matStr = sec.getString("material");
            String head = sec.getString("head-owner");

            if (matStr != null && matStr.startsWith("head-")) {
                head = matStr.substring(5);
            }

            Map<String, Integer> enchants = Collections.emptyMap();
            ConfigurationSection enchSec = sec.getConfigurationSection("enchants");
            if (enchSec != null) {
                enchants = new HashMap<>();
                for (String eK : enchSec.getKeys(false)) enchants.put(eK, enchSec.getInt(eK));
            }

            ItemFlag[] flags = sec.getStringList("item-flags").stream()
                    .map(f -> { try { return ItemFlag.valueOf(f.toUpperCase()); } catch (Exception e) { return null; } })
                    .filter(Objects::nonNull).toArray(ItemFlag[]::new);

            TreeMap<Integer, ItemPriority> priorities = new TreeMap<>();
            ConfigurationSection prioSec = sec.getConfigurationSection("priority");
            if (prioSec != null) {
                for (String pKey : prioSec.getKeys(false)) {
                    ConfigurationSection tier = prioSec.getConfigurationSection(pKey);
                    if (tier == null) continue;
                    try {
                        priorities.put(Integer.parseInt(pKey), ItemPriority.fromSection(tier));
                    } catch (NumberFormatException ignored) {}
                }
            }

            return new GuiItem(
                    key, slots, matStr,
                    sec.getString("display-name"), sec.getStringList("lore"),
                    sec.getBoolean("glow", false), sec.getString("item-model"),
                    sec.getStringList("actions"), sec.getStringList("left-actions"), sec.getStringList("right-actions"),
                    sec.getStringList("shift-actions"), sec.getStringList("shift-left-actions"), sec.getStringList("shift-right-actions"),
                    sec.getInt("amount", 1),
                    sec.get("custom-model-data") instanceof Integer i ? i : null,
                    sec.get("damage") instanceof Integer i ? i : null,
                    enchants, flags, head, sec.getBoolean("hide-tooltip", false), sec.getString("tooltip-style"),
                    sec.getDouble("cooldown", 0.0), sec.getString("cooldown-message"), priorities
            );
        }

        public List<String> getActionsForClick(ClickType click, Player viewer, Map<String, String> ph) {
            List<String> baseActions = switch (click) {
                case LEFT -> !leftActions.isEmpty() ? leftActions : actions;
                case RIGHT -> !rightActions.isEmpty() ? rightActions : actions;
                case SHIFT_LEFT -> !shiftLeftActions.isEmpty() ? shiftLeftActions : (!shiftActions.isEmpty() ? shiftActions : actions);
                case SHIFT_RIGHT -> !shiftRightActions.isEmpty() ? shiftRightActions : (!shiftActions.isEmpty() ? shiftActions : actions);
                default -> actions;
            };

            if (!priorities.isEmpty()) {
                for (ItemPriority p : priorities.values()) {
                    if (p.matches(viewer, ph)) {
                        if (p.actions() != null && !p.actions().isEmpty()) {
                            return p.actions();
                        }
                        return baseActions;
                    }
                }
            }

            return baseActions;
        }

        public ItemStack buildStack(Player viewer, Map<String, String> ph, AirAuctions plugin) {
            ItemPriority match = null;
            if (!priorities.isEmpty()) {
                for (ItemPriority p : priorities.values()) {
                    if (p.matches(viewer, ph)) {
                        match = p;
                        break;
                    }
                }
            }

            String activeMat = (match != null && match.material() != null) ? match.material() : this.materialStr;
            if (activeMat == null) return new ItemStack(Material.AIR);

            int activeAmount = (match != null && match.amount() != null) ? match.amount() : this.amount;
            String activeName = (match != null && match.displayName() != null) ? match.displayName() : this.rawName;
            List<String> activeLore = (match != null && match.lore() != null) ? match.lore() : this.rawLore;
            Integer activeData = (match != null && match.customModelData() != null) ? match.customModelData() : this.customModelData;
            String activeModel = (match != null && match.itemModel() != null) ? match.itemModel() : this.itemModel;
            String activeStyle = (match != null && match.tooltipStyle() != null) ? match.tooltipStyle() : this.tooltipStyle;
            boolean activeHide = (match != null && match.hideTooltip() != null) ? match.hideTooltip() : this.hideTooltip;
            boolean activeGlow = (match != null && match.glow() != null) ? match.glow() : this.glow;

            ItemComponent builder = new ItemComponent(activeMat, plugin).amount(activeAmount);

            if (activeName != null) {
                String appliedName = PlaceholderUtil.apply(viewer, activeName, ph);
                builder.name(MessageUtil.mini(viewer, "<!italic>" + appliedName, ph));
            }

            if (activeLore != null && !activeLore.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                boolean skip = plugin.config().skipEmptyLines();
                boolean pendingEmpty = false;
                var serializer = PlainTextComponentSerializer.plainText();

                for (String line : activeLore) {
                    String applied = PlaceholderUtil.apply(viewer, line, ph);
                    String[] splits = applied.split("\n");

                    for (String split : splits) {
                        Component comp = MessageUtil.mini(viewer, "<!italic>" + split, ph);
                        if (skip && serializer.serialize(comp).isEmpty()) {
                            pendingEmpty = true;
                        } else {
                            if (pendingEmpty && !lore.isEmpty()) lore.add(Component.empty());
                            lore.add(comp);
                            pendingEmpty = false;
                        }
                    }
                }
                builder.lore(lore);
            }

            builder.customModelData(activeData)
                    .damage(damage)
                    .enchants(enchants)
                    .glow(activeGlow)
                    .flags(flags)
                    .hideTooltip(activeHide)
                    .tooltipStyle(activeStyle)
                    .itemModel(activeModel);

            if (this.headOwner() != null) {
                String resolvedOwner = PlaceholderUtil.apply(viewer, this.headOwner(), ph);
                PlayerAuctionProfile.SkinData fetchedSkin = null;
                UUID ownerUuid = plugin.database().records().uuidFromName(resolvedOwner);
                if (ownerUuid != null) {
                    var profile = plugin.core().profiles().get(ownerUuid);
                    if (profile != null) fetchedSkin = profile.getSkinData();
                }
                builder.skullOwner(resolvedOwner, viewer, fetchedSkin);
            }

            return builder.build();
        }
    }
}