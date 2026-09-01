package com.ftxeven.airauctions.core.gui.action;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Resolves a 'to:' step against an ordered list of keys - shared by every action that cycles
 * through a fixed set of options (next/previous/first/last/N convention).
 */
public final class Cycle {

    private Cycle() {
    }

    public static @Nullable String resolve(List<String> keys, @Nullable String current, String to, Logger logger, String context) {
        if (keys.isEmpty()) {
            return null;
        }

        int currentIndex = current != null ? keys.indexOf(current) : -1;
        if (currentIndex < 0) {
            currentIndex = 0;
        }

        return switch (to.toLowerCase(Locale.ROOT)) {
            case "next" -> keys.get((currentIndex + 1) % keys.size());
            case "previous" -> keys.get(Math.floorMod(currentIndex - 1, keys.size()));
            case "first" -> keys.getFirst();
            case "last" -> keys.getLast();
            default -> resolveIndex(keys, to, logger, context);
        };
    }

    public static @Nullable Integer resolvePage(int currentPage, int totalPages, String to, Logger logger, String context) {
        List<String> pages = IntStream.rangeClosed(1, totalPages).mapToObj(String::valueOf).toList();
        String next = resolve(pages, String.valueOf(currentPage), to, logger, context);
        return next != null ? Integer.parseInt(next) : null;
    }

    private static @Nullable String resolveIndex(List<String> keys, String raw, Logger logger, String context) {
        try {
            int index = Math.clamp(Integer.parseInt(raw.trim()) - 1, 0, keys.size() - 1);
            return keys.get(index);
        } catch (NumberFormatException e) {
            logger.warning("Invalid 'to:" + raw + "' in " + context + ", expected next/previous/first/last or a number");
            return null;
        }
    }
}