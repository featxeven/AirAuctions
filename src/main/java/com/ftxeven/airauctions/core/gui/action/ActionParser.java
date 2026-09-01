package com.ftxeven.airauctions.core.gui.action;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ActionParser {

    private final Map<String, ParsedAction> cache = new ConcurrentHashMap<>();
    private final Logger logger;

    public ActionParser(Logger logger) {
        this.logger = logger;
    }

    public ParsedAction parse(String raw) {
        return cache.computeIfAbsent(raw, this::doParse);
    }

    private ParsedAction doParse(String raw) {
        String trimmed = raw.trim();

        if (!trimmed.startsWith("[")) {
            logger.warning("Malformed action '" + raw + "' - expected it to start with '[key]'");
            return ParsedAction.INVALID;
        }
        int close = trimmed.indexOf(']');
        if (close < 0) {
            logger.warning("Malformed action '" + raw + "' - unterminated '['");
            return ParsedAction.INVALID;
        }
        String key = trimmed.substring(1, close).trim().toLowerCase(Locale.ROOT);
        String args = trimmed.substring(close + 1).trim();
        return key.isEmpty() ? ParsedAction.INVALID : new ParsedAction(key, args);
    }

    public record ParsedAction(String key, String args) {
        static final ParsedAction INVALID = new ParsedAction("", "");

        public boolean isValid() {
            return !key.isEmpty();
        }
    }
}