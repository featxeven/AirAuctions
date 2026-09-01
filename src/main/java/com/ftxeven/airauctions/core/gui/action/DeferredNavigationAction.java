package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.OpenOptions;
import com.ftxeven.airauctions.core.gui.nav.GuiContext;
import com.ftxeven.airauctions.core.gui.nav.ScreenKey;
import com.ftxeven.airauctions.core.gui.nav.ScreenState;
import com.ftxeven.airauctions.util.Placeholders;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared shape behind any action that prompts the player for a value via the configured input
 * type (chat/dialog/sign) and only navigates once they answer.
 */
public abstract class DeferredNavigationAction implements ActionRegistry.Handler {

    private final String inputKey;

    protected DeferredNavigationAction(String inputKey) {
        this.inputKey = inputKey;
    }

    protected abstract String defaultTarget();

    protected abstract Map<String, Object> attributesFor(String answer);

    protected @Nullable String directValueKey() {
        return null;
    }

    @Override
    public final void execute(ActionContext context, String args) {
        ForwardNavigation.Parsed parsed = ForwardNavigation.parse(args, context, directValueKey());

        GuiSession current = context.session();
        ScreenState currentLive = ScreenState.liveStateOf(current);
        ForwardNavigation.applyRestore(context, current, currentLive, parsed);

        ScreenKey currentScreen = current.screenKey();
        ScreenKey forwardScreen = ForwardNavigation.resolveForwardScreen(context, defaultTarget(), currentScreen, parsed);
        ScreenState forward = ForwardNavigation.resolveForwardState(context, forwardScreen, currentLive, parsed);

        // re-triggering from the destination itself
        boolean refining = parsed.guiId() == null && current.definition().id().equals(defaultTarget());
        String tail = parsed.guiId() != null ? parsed.guiId() : current.definition().id();
        List<String> ancestorChain = refining ? current.originChain() : current.forwardChain(tail);
        GuiContext backLink = refining ? current.navBack() : new GuiContext(currentScreen, current.originChain(), current.navBack());

        if (parsed.value() != null) {
            String answer = Placeholders.apply(context.viewer(), parsed.value(), context.placeholders());
            navigate(context, current, forwardScreen, forward, backLink, ancestorChain, answer);
            return;
        }

        context.manager().input().request(context, inputKey, answer ->
                navigate(context, current, forwardScreen, forward, backLink, ancestorChain, answer));
    }

    private void navigate(ActionContext context, GuiSession current, ScreenKey forwardScreen, ScreenState forward,
                          GuiContext backLink, List<String> ancestorChain, String answer) {
        context.manager().open(context.viewer(), forwardScreen.guiId(), new LinkedHashMap<>(current.placeholders()),
                OpenOptions.forScreen(context.flagResolver(), forwardScreen, forward, backLink, ancestorChain, attributesFor(answer)));
    }
}