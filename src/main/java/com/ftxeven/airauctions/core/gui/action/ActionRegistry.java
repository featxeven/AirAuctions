package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.util.Placeholders;
import org.bukkit.Bukkit;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionRegistry {

    @FunctionalInterface
    public interface Handler {
        void execute(ActionContext context, String args);
    }

    private static final Pattern TIMED_PARAM = Pattern.compile("<[a-zA-Z]+:([^>]*)>");

    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private final Map<String, Handler> confirmGates = new ConcurrentHashMap<>();

    public ActionRegistry() {
        register("close", (ctx, args) -> ctx.viewer().closeInventory());

        register("refresh", (ctx, args) -> ctx.manager().refresh(ctx.viewer()));

        register("page", new PageAction());

        register("open", new OpenAction());

        register("message", (ctx, args) -> ctx.messenger().send(ctx.viewer(), List.of(args), ctx.placeholders()));

        register("broadcast", (ctx, args) -> ctx.messenger().broadcast(List.of(args), ctx.placeholders()));

        register("player", (ctx, args) ->
                Bukkit.dispatchCommand(ctx.viewer(), Placeholders.apply(ctx.viewer(), args, ctx.placeholders())));

        register("console", (ctx, args) ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Placeholders.apply(ctx.viewer(), args, ctx.placeholders())));

        register("sound", (ctx, args) -> {
            String tag = "<sound:" + args.trim().replaceAll("\\s+", ":") + ">";
            ctx.messenger().send(ctx.viewer(), List.of(tag), ctx.placeholders());
        });

        register("actionbar", (ctx, args) ->
                ctx.messenger().send(ctx.viewer(), List.of(simpleTag("actionbar", args)), ctx.placeholders()));

        register("subtitle", (ctx, args) -> // inherits timing from a preceding [title] on the same action list
                ctx.messenger().send(ctx.viewer(), List.of(simpleTag("subtitle", args)), ctx.placeholders()));

        register("title", (ctx, args) -> // [title] text  OR  [title] 'text' <fadeIn:#> <stay:#> <fadeOut:#>
                ctx.messenger().send(ctx.viewer(), List.of(timedTag("title", args, 3)), ctx.placeholders()));

        register("bossbar", (ctx, args) -> // [bossbar] text  OR  [bossbar] 'text' <duration:#> <color:TYPE> <overlay:TYPE> <progress:#> <countdown:BOOL>
                ctx.messenger().send(ctx.viewer(), List.of(timedTag("bossbar", args, 5)), ctx.placeholders()));
    }

    public ActionRegistry register(String key, Handler handler) {
        handlers.put(key, handler);
        return this;
    }

    public ActionRegistry registerConfirmable(String key, Handler handler, Handler confirmGate) {
        handlers.put(key, handler);
        confirmGates.put(key, confirmGate);
        return this;
    }

    public Handler get(String key) {
        return handlers.get(key);
    }

    public @Nullable Handler confirmGate(String key) {
        return confirmGates.get(key);
    }

    private static String simpleTag(String tagName, String message) {
        return "<" + tagName + ":'" + ActionTokens.escape(message) + "'>";
    }

    private static String timedTag(String tagName, String args, int expectedParams) {
        String trimmed = args.trim();
        String message = trimmed;
        String paramBlock = "";

        if (trimmed.startsWith("'")) {
            int closingQuote = ActionTokens.closingQuoteIndex(trimmed, 0);
            if (closingQuote >= 0) {
                message = ActionTokens.unescape(trimmed.substring(1, closingQuote));
                paramBlock = trimmed.substring(closingQuote + 1);
            }
        }

        List<String> params = new ArrayList<>();
        Matcher matcher = TIMED_PARAM.matcher(paramBlock);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        while (params.size() < expectedParams) {
            params.add("");
        }

        StringBuilder tag = new StringBuilder("<").append(tagName).append(":'").append(ActionTokens.escape(message)).append('\'');
        for (String param : params) {
            tag.append(':').append(param);
        }
        return tag.append('>').toString();
    }
}