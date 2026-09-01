package com.ftxeven.airauctions.core.gui.nav;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ScreenKey(String guiId, @Nullable UUID target) {}