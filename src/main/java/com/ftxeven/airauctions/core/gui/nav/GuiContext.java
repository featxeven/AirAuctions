package com.ftxeven.airauctions.core.gui.nav;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GuiContext(ScreenKey screen, List<String> originChain, @Nullable GuiContext previous) {}