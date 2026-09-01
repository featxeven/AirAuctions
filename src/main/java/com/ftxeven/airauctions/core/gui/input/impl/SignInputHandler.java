package com.ftxeven.airauctions.core.gui.input.impl;

import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.input.InputRegistry;
import com.ftxeven.airauctions.core.gui.input.config.SignInputConfig;
import com.ftxeven.airauctions.util.Messenger;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SignInputHandler implements InputRegistry.Handler {

    private static final Material SIGN_MATERIAL = Material.OAK_SIGN;
    private static final int INPUT_LINE = 0;
    private static final int MIN_CLIENT_PROTOCOL = 763; // 1.20

    private final InputRegistry registry;
    private final Messenger messenger;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SignInputHandler(InputRegistry registry, Messenger messenger) {
        this.registry = registry;
        this.messenger = messenger;
    }

    @Override
    public void request(ActionContext context, String inputKey, Consumer<String> onSubmit) {
        SignInputConfig.Context resolved = registry.sign().context(inputKey).orElse(null);
        if (resolved == null) {
            context.logger().warning("[sign input] Unknown context '" + inputKey + "' requested by item '"
                    + context.itemKey() + "' in GUI '" + context.guiId() + "'");
            return;
        }

        Player viewer = context.viewer();
        pending.put(viewer.getUniqueId(), new Pending(context, resolved, onSubmit));

        context.manager().pauseForInput(viewer);
        openEditor(viewer, resolved, context.placeholders());
    }

    @Override
    public void disconnect(UUID uuid) {
        pending.remove(uuid);
    }

    public boolean isSupported(Player viewer) {
        int protocol = viewer.getProtocolVersion();
        return protocol < 0 || protocol >= MIN_CLIENT_PROTOCOL;
    }

    // Sign construction

    private void openEditor(Player viewer, SignInputConfig.Context resolved, Map<String, String> placeholders) {
        Location location = viewer.getLocation();

        BlockData blockData = SIGN_MATERIAL.createBlockData();
        Sign sign = (Sign) blockData.createBlockState();
        applyLines(viewer, sign, resolved, placeholders);

        viewer.sendBlockChange(location, blockData);
        viewer.sendBlockUpdate(location, sign);
        viewer.openVirtualSign(location, Side.FRONT);
    }

    private void applyLines(Player viewer, Sign sign, SignInputConfig.Context resolved, Map<String, String> placeholders) {
        SignSide front = sign.getSide(Side.FRONT);
        for (Map.Entry<Integer, String> entry : resolved.lines().entrySet()) {
            int line = entry.getKey();
            if (line < 1 || line > 3) {
                continue;
            }
            front.line(line, messenger.renderLine(viewer, entry.getValue(), placeholders));
        }
        front.line(INPUT_LINE, Component.empty());
    }

    // Response

    public void handle(UncheckedSignChangeEvent event) {
        Player viewer = event.getPlayer();
        Pending request = pending.remove(viewer.getUniqueId());
        if (request == null) {
            return;
        }

        event.setCancelled(true);
        viewer.sendBlockChange(viewer.getLocation(), viewer.getLocation().getBlock().getBlockData());

        String value = plainLine(event.lines().get(INPUT_LINE));
        if (value.isEmpty()) {
            request.context().manager().resume(viewer);
        } else {
            request.onSubmit().accept(value);
        }
    }

    private String plainLine(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component).trim();
    }

    private record Pending(ActionContext context, SignInputConfig.Context resolved, Consumer<String> onSubmit) {}
}