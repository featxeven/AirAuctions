package com.ftxeven.airauctions.core.command;

import com.ftxeven.airauctions.command.SubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CommandRegistry {

    private final List<SubCommand> subCommands = new ArrayList<>();

    public CommandRegistry register(SubCommand subCommand) {
        subCommands.add(subCommand);
        return this;
    }

    public Optional<SubCommand> match(String label) {
        for (SubCommand subCommand : subCommands) {
            if (subCommand.enabled() && (subCommand.name().equalsIgnoreCase(label) || subCommand.aliases().stream().anyMatch(label::equalsIgnoreCase))) {
                return Optional.of(subCommand);
            }
        }
        return Optional.empty();
    }

    public List<SubCommand> all() {
        return subCommands;
    }
}