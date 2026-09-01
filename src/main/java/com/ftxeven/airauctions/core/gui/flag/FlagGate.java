package com.ftxeven.airauctions.core.gui.flag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class FlagGate {

    public static final Function<String, String> NO_FLAGS = key -> "false";

    private record Flag(String key, boolean negated) {}
    private record ParsedLine(List<Flag> flags, String remainder) {}

    private final ConcurrentHashMap<String, ParsedLine> cache = new ConcurrentHashMap<>();

    public Optional<String> apply(String rawLine, Function<String, String> resolver) {
        if (rawLine.isEmpty() || (rawLine.charAt(0) != '=' && rawLine.charAt(0) != '!')) {
            return Optional.of(rawLine);
        }

        ParsedLine parsed = cache.computeIfAbsent(rawLine, this::parse);
        for (Flag flag : parsed.flags()) {
            boolean value = "true".equalsIgnoreCase(resolver.apply(flag.key()));
            if (value == flag.negated()) {
                return Optional.empty();
            }
        }
        return Optional.of(parsed.remainder());
    }

    public boolean anyApplicable(List<String> lines, Function<String, String> resolver) {
        for (String line : lines) {
            if (apply(line, resolver).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private ParsedLine parse(String raw) {
        List<Flag> flags = new ArrayList<>();
        int i = 0;
        while (true) {
            boolean negated = raw.startsWith("!=", i);
            int sigilLen = negated ? 2 : (raw.startsWith("=", i) ? 1 : 0);
            if (sigilLen == 0) {
                break;
            }
            int keyStart = i + sigilLen;
            int keyEnd = keyStart;
            while (keyEnd < raw.length() && raw.charAt(keyEnd) != ' ') {
                keyEnd++;
            }
            if (keyEnd == keyStart) {
                break;
            }
            flags.add(new Flag(raw.substring(keyStart, keyEnd), negated));
            i = keyEnd < raw.length() ? keyEnd + 1 : keyEnd;
        }
        return new ParsedLine(List.copyOf(flags), raw.substring(i));
    }
}