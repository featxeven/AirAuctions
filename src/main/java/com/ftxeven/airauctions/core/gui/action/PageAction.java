package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;

// [page] previous|next|first|last|N

public final class PageAction implements ActionRegistry.Handler {

    @Override
    public void execute(ActionContext context, String step) {
        if (step == null || step.isBlank()) {
            context.logger().warning("[page] action requires a step (previous/next/first/last/N), got nothing");
            return;
        }

        GuiSession session = context.session();
        Integer next = Cycle.resolvePage(session.page(), session.totalPages(), step.trim(), context.logger(), "[page] action");
        if (next == null) {
            return;
        }

        session.page(next);
        context.manager().refresh(context.viewer());
    }
}