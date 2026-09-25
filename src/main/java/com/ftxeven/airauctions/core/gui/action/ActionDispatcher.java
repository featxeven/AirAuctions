package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.condition.ConditionEvaluator;
import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.util.Placeholders;
import com.ftxeven.airauctions.util.Scheduler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

public final class ActionDispatcher {

    private static final String CONFIRM_PREFIX = "confirm:";

    private final ActionRegistry registry;
    private final ActionParser parser;
    private final ConditionEvaluator conditions;
    private final FlagGate flags;

    public ActionDispatcher(ActionRegistry registry, ActionParser parser, ConditionEvaluator conditions, FlagGate flags) {
        this.registry = registry;
        this.parser = parser;
        this.conditions = conditions;
        this.flags = flags;
    }

    public void run(List<String> lines, ActionContext context) {
        Function<String, String> snapshot = snapshot(context);
        List<Optional<String>> gates = new ArrayList<>(lines.size());
        for (String raw : lines) {
            gates.add(flags.apply(raw, snapshot));
        }
        for (Optional<String> gated : gates) {
            if (!runOne(gated, context)) {
                return;
            }
        }
    }

    private boolean runOne(Optional<String> gated, ActionContext context) {
        if (gated.isEmpty()) {
            return true;
        }

        ActionParser.ParsedAction parsed = parser.parse(gated.get());
        if (!parsed.isValid()) {
            return true;
        }

        if (parsed.key().equals("requirement")) {
            return conditions.evaluate(parsed.args(), context.placeholderResolver());
        }
        if (parsed.key().equals("delay")) {
            dispatchDelayed(parsed.args(), context);
            return true;
        }

        ActionRegistry.Handler handler = resolveHandler(parsed.key());
        if (handler == null) {
            context.logger().warning("Unknown action '[" + parsed.key() + "]' on item '" + context.itemKey()
                    + "' in GUI '" + context.guiId() + "'");
            return true;
        }

        try {
            handler.execute(context, parsed.args());
        } catch (Exception e) {
            context.logger().log(Level.WARNING, "Action '[" + parsed.key() + "]' failed on item '" + context.itemKey()
                    + "' in GUI '" + context.guiId() + "'", e);
        }
        return true;
    }

    private @Nullable ActionRegistry.Handler resolveHandler(String key) {
        return key.startsWith(CONFIRM_PREFIX) ? registry.confirmGate(key.substring(CONFIRM_PREFIX.length())) : registry.get(key);
    }

    private void dispatchDelayed(String args, ActionContext context) {
        int space = args.indexOf(' ');
        if (space < 0) {
            context.logger().warning("Malformed [delay] action on item '" + context.itemKey()
                    + "' in GUI '" + context.guiId() + "' - expected 'ticks [action]'");
            return;
        }

        long ticks;
        try {
            ticks = Long.parseLong(args.substring(0, space).trim());
        } catch (NumberFormatException e) {
            context.logger().warning("Invalid delay tick count '" + args.substring(0, space)
                    + "' on item '" + context.itemKey() + "' in GUI '" + context.guiId() + "'");
            return;
        }

        String wrapped = args.substring(space + 1).trim();

        Scheduler.runEntityLater(context.viewer(), () ->
                runOne(flags.apply(wrapped, snapshot(context)), context), Math.max(1, ticks));
    }

    private static Function<String, String> snapshot(ActionContext context) {
        Function<String, String> resolver = context.flagResolver();
        Map<String, String> cache = new HashMap<>();
        return key -> cache.computeIfAbsent(key,
                k -> resolver.apply(Placeholders.apply(context.viewer(), k, context.placeholders())));
    }
}