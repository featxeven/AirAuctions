package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.OpenOptions;
import com.ftxeven.airauctions.core.gui.nav.GuiContext;
import com.ftxeven.airauctions.core.gui.nav.ScreenKey;
import com.ftxeven.airauctions.core.gui.nav.ScreenState;

import java.util.LinkedHashMap;


 // [open] gui:<id> <optional params>  -  forward navigation
 // [open] context:true                -  back navigation

public final class OpenAction implements ActionRegistry.Handler {

    @Override
    public void execute(ActionContext context, String args) {
        ForwardNavigation.Parsed parsed = ForwardNavigation.parse(args, context);

        GuiSession current = context.session();
        ScreenState currentLive = ScreenState.liveStateOf(current);
        ForwardNavigation.applyRestore(context, current, currentLive, parsed);

        if (parsed.context()) {
            if (parsed.hasForwardParams()) {
                context.logger().warning("[open] action mixes 'context:true' with forward-navigation params ('" + args + "'), the params are ignored");
            }
            context.manager().openBack(context.viewer());
            return;
        }

        if (parsed.guiId() == null) {
            context.logger().warning("[open] action requires 'gui:<id>' (or 'context:true'), got '" + args + "'");
            return;
        }

        ScreenKey currentScreen = current.screenKey();
        ScreenKey forwardScreen = ForwardNavigation.resolveForwardScreen(context, parsed.guiId(), currentScreen, parsed);
        ScreenState forward = ForwardNavigation.resolveForwardState(context, forwardScreen, currentLive, parsed);

        GuiContext backLink = new GuiContext(currentScreen, current.originChain(), current.navBack());
        OpenOptions options = OpenOptions.forScreen(context.flagResolver(), forwardScreen, forward, backLink, current.forwardChain());
        context.manager().open(context.viewer(), forwardScreen.guiId(), new LinkedHashMap<>(current.placeholders()), options);
    }
}