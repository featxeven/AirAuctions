package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.core.gui.nav.GuiContext;
import com.ftxeven.airauctions.core.gui.nav.ScreenKey;
import com.ftxeven.airauctions.core.gui.nav.ScreenState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record OpenOptions(
        Function<String, String> flagResolver,
        Map<String, Object> attributes,
        Kind kind,
        List<String> ancestorChain
) {
    public static final OpenOptions DEFAULT = new OpenOptions(FlagGate.NO_FLAGS, Map.of(), Kind.ENTRY, List.of());

    public OpenOptions {
        attributes = Map.copyOf(attributes);
        ancestorChain = List.copyOf(ancestorChain);
    }

    public static OpenOptions silentReopen(Function<String, String> flagResolver) {
        return new OpenOptions(flagResolver, Map.of(), Kind.REOPEN, List.of());
    }

    // Plain entry point (like DEFAULT) that also seeds initial session attributes and, when the
    // GUI has context: overrides, an ancestor chain to resolve them against
    public static OpenOptions entry(Map<String, Object> attributes, List<String> ancestorChain) {
        return new OpenOptions(FlagGate.NO_FLAGS, attributes, Kind.ENTRY, ancestorChain);
    }

    public static OpenOptions forScreen(Function<String, String> flagResolver, ScreenKey screen, ScreenState state,
                                        @Nullable GuiContext navBack, List<String> ancestorChain, Map<String, Object> extra) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(GuiSession.ATTR_PAGE, state.page());
        attrs.put(GuiSession.ATTR_TOTAL_PAGES, state.totalPages());
        attrs.putAll(state.attributes());
        if (screen.target() != null) {
            attrs.put(GuiSession.ATTR_TARGET, screen.target());
        }
        if (navBack != null) {
            attrs.put(GuiSession.ATTR_NAV_BACK, navBack);
        }
        attrs.putAll(extra);
        return new OpenOptions(flagResolver, attrs, Kind.NAVIGATE, ancestorChain);
    }

    public static OpenOptions forScreen(Function<String, String> flagResolver, ScreenKey screen, ScreenState state,
                                        @Nullable GuiContext navBack, List<String> ancestorChain) {
        return forScreen(flagResolver, screen, state, navBack, ancestorChain, Map.of());
    }

    public enum Kind {
        ENTRY,
        NAVIGATE,
        REOPEN
    }
}