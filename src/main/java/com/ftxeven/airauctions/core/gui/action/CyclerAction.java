package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public abstract class CyclerAction implements ActionRegistry.Handler {

    private final String name;

    protected CyclerAction(String name) {
        this.name = name;
    }

    @Override
    public final void execute(ActionContext context, String args) {
        Map<String, String> parsed = ActionTokens.parse(args);
        String dimension = parsed.get("by");
        String to = parsed.get("to");
        if (dimension == null || to == null) {
            context.logger().warning("[" + name + "] action requires both 'by:' and 'to:' (got '" + args + "')");
            return;
        }

        String attribute = attributeFor(dimension);
        if (attribute == null) {
            context.logger().warning("[" + name + "] action has unknown dimension 'by:" + dimension + "'");
            return;
        }

        GuiSession session = context.session();
        List<String> keys = optionsFor(session, dimension);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        String current = session.attribute(attribute, String.class);
        String next = Cycle.resolve(keys, current, to, context.logger(), "[" + name + "] action (by:" + dimension + ")");
        if (next == null) {
            return;
        }

        session.attribute(attribute, next);
        context.manager().refresh(context.viewer());
    }

    protected abstract @Nullable String attributeFor(String dimension);

    protected abstract @Nullable List<String> optionsFor(GuiSession session, String dimension);
}