package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.manager.main.AuctionHouseManager;
import com.ftxeven.airauctions.core.manager.main.AuctionSearchManager;
import com.ftxeven.airauctions.core.manager.player.ExpiredListingsManager;
import com.ftxeven.airauctions.core.model.AuctionListing;
import com.ftxeven.airauctions.util.MessageUtil;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import com.ftxeven.airauctions.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemAction {
    private final AirAuctions plugin;
    private static final Pattern DELAY_PATTERN = Pattern.compile("\\s*<delay:(\\d+)>\\s*$");
    private final Map<List<String>, List<ActionData>> cache = new ConcurrentHashMap<>();

    public ItemAction(AirAuctions plugin) { this.plugin = plugin; }

    public void executeAll(List<String> raw, Player p, Map<String, String> ph) {
        if (p == null || raw == null || raw.isEmpty()) return;
        List<ActionData> actions = cache.computeIfAbsent(raw, r -> r.stream().map(this::parseSingle).filter(Objects::nonNull).toList());

        for (ActionData ad : actions) {
            if (ad.type == Type.REQUIREMENT) {
                if (!checkRequirement(p, ad.payload, ph)) return;
                continue;
            }

            if (ad.delay <= 0) run(p, ad, ph);
            else plugin.scheduler().runDelayed(() -> run(p, ad, ph), ad.delay);
        }
    }

    private boolean checkRequirement(Player p, String expression, Map<String, String> ph) {
        String applied = PlaceholderUtil.apply(p, expression, ph);

        String[] parts = applied.split(" ");
        if (parts.length == 3) {
            try {
                double left = Double.parseDouble(parts[0]);
                String op = parts[1];
                double right = Double.parseDouble(parts[2]);

                return switch (op) {
                    case ">" -> left > right;
                    case "<" -> left < right;
                    case ">=" -> left >= right;
                    case "<=" -> left <= right;
                    case "==" -> left == right;
                    default -> true;
                };
            } catch (NumberFormatException ignored) {
                return parts[0].equalsIgnoreCase(parts[2]);
            }
        }

        return p.hasPermission(applied);
    }

    private ActionData parseSingle(String raw) {
        int delay = 0;
        String line = raw.trim();
        Matcher m = DELAY_PATTERN.matcher(line);
        if (m.find()) {
            delay = Integer.parseInt(m.group(1));
            line = line.substring(0, m.start()).trim();
        }

        String lower = line.toLowerCase();
        if (lower.startsWith("[player]")) return new ActionData(Type.PLAYER_CMD, line.substring(8).trim(), delay, 0, 0);
        if (lower.startsWith("[console]")) return new ActionData(Type.CONSOLE_CMD, line.substring(9).trim(), delay, 0, 0);
        if (lower.startsWith("[message]")) return new ActionData(Type.MESSAGE, line.substring(9).trim(), delay, 0, 0);
        if (lower.startsWith("[open]")) return new ActionData(Type.OPEN, line.substring(6).trim(), delay, 0, 0);
        if (lower.equals("[close]")) return new ActionData(Type.CLOSE, "", delay, 0, 0);
        if (lower.equals("[refresh]")) return new ActionData(Type.REFRESH, "", delay, 0, 0);
        if (lower.startsWith("[sound]")) {
            String[] pts = line.substring(7).trim().split("\\s+");
            return new ActionData(Type.SOUND, pts[0], delay, pF(pts, 1), pF(pts, 2));
        }
        if (lower.equals("[buy]")) return new ActionData(Type.BUY, "", delay, 0, 0);
        if (lower.equals("[preview]")) return new ActionData(Type.PREVIEW, "", delay, 0, 0);
        if (lower.equals("[cancel]")) return new ActionData(Type.CANCEL, "", delay, 0, 0);
        if (lower.equals("[collect]")) return new ActionData(Type.COLLECT, "", delay, 0, 0);
        if (lower.startsWith("[requirement]")) return new ActionData(Type.REQUIREMENT, line.substring(13).trim(), delay, 0, 0);
        return null;
    }

    private void run(Player p, ActionData ad, Map<String, String> ph) {
        String payload = (ad.type == Type.OPEN || ad.type == Type.CLOSE || ad.type == Type.REFRESH)
                ? ad.payload : PlaceholderUtil.apply(p, ad.payload, ph);
        switch (ad.type) {
            case PLAYER_CMD -> p.performCommand(payload);
            case CONSOLE_CMD -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload);
            case MESSAGE -> {
                var comp = MessageUtil.mini(p, payload, ph);
                if (comp != null) p.sendMessage(comp);
            }
            case OPEN -> handleOpen(p, payload, ph);
            case SOUND -> SoundUtil.play(p, ad.payload, ad.vol, ad.pitch);
            case CLOSE -> p.closeInventory();
            case REFRESH -> plugin.core().gui().refresh(p, p.getOpenInventory().getTopInventory(), ph);

            case BUY -> handleBuy(p, ph);
            case PREVIEW -> handlePreview(p, ph);
            case CANCEL -> handleCancel(p, ph);
            case COLLECT -> handleCollect(p, ph);
        }
    }

    private void handleOpen(Player p, String target, Map<String, String> ph) {
        String[] parts = target.split("\\s+");
        if (parts.length == 0) return;

        Map<String, String> newPh = new HashMap<>();
        if (ph.containsKey("target")) newPh.put("target", ph.get("target"));

        String guiId = null;
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].toLowerCase();
            String value = PlaceholderUtil.apply(p, kv[1], ph);

            if (key.equals("gui")) guiId = value;
            else newPh.put(key, value);
        }

        if (guiId != null) plugin.core().gui().open(guiId, p, newPh);
    }

    private void handleBuy(Player p, Map<String, String> ph) {
        int id = Integer.parseInt(ph.getOrDefault("listing-id", "-1"));
        var listing = plugin.core().auctions().getActiveListing(id);
        if (listing == null) return;

        var holder = p.getOpenInventory().getTopInventory().getHolder();

        if (holder instanceof AuctionHouseManager.Holder) {
            var am = plugin.core().gui().get("auction_house", AuctionHouseManager.class);
            if (am != null && !am.isInvalidPurchase(p, listing)) {
                processBuyLogic(p, listing, am.getDefinition(), ph, () -> am.processPurchase(p, listing));
            }
        } else if (holder instanceof AuctionSearchManager.SearchHolder) {
            var sm = plugin.core().gui().get("auction_search", AuctionSearchManager.class);
            if (sm != null && !sm.isInvalidPurchase(p, listing)) {
                processBuyLogic(p, listing, sm.getDefinition(), ph, () -> sm.processPurchase(p, listing));
            }
        }
    }

    private void processBuyLogic(Player p, AuctionListing listing, GuiDefinition def, Map<String, String> ph, Runnable directBuy) {
        boolean isShulker = listing.item().getType().name().endsWith("SHULKER_BOX");
        if (def.config().getBoolean("auction-item.apply-confirm", false)) {
            var shulkerMgr = plugin.core().gui().get("confirm_purchase_shulker", com.ftxeven.airauctions.core.manager.confirm.ConfirmPurchaseShulkerManager.class);

            boolean shulkerEnabled = true;
            if (isShulker && shulkerMgr != null) {
                var targetSec = shulkerMgr.getConfig().getConfigurationSection("target-listings");
                if (targetSec != null) shulkerEnabled = targetSec.getBoolean("enabled", true);
            }

            String guiKey = (isShulker && shulkerEnabled) ? "confirm_purchase_shulker" : "confirm_purchase";
            plugin.core().gui().open(guiKey, p, ph);
        } else {
            directBuy.run();
        }
    }

    private void handlePreview(Player p, Map<String, String> ph) {
        int id = Integer.parseInt(ph.getOrDefault("listing-id", "-1"));
        var listing = plugin.core().auctions().getActiveListing(id);
        ItemStack item;

        if (listing != null) {
            item = listing.item();
        } else {
            var history = plugin.database().history().getHistoryByIdSync(id);
            item = (history != null) ? history.item() : null;
        }

        if (item != null && item.getType().name().endsWith("SHULKER_BOX")) {
            plugin.core().gui().open("preview_shulker", p, ph);
        }
    }

    private void handleCancel(Player p, Map<String, String> ph) {
        int id = Integer.parseInt(ph.getOrDefault("listing-id", "-1"));
        var listing = plugin.core().auctions().getActiveListing(id);
        var pm = plugin.core().gui().get("player_listings", com.ftxeven.airauctions.core.manager.player.PlayerListingsManager.class);

        if (listing == null || pm == null) return;

        if (pm.getDefinition().config().getBoolean("cancel-collect", false) &&
                !plugin.config().dropItemsWhenFull() && p.getInventory().firstEmpty() == -1) {
            MessageUtil.send(p, plugin.lang().get("auctions.cancel.error.inventory-full"), Map.of());
            return;
        }

        if (pm.getDefinition().config().getBoolean("auction-item.apply-confirm", true)) {
            plugin.core().gui().open("cancel_listing", p, ph);
        } else {
            pm.processCancellation(p, listing);
        }
    }

    private void handleCollect(Player p, Map<String, String> ph) {
        int id = Integer.parseInt(ph.getOrDefault("listing-id", "-1"));
        var listing = plugin.core().auctions().getExpiredListing(id);
        var em = plugin.core().gui().get("player_expired", ExpiredListingsManager.class);

        if (listing == null || em == null) return;

        if (p.getInventory().firstEmpty() == -1 && !plugin.config().dropItemsWhenFull()) {
            MessageUtil.send(p, plugin.lang().get("auctions.cancel.error.inventory-full"), Map.of());
            return;
        }

        em.processCollection(p, listing);
    }

    private float pF(String[] pts, int i) { try { return pts.length > i ? Float.parseFloat(pts[i]) : 1f; } catch (Exception e) { return 1f; } }
    public record ActionData(Type type, String payload, int delay, float vol, float pitch) {}
    public enum Type {
        PLAYER_CMD, CONSOLE_CMD, MESSAGE, OPEN, SOUND, CLOSE, REFRESH,
        BUY, PREVIEW, CANCEL, COLLECT, REQUIREMENT
    }
}