package com.ftxeven.airauctions.core.gui.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class SlotParser {

    private SlotParser() {
    }

    public static Set<Integer> parse(Object raw, String context, Logger logger) {
        Set<Integer> slots = new LinkedHashSet<>();
        if (raw instanceof Number number) {
            slots.add(number.intValue());
        } else if (raw instanceof String text) {
            parseInto(slots, text, context, logger);
        } else if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Number number) {
                    slots.add(number.intValue());
                } else if (entry instanceof String text) {
                    parseInto(slots, text, context, logger);
                } else {
                    logger.warning("Invalid slot entry '" + entry + "' in " + context + ", skipping");
                }
            }
        } else if (raw != null) {
            logger.warning("Invalid slots value in " + context + ", expected a number, range string, or list");
        }
        return slots;
    }

    private static void parseInto(Set<Integer> target, String text, String context, Logger logger) {
        for (String part : text.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-', 1);
            if (dash > 0) {
                parseRange(target, part, dash, context, logger);
            } else {
                parseSingle(target, part, context, logger);
            }
        }
    }

    private static void parseRange(Set<Integer> target, String part, int dash, String context, Logger logger) {
        try {
            int from = Integer.parseInt(part.substring(0, dash).trim());
            int to = Integer.parseInt(part.substring(dash + 1).trim());
            for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                target.add(slot);
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid slot range '" + part + "' in " + context + ", skipping");
        }
    }

    private static void parseSingle(Set<Integer> target, String part, String context, Logger logger) {
        try {
            target.add(Integer.parseInt(part));
        } catch (NumberFormatException e) {
            logger.warning("Invalid slot '" + part + "' in " + context + ", skipping");
        }
    }
}