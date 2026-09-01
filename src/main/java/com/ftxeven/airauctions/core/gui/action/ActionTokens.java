package com.ftxeven.airauctions.core.gui.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActionTokens {

    private ActionTokens() {
    }

    // key:value parsing

    public static Map<String, String> parse(String raw) {
        Map<String, String> args = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return args;
        }
        for (String token : split(raw.trim())) {
            int sep = token.indexOf(':');
            if (sep > 0) {
                args.put(token.substring(0, sep), unquote(token.substring(sep + 1)));
            }
        }
        return args;
    }

    // Tokenizing 'key:value key:value ...' strings

    public static List<String> split(String args) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int n = args.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(args.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            boolean sawColon = false;
            while (i < n) {
                char c = args.charAt(i);
                if (Character.isWhitespace(c)) {
                    break;
                }
                if (c == ':' && !sawColon) {
                    sawColon = true;
                    if (i + 1 < n && args.charAt(i + 1) == '\'') {
                        int close = closingQuoteIndex(args, i + 1);
                        i = close < 0 ? n : close + 1;
                        continue;
                    }
                }
                i++;
            }
            tokens.add(args.substring(start, i));
        }
        return tokens;
    }

    public static String unquote(String raw) {
        if (!raw.isEmpty() && raw.charAt(0) == '\'') {
            int close = closingQuoteIndex(raw, 0);
            if (close == raw.length() - 1) {
                return unescape(raw.substring(1, close));
            }
        }
        return raw;
    }

    // Shared quote scanning

    public static int closingQuoteIndex(String text, int openIndex) {
        int i = openIndex + 1;
        while (i < text.length()) {
            if (text.charAt(i) == '\'') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }

    public static String unescape(String text) {
        return text.replace("''", "'");
    }

    public static String escape(String text) {
        return text.replace("'", "''");
    }
}