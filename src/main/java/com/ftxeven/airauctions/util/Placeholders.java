package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.core.message.ReferenceExpander;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("%([a-zA-Z0-9_]+)%");
    private static final Pattern NESTED = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final Pattern HAS_PERMISSION_KEY = Pattern.compile("has_permission_(.+)", Pattern.CASE_INSENSITIVE);

    private static volatile ReferenceExpander references = ReferenceExpander.EMPTY;

    private Placeholders() {
    }

    public static void references(ReferenceExpander expander) {
        references = expander;
    }

    public static String expandReferences(String line) {
        return references.expand(line);
    }

    public static String apply(CommandSender viewer, String line, Map<String, String> placeholders) {
        return apply(viewer, line, placeholders, new HashSet<>());
    }

    private static String apply(CommandSender viewer, String line, Map<String, String> placeholders, Set<String> resolving) {
        String template = references.expand(line);
        template = expandNested(viewer, template, placeholders, resolving);
        if (template.indexOf('%') < 0) {
            return template;
        }

        boolean papi = papiEnabled();
        OfflinePlayer context = viewer instanceof OfflinePlayer offlinePlayer ? offlinePlayer : null;

        Matcher matcher = TOKEN.matcher(template);
        StringBuilder result = null;
        int last = 0;
        while (matcher.find()) {
            String value = resolveToken(matcher.group(1), viewer, placeholders);
            if (value == null) {
                continue;
            }
            if (result == null) {
                result = new StringBuilder(template.length() + 16);
            }
            result.append(expandPapi(papi, context, template.substring(last, matcher.start()))).append(value);
            last = matcher.end();
        }

        if (result == null) {
            return expandPapi(papi, context, template);
        }
        return result.append(expandPapi(papi, context, template.substring(last))).toString();
    }

    private static String expandNested(CommandSender viewer, String template, Map<String, String> placeholders, Set<String> resolving) {
        if (template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = NESTED.matcher(template);
        StringBuilder result = new StringBuilder(template.length());
        int last = 0;
        while (matcher.find()) {
            String key = matcher.group(1);
            String resolved;
            if (!resolving.add(key)) {
                resolved = "";
            } else {
                resolved = apply(viewer, "%" + key + "%", placeholders, resolving);
                resolving.remove(key);
            }
            result.append(template, last, matcher.start()).append(resolved);
            last = matcher.end();
        }
        return result.append(template.substring(last)).toString();
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

    private static String expandPapi(boolean enabled, @Nullable OfflinePlayer context, String text) {
        if (!enabled || text.indexOf('%') < 0) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(context, text);
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