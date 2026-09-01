package com.ftxeven.airauctions.core.message;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReferenceExpander {

    private static final Pattern TAG = Pattern.compile("<(?:ref|r):([a-zA-Z0-9_-]+)>");

    public static final ReferenceExpander EMPTY = new ReferenceExpander(Map.of());

    private final Map<String, String> flattened;

    private ReferenceExpander(Map<String, String> flattened) {
        this.flattened = flattened;
    }

    public static ReferenceExpander resolve(Map<String, String> raw, BiConsumer<String, String> onWarning) {
        if (raw.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> flattened = new LinkedHashMap<>();
        for (String key : raw.keySet()) {
            resolveOne(key, raw, flattened, new LinkedHashSet<>(), onWarning);
        }
        return new ReferenceExpander(Map.copyOf(flattened));
    }

    public String expand(String text) {
        if (flattened.isEmpty() || text.indexOf('<') < 0) {
            return text;
        }
        Matcher matcher = TAG.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int last = 0;
        do {
            result.append(text, last, matcher.start());
            result.append(flattened.getOrDefault(matcher.group(1), matcher.group()));
            last = matcher.end();
        } while (matcher.find());
        return result.append(text, last, text.length()).toString();
    }

    private static String resolveOne(String key, Map<String, String> raw, Map<String, String> flattened, Set<String> stack, BiConsumer<String, String> onWarning) {
        String cached = flattened.get(key);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(key)) {
            onWarning.accept(key, "reference cycle detected involving '" + key + "', stopping expansion there");
            return raw.getOrDefault(key, "");
        }

        String value = raw.get(key);
        Matcher matcher = TAG.matcher(value);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(value, last, matcher.start());
            String refKey = matcher.group(1);
            if (raw.containsKey(refKey)) {
                out.append(resolveOne(refKey, raw, flattened, stack, onWarning));
            } else {
                onWarning.accept(key, "unknown reference '<ref:" + refKey + ">' used in reference '" + key + "'");
                out.append(matcher.group());
            }
            last = matcher.end();
        }
        out.append(value, last, value.length());

        String resolved = out.toString();
        stack.remove(key);
        flattened.put(key, resolved);
        return resolved;
    }
}