package com.ftxeven.airauctions.core.command;

import java.util.List;

public record RootCommand(String name, List<String> aliases, String usage) {
    public RootCommand {
        aliases = List.copyOf(aliases);
    }
}