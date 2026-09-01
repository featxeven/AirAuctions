package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.action.ActionRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * [amount]           - opens this GUI's input context to type a custom value.
 * [amount] +<number> - adds <number> to the current value.
 * [amount] -<number> - subtracts <number> from the current value.
 */
public final class AmountAction implements ActionRegistry.Handler {

    public static final String ATTR_EDITOR = "amount-editor";

    private final ConfigManager configs;

    public AmountAction(ConfigManager configs) {
        this.configs = configs;
    }

    @Override
    public void execute(ActionContext context, String args) {
        GuiSession session = context.session();
        AmountEditor editor = session.attribute(ATTR_EDITOR, AmountEditor.class);
        if (editor == null) {
            context.logger().warning("[amount] action used on item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' but that GUI has no amount editor installed");
            return;
        }
        if (!editor.valid()) {
            TargetedListingAction.notifyUnavailable(context, configs);
            return;
        }

        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty()) {
            context.manager().input().request(context, editor.inputContext(), raw -> {
                editor.applyTyped(context.viewer(), raw);
                context.manager().resume(context.viewer());
            });
            return;
        }

        Double step = parseStep(trimmed);
        if (step == null) {
            context.logger().warning("[amount] action has an invalid step '" + trimmed + "' on item '"
                    + context.itemKey() + "' in GUI '" + context.guiId() + "', expected '+<number>' or '-<number>'");
            return;
        }

        editor.set(Math.max(0, editor.current() + step));
        context.manager().refresh(context.viewer());
    }

    private static @Nullable Double parseStep(String raw) {
        if (raw.charAt(0) != '+' && raw.charAt(0) != '-') {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}