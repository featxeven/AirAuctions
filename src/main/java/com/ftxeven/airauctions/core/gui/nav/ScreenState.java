package com.ftxeven.airauctions.core.gui.nav;

import com.ftxeven.airauctions.core.gui.GuiSession;

import java.util.Map;

public record ScreenState(int page, int totalPages, Map<String, String> attributes) {

    public static final ScreenState DEFAULT = new ScreenState(1, 1, Map.of());

    public ScreenState {
        page = Math.max(1, page);
        totalPages = Math.max(1, totalPages);
        attributes = Map.copyOf(attributes);
    }

    public static ScreenState liveStateOf(GuiSession session) {
        return new ScreenState(session.page(), session.totalPages(), session.stringAttributes());
    }
}