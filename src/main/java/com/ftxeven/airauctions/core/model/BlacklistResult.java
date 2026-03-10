package com.ftxeven.airauctions.core.model;

public record BlacklistResult(
        boolean blocked,
        String reason
) {
    public static BlacklistResult allowed() {
        return new BlacklistResult(false, null);
    }

    public static BlacklistResult blocked(String reason) {
        return new BlacklistResult(true, reason);
    }
}