package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.nav.LockedContext;
import com.ftxeven.airauctions.core.gui.nav.ScreenKey;
import com.ftxeven.airauctions.core.gui.nav.ScreenState;
import com.ftxeven.airauctions.util.Placeholders;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and resolves the 'gui:/page:/target:/<kind>:<dimension>:<value>' + 'restore:<...>'
 * syntax shared by every action that navigates forward to another screen
 */
public final class ForwardNavigation {

    private static final Pattern RESTORE_BLOCK = Pattern.compile("restore:<([^>]*)>", Pattern.CASE_INSENSITIVE);

    private ForwardNavigation() {
    }

    // Parsing

    public static Parsed parse(String raw, ActionContext context) {
        return parse(raw, context, null);
    }

    public static Parsed parse(String raw, ActionContext context, @Nullable String valueKey) {
        String args = raw == null ? "" : raw;
        Parsed.Builder builder = new Parsed.Builder();

        Matcher restoreMatcher = RESTORE_BLOCK.matcher(args);
        if (restoreMatcher.find()) {
            builder.restoreSpecified = true;
            for (String entry : restoreMatcher.group(1).split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    applyRestoreToken(builder, trimmed, context);
                }
            }
            args = args.substring(0, restoreMatcher.start()) + args.substring(restoreMatcher.end());
        }

        String trimmed = args.trim();
        if (!trimmed.isEmpty()) {
            for (String token : ActionTokens.split(trimmed)) {
                applyToken(builder, token, valueKey, context);
            }
        }

        return builder.build();
    }

    private static void applyToken(Parsed.Builder builder, String token, @Nullable String valueKey, ActionContext context) {
        int sep = token.indexOf(':');
        if (sep <= 0) {
            context.logger().warning("Malformed navigation param '" + token + "'");
            return;
        }
        String key = token.substring(0, sep).toLowerCase(Locale.ROOT);
        String rest = token.substring(sep + 1);

        if (valueKey != null && key.equals(valueKey)) {
            builder.value = ActionTokens.unquote(rest);
            return;
        }

        switch (key) {
            case "gui" -> builder.guiId = rest;
            case "page" -> builder.page = rest;
            case "target" -> builder.target = rest;
            case "context" -> builder.context = Boolean.parseBoolean(rest);
            default -> putDimensioned(builder.dimensions, key, rest, context);
        }
    }

    private static void applyRestoreToken(Parsed.Builder builder, String entry, ActionContext context) {
        int sep = entry.indexOf(':');
        if (sep <= 0) {
            context.logger().warning("Malformed restore entry '" + entry + "'");
            return;
        }
        String key = entry.substring(0, sep).toLowerCase(Locale.ROOT);
        String rest = entry.substring(sep + 1);

        if (key.equals("page")) {
            builder.restorePage = rest;
        } else {
            putDimensioned(builder.restoreDimensions, key, rest, context);
        }
    }

    private static void putDimensioned(Map<String, String> target, String kind, String rest, ActionContext context) {
        int sep = rest.indexOf(':');
        if (sep <= 0) {
            context.logger().warning("Navigation param is missing ':<value>' in '" + kind + ":" + rest + "'");
            return;
        }
        String dimension = rest.substring(0, sep).toLowerCase(Locale.ROOT);
        String value = rest.substring(sep + 1);
        target.put(kind + "-" + dimension, value);
    }

    // Resolution

    // expands placeholders in the destination gui id and resolves its target
    public static ScreenKey resolveForwardScreen(ActionContext context, String guiId, ScreenKey currentScreen, Parsed parsed) {
        String resolvedGuiId = Placeholders.apply(context.viewer(), guiId, context.placeholders());
        UUID target = parsed.target() != null
                ? resolveTarget(parsed.target(), currentScreen.target(), context)
                : null;
        return new ScreenKey(resolvedGuiId, target);
    }

    // resolves the destination's context field-by-field
    public static ScreenState resolveForwardState(ActionContext context, ScreenKey destination, ScreenState sourceLive, Parsed parsed) {
        LockedContext locked = context.manager().lockedContext(context.viewer().getUniqueId(), destination);

        int page = resolvePage(parsed.page(), locked.page(), sourceLive.page(), context);

        Map<String, String> attributes = new LinkedHashMap<>(sourceLive.attributes());
        parsed.dimensions().forEach((key, value) -> {
            if (!value.equalsIgnoreCase("current")) {
                attributes.put(key, Placeholders.apply(context.viewer(), value, context.placeholders()));
            }
        });
        attributes.putAll(locked.attributes());

        return new ScreenState(page, sourceLive.totalPages(), attributes);
    }

    // 'restore:<...>' pins ONLY the named fields into 'current's own locked context
    public static void applyRestore(ActionContext context, GuiSession current, ScreenState live, Parsed parsed) {
        if (!parsed.restoreSpecified()) {
            return;
        }

        Integer page = null;
        if (parsed.restorePage() != null) {
            page = parsed.restorePage().equalsIgnoreCase("current")
                    ? live.page()
                    : Cycle.resolvePage(live.page(), current.totalPages(), parsed.restorePage().trim(), context.logger(), "restore:<page:> block");
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        parsed.restoreDimensions().forEach((key, value) -> {
            String resolved = value.equalsIgnoreCase("current")
                    ? live.attributes().get(key)
                    : Placeholders.apply(context.viewer(), value, context.placeholders());
            if (resolved != null) {
                attributes.put(key, resolved);
            }
        });

        context.manager().lockContextFields(context.viewer().getUniqueId(), current.screenKey(), page, attributes);
    }

    private static int resolvePage(@Nullable String override, @Nullable Integer lockedPage, int sourceLivePage, ActionContext context) {
        if (override != null && !override.equalsIgnoreCase("current")) {
            Integer resolved = resolveForwardPageStep(sourceLivePage, override.trim(), context);
            if (resolved != null) {
                return resolved;
            }
        }
        if (lockedPage != null) {
            return lockedPage;
        }
        return sourceLivePage;
    }

    private static @Nullable Integer resolveForwardPageStep(int basePage, String step, ActionContext context) {
        return switch (step.toLowerCase(Locale.ROOT)) {
            case "first" -> 1;
            case "next" -> basePage + 1;
            case "previous" -> Math.max(1, basePage - 1);
            case "last" -> {
                context.logger().warning("'page:last' isn't supported for a forward gui: target - "
                        + "the destination's page count isn't known until it's open");
                yield null;
            }
            default -> parseAbsolutePage(step, context);
        };
    }

    private static @Nullable Integer parseAbsolutePage(String step, ActionContext context) {
        try {
            return Math.max(1, Integer.parseInt(step.trim()));
        } catch (NumberFormatException e) {
            context.logger().warning("Invalid 'page:" + step + "', expected first/next/previous or a number");
            return null;
        }
    }

    private static @Nullable UUID resolveTarget(String override, @Nullable UUID fallback, ActionContext context) {
        if (override.equalsIgnoreCase("current")) {
            return fallback;
        }
        String resolved = Placeholders.apply(context.viewer(), override, context.placeholders()).trim();
        if (resolved.equalsIgnoreCase("self") || resolved.equalsIgnoreCase("viewer")) {
            return context.viewer().getUniqueId();
        }
        try {
            return UUID.fromString(resolved);
        } catch (IllegalArgumentException e) {
            UUID byName = context.manager().resolvePlayerName(resolved);
            if (byName != null) {
                return byName;
            }
            context.logger().warning("Could not resolve target '" + override + "' to a uuid");
            return fallback;
        }
    }

    // Types

    public record Parsed(
            @Nullable String guiId,
            @Nullable String page,
            @Nullable String target,
            boolean context,
            @Nullable String value,
            Map<String, String> dimensions,
            boolean restoreSpecified,
            @Nullable String restorePage,
            Map<String, String> restoreDimensions
    ) {
        public boolean hasForwardParams() {
            return guiId != null || page != null || target != null || !dimensions.isEmpty();
        }

        private static final class Builder {
            String guiId;
            String page;
            String target;
            boolean context;
            String value;
            final Map<String, String> dimensions = new LinkedHashMap<>();
            boolean restoreSpecified;
            String restorePage;
            final Map<String, String> restoreDimensions = new LinkedHashMap<>();

            Parsed build() {
                return new Parsed(guiId, page, target, context, value, dimensions, restoreSpecified, restorePage, restoreDimensions);
            }
        }
    }
}