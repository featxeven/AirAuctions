package com.ftxeven.airauctions.listener;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.Version;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public final class PlayerListener implements Listener {

    private final ServiceManager services;
    private final ConfigManager configs;
    private final Messenger messenger;
    private final GuiManager guis;

    public PlayerListener(ServiceManager services, ConfigManager configs, Messenger messenger, GuiManager guis) {
        this.services = services;
        this.configs = configs;
        this.messenger = messenger;
        this.guis = guis;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        services.players().handleJoin(player, () -> services.notifications().scheduleNotifications(player));
        notifyIfOutdated(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        guis.disconnect(uuid);
        services.validator().clearCooldown(uuid);
        services.confirmations().clear(uuid);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        guis.input().handleChat(event);
    }

    @EventHandler
    public void onSign(UncheckedSignChangeEvent event) {
        guis.input().handleSign(event);
    }

    private void notifyIfOutdated(Player player) {
        if (!configs.main().general().notifyUpdates() || !Version.isOutdated() || !player.hasPermission(Permissions.ADMIN)) {
            return;
        }
        messenger.send(player, configs.lang().get("general.commands.outdated"), Map.of(
                "current", Version.current(),
                "latest", Version.getLatest()
        ));
    }
}