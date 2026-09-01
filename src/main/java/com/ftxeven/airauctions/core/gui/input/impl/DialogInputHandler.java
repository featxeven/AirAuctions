package com.ftxeven.airauctions.core.gui.input.impl;

import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.input.CancelBehavior;
import com.ftxeven.airauctions.core.gui.input.InputRegistry;
import com.ftxeven.airauctions.core.gui.input.config.DialogInputConfig;
import com.ftxeven.airauctions.util.Messenger;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DialogInputHandler implements InputRegistry.Handler {

    private static final String INPUT_KEY = "value";
    private static final int MIN_CLIENT_PROTOCOL = 771; // 1.21.6

    private final InputRegistry registry;
    private final Messenger messenger;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public DialogInputHandler(InputRegistry registry, Messenger messenger) {
        this.registry = registry;
        this.messenger = messenger;
    }

    @Override
    public void request(ActionContext context, String inputKey, Consumer<String> onSubmit) {
        DialogInputConfig.Context resolved = registry.dialog().context(inputKey).orElse(null);
        if (resolved == null) {
            context.logger().warning("[dialog input] Unknown context '" + inputKey + "' requested by item '"
                    + context.itemKey() + "' in GUI '" + context.guiId() + "'");
            return;
        }

        Player viewer = context.viewer();
        UUID uuid = viewer.getUniqueId();
        pending.put(uuid, new Pending(context, resolved, onSubmit));

        context.manager().pauseForInput(viewer);
        viewer.showDialog(buildDialog(viewer, resolved, context.placeholders(), uuid));
    }

    @Override
    public void disconnect(UUID uuid) {
        if (pending.remove(uuid) != null) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                viewer.closeDialog();
            }
        }
    }

    public boolean isSupported(Player viewer) {
        int protocol = viewer.getProtocolVersion();
        return protocol < 0 || protocol >= MIN_CLIENT_PROTOCOL;
    }

    // Dialog construction

    private Dialog buildDialog(Player viewer, DialogInputConfig.Context resolved, Map<String, String> placeholders, UUID uuid) {
        return Dialog.create(builder -> builder.empty()
                .base(buildBase(viewer, resolved, placeholders))
                .type(buildType(viewer, resolved, placeholders, uuid)));
    }

    private DialogBase buildBase(Player viewer, DialogInputConfig.Context resolved, Map<String, String> placeholders) {
        List<DialogBody> body = new ArrayList<>();
        for (DialogInputConfig.Entry entry : resolved.body()) {
            body.add(buildBodyEntry(viewer, entry, resolved.width(), placeholders));
        }

        DialogBase.Builder base = DialogBase.builder(messenger.renderLine(viewer, resolved.title(), placeholders))
                .canCloseWithEscape(resolved.canClose())
                .body(body);

        if (!resolved.externalTitle().isBlank()) {
            base.externalTitle(messenger.renderLine(viewer, resolved.externalTitle(), placeholders));
        }

        DialogInputConfig.Field field = resolved.input();
        if (field != null) {
            base.inputs(List.of(buildInput(viewer, field, placeholders)));
        }

        return base.build();
    }

    private DialogBody buildBodyEntry(Player viewer, DialogInputConfig.Entry entry, int dialogWidth, Map<String, String> placeholders) {
        return switch (entry) {
            case DialogInputConfig.Entry.Text text ->
                    DialogBody.plainMessage(messenger.renderLine(viewer, text.text(), placeholders), dialogWidth);
            case DialogInputConfig.Entry.Item item -> buildItemBody(viewer, item, placeholders);
        };
    }

    private DialogBody buildItemBody(Player viewer, DialogInputConfig.Entry.Item item, Map<String, String> placeholders) {
        var builder = DialogBody.item(new ItemStack(item.material()))
                .showDecorations(item.showDecorations())
                .showTooltip(item.showTooltip())
                .width(item.width())
                .height(item.height());

        if (!item.description().isBlank()) {
            builder.description(DialogBody.plainMessage(
                    messenger.renderLine(viewer, item.description(), placeholders),
                    item.descriptionWidth()
            ));
        }

        return builder.build();
    }

    private DialogInput buildInput(Player viewer, DialogInputConfig.Field field, Map<String, String> placeholders) {
        var input = DialogInput.text(INPUT_KEY, messenger.renderLine(viewer, field.label(), placeholders))
                .labelVisible(field.labelVisible())
                .initial(field.initialValue())
                .width(field.width());
        if (field.maxLength() > 0) {
            input.maxLength(field.maxLength());
        }
        return input.build();
    }

    private DialogType buildType(Player viewer, DialogInputConfig.Context resolved, Map<String, String> placeholders, UUID uuid) {
        DialogInputConfig.Buttons buttons = resolved.buttons();

        ActionButton submit = ActionButton.builder(messenger.renderLine(viewer, buttons.submit().text(), placeholders))
                .width(buttons.submit().width())
                .action(DialogAction.customClick(
                        (view, audience) -> handleSubmit(uuid, view),
                        ClickCallback.Options.builder().uses(1).build()
                ))
                .build();

        ActionButton cancel = ActionButton.builder(messenger.renderLine(viewer, buttons.cancel().text(), placeholders))
                .width(buttons.cancel().width())
                .action(DialogAction.customClick(
                        (view, audience) -> handleCancel(uuid),
                        ClickCallback.Options.builder().uses(1).build()
                ))
                .build();

        return DialogType.confirmation(submit, cancel);
    }

    // Responses

    private void handleSubmit(UUID uuid, DialogResponseView view) {
        Pending request = pending.remove(uuid);
        if (request == null) {
            return;
        }
        request.context().viewer().closeDialog();

        String value = view.getText(INPUT_KEY);
        String trimmed = value != null ? value.trim() : "";
        if (trimmed.isEmpty()) {
            request.context().manager().resume(request.context().viewer());
        } else {
            request.onSubmit().accept(trimmed);
        }
    }

    private void handleCancel(UUID uuid) {
        Pending request = pending.remove(uuid);
        if (request == null) {
            return;
        }
        request.context().viewer().closeDialog();
        cancel(request.context(), request.resolved().onCancel());
    }

    private void cancel(ActionContext context, CancelBehavior behavior) {
        if (behavior == CancelBehavior.BACK) {
            context.manager().resume(context.viewer());
        } else {
            context.manager().close(context.viewer());
        }
    }

    private record Pending(ActionContext context, DialogInputConfig.Context resolved, Consumer<String> onSubmit) {}
}