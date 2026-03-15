package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.AirAuctions;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, Component> STATIC_CACHE = new ConcurrentHashMap<>();
    private static final Tag EMPTY_TAG = Tag.inserting(Component.empty());
    private static AirAuctions plugin;

    private MessageUtil() {}

    public static void init(AirAuctions airAuctions) {
        plugin = airAuctions;
        PlaceholderUtil.init(airAuctions);
        STATIC_CACHE.clear();
    }

    public static Component mini(Player player, String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) return Component.empty();

        if ((placeholders == null || placeholders.isEmpty()) && !raw.contains("<") && !raw.contains("%")) {
            return STATIC_CACHE.computeIfAbsent(raw, MM::deserialize);
        }

        String applied = PlaceholderUtil.apply(player, raw, placeholders);

        TitleState titleState = new TitleState();
        Component result = MM.deserialize(applied, new InlineTagResolver(player, placeholders, titleState));

        if (titleState.hasAny()) {
            TitleUtil.sendComponents(player, titleState.main, titleState.sub,
                    titleState.fadeIn, titleState.stay, titleState.fadeOut);
        }

        return result;
    }

    public static void send(Player player, Object messageObj, Map<String, String> placeholders) {
        if (player == null || messageObj == null) return;

        if (messageObj instanceof List<?> list) {
            for (Object line : list) {
                if (line instanceof String s) {
                    processSingleMessage(player, s, placeholders);
                }
            }
        } else if (messageObj instanceof String s) {
            processSingleMessage(player, s, placeholders);
        }
    }

    private static void processSingleMessage(Player player, String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty() || message.equals("\"\"")) return;

        boolean hasVisibleText = false;
        boolean hasTags = false;
        int depth = 0;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '<') {
                depth++;
                hasTags = true;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && c != ' ') {
                hasVisibleText = true;
                break;
            } else if (depth == 0) {
                hasVisibleText = true;
                break;
            }
        }

        if (!hasVisibleText && hasTags) {
            mini(player, message, placeholders);
            return;
        }

        Component component = mini(player, message, placeholders);
        plugin.scheduler().runEntityTask(player, () -> player.sendMessage(component));
    }

    private static class TitleState {
        Component main = Component.empty();
        Component sub = Component.empty();
        int fadeIn = 10, stay = 70, fadeOut = 20;
        boolean hasAny() { return main != Component.empty() || sub != Component.empty(); }
    }

    private record InlineTagResolver(Player player, Map<String, String> placeholders, TitleState titleState) implements TagResolver {

        @Override
        public boolean has(@NotNull String name) {
            return switch (name) {
                case "sound", "actionbar", "title", "subtitle", "bossbar" -> true;
                default -> false;
            };
        }

        @Override
        public Tag resolve(@NotNull String name, @NotNull ArgumentQueue args, @NotNull Context context) {
            switch (name) {
                case "sound" -> {
                    SoundUtil.play(player, args.popOr("!").value(),
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f,
                            args.hasNext() ? (float) args.pop().asDouble().orElse(1.0) : 1f);
                    return EMPTY_TAG;
                }
                case "actionbar" -> {
                    ActionbarUtil.send(plugin, player, args.popOr("!").value(), placeholders);
                    return EMPTY_TAG;
                }
                case "title" -> {
                    titleState.main = MM.deserialize(PlaceholderUtil.apply(player, args.popOr("!").value(), placeholders));
                    if (args.hasNext()) titleState.fadeIn = args.pop().asInt().orElse(10);
                    if (args.hasNext()) titleState.stay = args.pop().asInt().orElse(70);
                    if (args.hasNext()) titleState.fadeOut = args.pop().asInt().orElse(20);
                    return EMPTY_TAG;
                }
                case "subtitle" -> {
                    titleState.sub = MM.deserialize(PlaceholderUtil.apply(player, args.popOr("!").value(), placeholders));
                    return EMPTY_TAG;
                }
                case "bossbar" -> {
                    BossbarUtil.send(plugin, player, args.popOr("!").value(), placeholders,
                            args.hasNext() ? args.pop().asInt().orElse(100) : 100,
                            args.hasNext() ? BossBar.Color.valueOf(args.pop().value().toUpperCase()) : BossBar.Color.WHITE,
                            BossBar.Overlay.PROGRESS, 1.0f, false);
                    return EMPTY_TAG;
                }
            }
            return null;
        }
    }
}