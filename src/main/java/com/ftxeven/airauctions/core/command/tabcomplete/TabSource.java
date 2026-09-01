package com.ftxeven.airauctions.core.command.tabcomplete;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface TabSource {

    List<String> resolve(Context context, String param);

    record Context(CommandSender sender, String[] args, Map<String, String> actions) {}
}