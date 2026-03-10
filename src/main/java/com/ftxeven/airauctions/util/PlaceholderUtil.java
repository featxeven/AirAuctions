package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.AirAuctions;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PlaceholderUtil {

    private static boolean HAS_PAPI;
    private static String prefix;

    private PlaceholderUtil() {}

    public static void init(AirAuctions plugin) {
        HAS_PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        prefix = plugin.lang().get("general.prefix", "");
    }

    public static String apply(Player player, String text, Map<String, String> context) {
        if (text == null || text.isEmpty() || text.indexOf('%') == -1) return text;

        String result = text;

        if (result.contains("%prefix%")) {
            result = result.replace("%prefix%", prefix);
        }

        if (player != null && result.contains("%player%")) {
            result = result.replace("%player%", player.getName());
        }

        if (context != null && !context.isEmpty()) {
            for (Map.Entry<String, String> entry : context.entrySet()) {
                String key = "%" + entry.getKey() + "%";
                if (result.contains(key)) {
                    String val = entry.getValue();
                    result = result.replace(key, val != null ? val : "");
                }
            }
        }

        return HAS_PAPI ? PlaceholderAPI.setPlaceholders(player, result) : result;
    }

    public static String apply(Player player, String text) {
        return apply(player, text, null);
    }
}