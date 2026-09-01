package com.ftxeven.airauctions.core.gui.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AliasExpander {

    private static final Pattern ALIAS = Pattern.compile("\\$([a-zA-Z0-9_-]+)");

    private final Map<String, String> aliases;
    private final Logger logger;

    public AliasExpander(Map<String, String> aliases, Logger logger) {
        this.aliases = aliases;
        this.logger = logger;
    }

    public String expand(String text, String context) {
        if (text.indexOf('$') < 0) {
            return text;
        }
        Matcher matcher = ALIAS.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = aliases.get(name);
            if (value == null) {
                logger.warning("Unknown alias '$" + name + "' used in " + context + ", leaving as-is");
                value = matcher.group();
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public List<String> expandAll(List<String> lines, String context) {
        List<String> expanded = new ArrayList<>(lines.size());
        for (String line : lines) {
            expanded.add(expand(line, context));
        }
        return List.copyOf(expanded);
    }
}