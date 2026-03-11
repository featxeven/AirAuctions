package com.ftxeven.airauctions.core.model;

import java.util.UUID;

public final class PlayerAuctionProfile {
    private final UUID uuid;
    private final SkinData skinData;

    public PlayerAuctionProfile(UUID uuid, int totalSold, SkinData skinData) {
        this.uuid = uuid;
        this.skinData = skinData;
    }

    public UUID getUuid() { return uuid; }

    public SkinData getSkinData() { return skinData; }

    public record SkinData(String value, String signature) {
        public boolean hasData() {
            return value != null && !value.isEmpty();
        }
    }
}