package com.ftxeven.airauctions.gui.render;

import com.ftxeven.airauctions.core.gui.render.MaterialResolver;
import com.ftxeven.airauctions.model.PlayerData;
import com.ftxeven.airauctions.service.player.PlayerService;
import com.ftxeven.airauctions.service.simulation.PlayerPool;

import java.util.Optional;
import java.util.UUID;

public final class PlayerHeadResolver implements MaterialResolver.HeadResolver {

    private static final String SYNTHETIC_SIGNATURE_MARKER = "synthetic";

    private final PlayerService players;

    public PlayerHeadResolver(PlayerService players) {
        this.players = players;
    }

    @Override
    public Optional<MaterialResolver.CachedHead> byUuid(UUID uuid) {
        Optional<MaterialResolver.CachedHead> synthetic = PlayerPool.skinFor(uuid).map(PlayerHeadResolver::toCachedHead);
        return synthetic.isPresent() ? synthetic : players.find(uuid).map(PlayerHeadResolver::toHead);
    }

    @Override
    public Optional<MaterialResolver.CachedHead> byName(String name) {
        Optional<MaterialResolver.CachedHead> synthetic = PlayerPool.skinFor(name).map(PlayerHeadResolver::toCachedHead);
        return synthetic.isPresent() ? synthetic : players.findByName(name).map(PlayerHeadResolver::toHead);
    }

    private static MaterialResolver.CachedHead toCachedHead(PlayerPool.SyntheticSkin skin) {
        return new MaterialResolver.CachedHead(skin.uuid(), skin.name(), skin.textureValue(), SYNTHETIC_SIGNATURE_MARKER);
    }

    private static MaterialResolver.CachedHead toHead(PlayerData data) {
        PlayerData.Skin skin = data.skin();
        return new MaterialResolver.CachedHead(data.uuid(), data.name(), skin.value(), skin.signature());
    }
}