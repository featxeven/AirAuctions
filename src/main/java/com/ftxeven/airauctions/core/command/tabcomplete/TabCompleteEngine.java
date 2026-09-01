package com.ftxeven.airauctions.core.command.tabcomplete;

import com.ftxeven.airauctions.core.command.DynamicCommand;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition.CopyPosition;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition.EntriesPosition;
import com.ftxeven.airauctions.core.command.tabcomplete.TabPosition.TabEntry;
import com.ftxeven.airauctions.core.condition.ConditionEvaluator;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public final class TabCompleteEngine {

    private final TabSourceRegistry sources;
    private final ConditionEvaluator conditions;

    public TabCompleteEngine(TabSourceRegistry sources, ConditionEvaluator conditions) {
        this.sources = sources;
        this.conditions = conditions;
    }

    public List<String> complete(CommandSender sender, DynamicCommand command, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        List<TabEntry> entries = resolveEntries(args.length, command.tabComplete());
        if (entries.isEmpty()) {
            return List.of();
        }

        String partial = args[args.length - 1];
        TabSource.Context context = new TabSource.Context(sender, args, command.actions());

        Set<String> suggestions = new LinkedHashSet<>();
        for (TabEntry entry : entries) {
            if (!passes(sender, entry, args)) {
                continue;
            }
            for (String value : resolveSources(entry.sources(), context)) {
                suggestions.add(entry.suffixMode() ? partial + value : value);
            }
        }

        if (suggestions.isEmpty()) {
            return List.of();
        }

        String lowerPartial = partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>(suggestions.size());
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(lowerPartial)) {
                matches.add(suggestion);
            }
        }
        return matches;
    }

    // lets a command mirror one of its own tab-complete restrictions into real argument
    // parsing instead of the two having to be kept in sync by hand
    public static Optional<String> sourceParam(Map<Integer, TabPosition> tabComplete, int position, String token) {
        if (!(tabComplete.get(position) instanceof EntriesPosition entries)) {
            return Optional.empty();
        }
        for (TabEntry entry : entries.entries()) {
            for (String source : entry.sources()) {
                int colon = source.indexOf(':');
                String base = colon >= 0 ? source.substring(0, colon) : source;
                if (base.equalsIgnoreCase(token)) {
                    return Optional.of(colon >= 0 ? source.substring(colon + 1) : "");
                }
            }
        }
        return Optional.empty();
    }

    // Position resolution

    private List<TabEntry> resolveEntries(int position, Map<Integer, TabPosition> positions) {
        TabPosition tabPosition = positions.get(position);
        if (tabPosition instanceof EntriesPosition entries) {
            return entries.entries();
        }
        if (tabPosition instanceof CopyPosition copy) {
            return resolveCopy(copy, positions);
        }
        return List.of();
    }

    private List<TabEntry> resolveCopy(CopyPosition copy, Map<Integer, TabPosition> positions) {
        if (!(positions.get(copy.copyArg()) instanceof EntriesPosition source)) {
            return copy.appendSources();
        }

        List<TabEntry> merged = new ArrayList<>();
        for (TabEntry entry : source.entries()) {
            List<String> remaining = entry.sources().stream()
                    .filter(token -> !excluded(token, copy.excludeSources()))
                    .toList();
            if (!remaining.isEmpty()) {
                merged.add(new TabEntry(remaining, entry.requires(), entry.conditions(), entry.suffixMode()));
            }
        }
        merged.addAll(copy.appendSources());
        return merged;
    }

    private boolean excluded(String token, List<String> excludeSources) {
        String base = baseToken(token);
        for (String exclude : excludeSources) {
            if (base.equalsIgnoreCase(baseToken(exclude))) {
                return true;
            }
        }
        return false;
    }

    private static String baseToken(String token) {
        int colon = token.indexOf(':');
        return colon >= 0 ? token.substring(0, colon) : token;
    }

    // Entry gating

    private boolean passes(CommandSender sender, TabEntry entry, String[] args) {
        if (!entry.requires().isEmpty()) {
            String permission = CommandArgs.substitute(entry.requires(), args);
            if (!sender.hasPermission(permission)) {
                return false;
            }
        }
        return entry.conditions().isEmpty() || conditions.evaluate(entry.conditions(), CommandArgs.resolver(sender, args));
    }

    // Source resolution

    private List<String> resolveSources(List<String> tokens, TabSource.Context context) {
        List<String> resolved = new ArrayList<>();
        for (String token : tokens) {
            resolved.addAll(isComposite(token) ? resolveComposite(token, context) : resolveSingle(token, context));
        }
        return resolved;
    }

    private boolean isComposite(String token) {
        return token.replaceAll("\\{[^}]*}", "").replaceAll("<[^>]*>", "").indexOf(' ') >= 0;
    }

    private List<String> resolveSingle(String token, TabSource.Context context) {
        int colon = token.indexOf(':');
        String name = colon >= 0 ? token.substring(0, colon) : token;
        String param = colon >= 0 ? token.substring(colon + 1) : null;
        return sources.find(name).map(source -> source.resolve(context, param)).orElseGet(() -> List.of(token));
    }

    private List<String> resolveComposite(String token, TabSource.Context context) {
        List<List<String>> parts = Arrays.stream(token.split(" ")).map(p -> resolveSingle(p, context)).toList();
        int shortest = parts.stream().mapToInt(List::size).min().orElse(0);
        List<String> combined = new ArrayList<>(shortest);
        for (int i = 0; i < shortest; i++) {
            int index = i;
            combined.add(parts.stream().map(p -> p.get(index)).collect(Collectors.joining(" ")));
        }
        return combined;
    }
}