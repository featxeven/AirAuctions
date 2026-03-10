package com.ftxeven.airauctions.core.model;

import java.util.UUID;

public final class PlayerAuctionProfile {
    private final UUID uuid;
    private int totalSold;
    private SkinData skinData;

    public PlayerAuctionProfile(UUID uuid, int totalSold, SkinData skinData) {
        this.uuid = uuid;
        this.totalSold = totalSold;
        this.skinData = skinData;
    }

    public UUID getUuid() { return uuid; }

    public int getTotalSold() { return totalSold; }
    public void setTotalSold(int totalSold) { this.totalSold = totalSold; }
    public void incrementTotalSold() { this.totalSold++; }

    public SkinData getSkinData() { return skinData; }

    public record SkinData(String value, String signature) {
        public boolean hasData() {
            return value != null && !value.isEmpty();
        }
    }
}