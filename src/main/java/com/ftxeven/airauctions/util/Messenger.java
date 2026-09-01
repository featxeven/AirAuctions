package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.core.animation.AnimationManager;
import com.ftxeven.airauctions.core.message.MessageTagParser;
import com.ftxeven.airauctions.core.message.MessageTagRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class Messenger {

    private final AnimationManager animations;
    private final MessageTagParser tagParser;
    private final MessageTagRenderer tagRenderer;
    private final Logger logger;

    public Messenger(Logger logger, AnimationManager animations) {
        this.animations = animations;
        this.logger = logger;
        this.tagParser = new MessageTagParser(logger);
        this.tagRenderer = new MessageTagRenderer(animations, logger);
    }

    // Sending

    public void send(CommandSender target, List<String> lines, Map<String, String> placeholders) {
        MessageTagRenderer.TitleBuffer titleBuffer = new MessageTagRenderer.TitleBuffer();
        for (String rawLine : lines) {
            String substituted = Placeholders.apply(target, rawLine, placeholders);
            String leftover = tagParser.scan(substituted, tag -> tagRenderer.render(target, tag, titleBuffer));
            sendChatLine(target, leftover);
        }
        tagRenderer.flushTitle(target, titleBuffer);
    }

    public void send(CommandSender target, List<String> lines) {
        send(target, lines, Map.of());
    }

    // same as send(), but silently does nothing if the target isn't online
    public void send(UUID uuid, List<String> lines, Map<String, String> placeholders) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            Scheduler.runEntity(player, () -> send(player, lines, placeholders));
        }
    }

    // Broadcasting

    public void broadcast(List<String> lines, Map<String, String> placeholders) {
        broadcast(lines, placeholders, null);
    }

    public void broadcast(List<String> lines, Map<String, String> placeholders, UUID excluded) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (excluded == null || !player.getUniqueId().equals(excluded)) {
                send(player, lines, placeholders);
            }
        }
    }

    // GUI rendering (item display names, lore, gui titles)

    public Component renderLine(CommandSender viewer, String line, Map<String, String> placeholders, long startTick) {
        String substituted = Placeholders.apply(viewer, line, placeholders);
        Component rendered = deserialize(animations.resolve(substituted, startTick));
        return rendered.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component renderLine(CommandSender viewer, String line, Map<String, String> placeholders) {
        return renderLine(viewer, line, placeholders, animations.currentTick());
    }

    public List<Component> renderLines(CommandSender viewer, List<String> lines, Map<String, String> placeholders, long startTick) {
        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(renderLine(viewer, line, placeholders, startTick));
        }
        return rendered;
    }

    // Plain text

    public String plain(String line, Map<String, String> placeholders) {
        String substituted = Placeholders.apply(null, line, placeholders);
        String stripped = tagParser.strip(substituted);
        String resolved = animations.resolve(stripped);
        return MiniText.plain(deserialize(resolved));
    }

    // Chat lines

    private void sendChatLine(CommandSender target, String rawText) {
        String text = animations.ensureLoopCount(rawText, "a chat message");
        long refStart = animations.currentTick();
        String[] lastSent = {animations.resolve(text, refStart)};
        deliverChatFrame(target, lastSent[0]);

        if (!animations.isAnimated(text)) {
            return;
        }

        long duration = animations.duration(text);
        animations.scheduleRedrawLoop(target, refStart, duration, elapsed -> {
            String frame = animations.resolve(text, refStart);
            if (!frame.equals(lastSent[0])) {
                deliverChatFrame(target, frame);
                lastSent[0] = frame;
            }
        }, () -> { });
    }

    private void deliverChatFrame(CommandSender target, String text) {
        if (!text.isEmpty()) {
            target.sendMessage(deserialize(text));
        }
    }

    private Component deserialize(String text) {
        try {
            return MiniText.parse(text);
        } catch (Exception e) {
            logger.warning("Could not parse MiniMessage text '" + text + "': " + e.getMessage());
            return Component.text(text);
        }
    }
}