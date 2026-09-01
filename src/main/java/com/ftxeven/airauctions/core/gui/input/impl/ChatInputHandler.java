package com.ftxeven.airauctions.core.gui.input.impl;

import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.input.CancelBehavior;
import com.ftxeven.airauctions.core.gui.input.InputRegistry;
import com.ftxeven.airauctions.core.gui.input.config.ChatInputConfig;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.Scheduler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputHandler implements InputRegistry.Handler {

    private final InputRegistry registry;
    private final Messenger messenger;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputHandler(InputRegistry registry, Messenger messenger) {
        this.registry = registry;
        this.messenger = messenger;
    }

    @Override
    public void request(ActionContext context, String inputKey, Consumer<String> onSubmit) {
        ChatInputConfig.Context resolved = registry.chat().context(inputKey).orElse(null);
        if (resolved == null) {
            context.logger().warning("[chat input] Unknown context '" + inputKey + "' requested by item '"
                    + context.itemKey() + "' in GUI '" + context.guiId() + "'");
            return;
        }

        Player viewer = context.viewer();
        pending.put(viewer.getUniqueId(), new Pending(context, resolved, onSubmit));

        context.manager().pauseForInput(viewer);
        Map<String, String> placeholders = new LinkedHashMap<>(context.placeholders());
        placeholders.put("cancel_key", resolved.cancelKey());
        messenger.send(viewer, List.of(resolved.prompt()), placeholders);
    }

    public void handle(AsyncChatEvent event) {
        Player viewer = event.getPlayer();
        Pending request = pending.remove(viewer.getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Scheduler.runEntityLater(viewer, () -> resolve(request, message), 1L);
    }

    @Override
    public void disconnect(UUID uuid) {
        pending.remove(uuid);
    }

    private void resolve(Pending request, String message) {
        if (message.equalsIgnoreCase(request.resolved().cancelKey())) {
            cancel(request.context(), request.resolved().onCancel());
        } else {
            request.onSubmit().accept(message);
        }
    }

    private void cancel(ActionContext context, CancelBehavior behavior) {
        if (behavior == CancelBehavior.BACK) {
            context.manager().resume(context.viewer());
        } else {
            context.manager().close(context.viewer());
        }
    }

    private record Pending(ActionContext context, ChatInputConfig.Context resolved, Consumer<String> onSubmit) {}
}