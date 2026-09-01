package com.ftxeven.airauctions.core.command.tabcomplete;

import com.ftxeven.airauctions.util.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandArgs {

    private static final Pattern ARG_TAG = Pattern.compile("%arg_(\\d+)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARG_KEY = Pattern.compile("arg_(\\d+)", Pattern.CASE_INSENSITIVE);

    private CommandArgs() {
    }

    // for "requires" - a plain permission string
    public static String substitute(String text, String[] args) {
        Matcher matcher = ARG_TAG.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(arg(args, matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // for "conditions" - resolved lazily per key so ConditionEvaluator's cache stays
    // keyed on the raw condition string
    public static Function<String, String> resolver(CommandSender sender, String[] args) {
        Function<String, String> fallback = Placeholders.resolver(sender, Map.of());
        return key -> {
            Matcher argMatcher = ARG_KEY.matcher(key);
            return argMatcher.matches() ? arg(args, argMatcher.group(1)) : fallback.apply(key);
        };
    }

    private static String arg(String[] args, String indexGroup) {
        int index = Integer.parseInt(indexGroup) - 1;
        return index >= 0 && index < args.length ? args[index] : "";
    }
}