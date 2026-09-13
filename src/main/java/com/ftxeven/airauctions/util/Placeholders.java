package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.core.message.ReferenceExpander;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("%([a-zA-Z0-9_]+)%");
    private static final Pattern HAS_PERMISSION_KEY = Pattern.compile("has_permission_(.+)", Pattern.CASE_INSENSITIVE);

    private static volatile ReferenceExpander references = ReferenceExpander.EMPTY;

    private Placeholders() {
    }

    public static void references(ReferenceExpander expander) {
        references = expander;
    }

    public static String apply(CommandSender viewer, String line, Map<String, String> placeholders) {
        String result = references.expand(line);
        if (result.indexOf('%') >= 0) {
            result = replaceTokens(result, viewer, placeholders);
        }
        if (papiEnabled()) {
            result = PlaceholderAPI.setPlaceholders(viewer instanceof OfflinePlayer offlinePlayer ? offlinePlayer : null, result);
        }
        return result;
    }

    public static Function<String, String> resolver(CommandSender viewer, Map<String, String> placeholders) {
        return key -> {
            Matcher permission = HAS_PERMISSION_KEY.matcher(key);
            if (permission.matches()) {
                return String.valueOf(viewer != null && viewer.hasPermission(permission.group(1)));
            }
            return apply(viewer, "%" + key + "%", placeholders);
        };
    }

    public static boolean papiEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private static String replaceTokens(String line, CommandSender viewer, Map<String, String> placeholders) {
        Matcher matcher = TOKEN.matcher(line);
        if (!matcher.find()) {
            return line;
        }

        StringBuilder result = new StringBuilder(line.length());
        int last = 0;
        do {
            String value = resolveToken(matcher.group(1), viewer, placeholders);
            if (value != null) {
                result.append(line, last, matcher.start()).append(value);
                last = matcher.end();
            }
        } while (matcher.find());
        return result.append(line, last, line.length()).toString();
    }

    private static @Nullable String resolveToken(String key, CommandSender viewer, Map<String, String> placeholders) {
        String value = lookup(placeholders, key);
        if (value != null) {
            return value;
        }
        return key.equalsIgnoreCase("player") && viewer instanceof Player player ? player.getName() : null;
    }

    private static @Nullable String lookup(Map<String, String> placeholders, String key) {
        String exact = placeholders.get(key);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}