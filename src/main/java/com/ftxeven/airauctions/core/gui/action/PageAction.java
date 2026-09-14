package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;

import java.util.Map;

// [page] to:previous|next|first|last|N

public final class PageAction implements ActionRegistry.Handler {

    @Override
    public void execute(ActionContext context, String args) {
        Map<String, String> parsed = ActionTokens.parse(args);
        String to = parsed.get("to");
        if (to == null) {
            context.logger().warning("[page] action requires 'to:' (previous/next/first/last/N), got '" + args + "'");
            return;
        }

        GuiSession session = context.session();
        Integer next = Cycle.resolvePage(session.page(), session.totalPages(), to.trim(), context.logger(), "[page] action");
        if (next == null) {
            return;
        }

        session.page(next);
        context.manager().refresh(context.viewer());
    }
}