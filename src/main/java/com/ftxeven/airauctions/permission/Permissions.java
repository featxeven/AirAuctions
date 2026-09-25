package com.ftxeven.airauctions.permission;

public final class Permissions {

    public static final String ADMIN = "airauctions.admin";

    private Permissions() {
    }

    public static String command(String key) {
        return "airauctions.command." + key;
    }

    public static String commandOthers(String key) {
        return command(key) + ".others";
    }

    public static final class Bypass {

        public static final String GAMEMODES = "airauctions.bypass.gamemodes";
        public static final String WORLDS = "airauctions.bypass.worlds";
        public static final String DAMAGED_ITEMS = "airauctions.bypass.damaged-items";
        public static final String BLACKLIST = "airauctions.bypass.blacklist";
        public static final String COOLDOWN = "airauctions.bypass.cooldown";
        public static final String LIMIT = "airauctions.bypass.limit";
        public static final String EXPIRE_TIME = "airauctions.bypass.expire-time";
        public static final String DURATION_TIME = "airauctions.bypass.duration-time";
        public static final String FEE = "airauctions.bypass.fee";
        public static final String TAX = "airauctions.bypass.tax";

        private Bypass() {
        }
    }
}