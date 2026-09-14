package com.ftxeven.airauctions.core.gui.nav;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public record GuiContext(ScreenKey screen, List<String> originChain, Function<String, String> flagResolver, @Nullable GuiContext previous) {}