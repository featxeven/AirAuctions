package com.ftxeven.airauctions.core.command;

import com.ftxeven.airauctions.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUnits {

    public record DurationUnit(int ticks) {}

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)([a-zA-Z]+)");
    public static final long TICKS_PER_SECOND = 20;

    private final Supplier<Map<String, DurationUnit>> units;

    public DurationUnits(Supplier<Map<String, DurationUnit>> units) {
        this.units = units;
    }

    public List<String> keys(String filter) {
        Set<String> allowed = allowed(filter);
        List<String> keys = new ArrayList<>();
        for (String key : units.get().keySet()) {
            if (allowed == null || allowed.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    // accepts either a bare number (uses the first allowed unit, in declared order) or one
    // or more "<number><unit>" segments run together with no separator, e.g. "300", "5m", "2h45m"
    public OptionalLong parse(String raw, String filter) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        String compact = raw.trim().replaceAll("\\s+", "");

        Map<String, DurationUnit> all = units.get();
        Set<String> allowed = allowed(filter);

        if (isDigitsOnly(compact)) {
            String defaultKey = defaultKey(all, allowed);
            return defaultKey != null ? amount(compact, all.get(defaultKey)) : OptionalLong.empty();
        }

        return parseSegments(compact, all, allowed);
    }

    private OptionalLong parseSegments(String compact, Map<String, DurationUnit> all, Set<String> allowed) {
        Matcher matcher = SEGMENT.matcher(compact);
        Set<String> seenUnits = new HashSet<>();
        long totalTicks = 0;
        int consumed = 0;

        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return OptionalLong.empty(); // gap/garbage between segments (or before the first one)
            }

            String key = resolveKey(matcher.group(2), all, allowed);
            if (key == null || !seenUnits.add(key)) {
                return OptionalLong.empty(); // unknown/disallowed unit, or the same unit used twice
            }

            OptionalLong segmentTicks = amount(matcher.group(1), all.get(key));
            if (segmentTicks.isEmpty()) {
                return OptionalLong.empty();
            }

            totalTicks += segmentTicks.getAsLong();
            consumed = matcher.end();
        }

        return consumed == compact.length() && consumed > 0 ? OptionalLong.of(totalTicks) : OptionalLong.empty();
    }

    private OptionalLong amount(String numberPart, DurationUnit unit) {
        try {
            long value = Long.parseLong(numberPart);
            return value >= 0 ? OptionalLong.of(value * unit.ticks()) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private String resolveKey(String token, Map<String, DurationUnit> all, Set<String> allowed) {
        for (String key : all.keySet()) {
            if ((allowed == null || allowed.contains(key)) && key.equalsIgnoreCase(token)) {
                return key;
            }
        }
        return null;
    }

    private String defaultKey(Map<String, DurationUnit> all, Set<String> allowed) {
        for (String key : all.keySet()) {
            if (allowed == null || allowed.contains(key)) {
                return key;
            }
        }
        return null;
    }

    private static boolean isDigitsOnly(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // strips the wrapping "<...>" of the config-facing set notation (ex: "<s, m, h, d>")
    private Set<String> allowed(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        String trimmed = filter.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        Set<String> keys = new HashSet<>();
        for (String token : trimmed.split(",")) {
            String key = token.trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    // resolves the filter a command's own tab-complete config declared for DURATION_UNITS
    // at the given position
    public static String filterFor(Map<Integer, TabPosition> tabComplete, int position) {
        return TabCompleteEngine.sourceParam(tabComplete, position, "DURATION_UNITS").orElse("");
    }

    public static OptionalInt wholeSeconds(long ticks) {
        if (ticks % TICKS_PER_SECOND != 0) {
            return OptionalInt.empty();
        }
        long seconds = ticks / TICKS_PER_SECOND;
        return seconds <= Integer.MAX_VALUE ? OptionalInt.of((int) seconds) : OptionalInt.empty();
    }
}