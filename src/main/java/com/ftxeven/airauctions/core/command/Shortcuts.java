package com.ftxeven.airauctions.core.command;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Shortcuts {

    private Shortcuts() {
    }

    public static boolean isRoot(RootCommand main, String label) {
        return label.equalsIgnoreCase(main.name()) || main.aliases().stream().anyMatch(label::equalsIgnoreCase);
    }

    // empty if 'label' matches neither the root command nor any shortcut
    public static Optional<String[]> resolveArgs(RootCommand main, Map<String, Shortcut> shortcuts, String label, String[] typedArgs) {
        if (isRoot(main, label)) {
            return Optional.of(typedArgs);
        }
        for (Map.Entry<String, Shortcut> entry : shortcuts.entrySet()) {
            Shortcut shortcut = entry.getValue();
            boolean matches = entry.getKey().equalsIgnoreCase(label) || shortcut.aliases().stream().anyMatch(label::equalsIgnoreCase);
            if (matches) {
                return Optional.of(merge(virtualArgs(shortcut), typedArgs));
            }
        }
        return Optional.empty();
    }

    public static String[] virtualArgs(Shortcut shortcut) {
        String[] tokens = shortcut.runs().trim().split("\\s+");
        return tokens.length <= 1 ? new String[0] : Arrays.copyOfRange(tokens, 1, tokens.length);
    }

    public static String[] merge(String[] virtualArgs, String[] typedArgs) {
        if (virtualArgs.length == 0) {
            return typedArgs;
        }
        String[] merged = new String[virtualArgs.length + typedArgs.length];
        System.arraycopy(virtualArgs, 0, merged, 0, virtualArgs.length);
        System.arraycopy(typedArgs, 0, merged, virtualArgs.length, typedArgs.length);
        return merged;
    }

    public record Shortcut(String runs, List<String> aliases) {
        public Shortcut {
            aliases = List.copyOf(aliases);
        }
    }
}