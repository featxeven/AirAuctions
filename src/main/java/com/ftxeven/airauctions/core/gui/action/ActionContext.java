package com.ftxeven.airauctions.core.gui.action;

import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

public record ActionContext(
        Player viewer,
        GuiSession session,
        GuiManager manager,
        Messenger messenger,
        Map<String, String> placeholders,
        Function<String, String> placeholderResolver,
        Function<String, String> flagResolver,
        String itemKey,
        Logger logger
) {
    public String guiId() {
        return session.definition().id();
    }
}