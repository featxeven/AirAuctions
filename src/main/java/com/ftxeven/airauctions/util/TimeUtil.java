package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.AirAuctions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class TimeUtil {

    private TimeUtil() {}

    public static String formatSeconds(AirAuctions plugin, long totalSeconds) {
        long seconds = totalSeconds;
        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;
        seconds %= 60;

        String day = String.valueOf(plugin.lang().get("placeholders.day"));
        String daysStr = String.valueOf(plugin.lang().get("placeholders.days"));
        String hour = String.valueOf(plugin.lang().get("placeholders.hour"));
        String hoursStr = String.valueOf(plugin.lang().get("placeholders.hours"));
        String minute = String.valueOf(plugin.lang().get("placeholders.minute"));
        String minutesStr = String.valueOf(plugin.lang().get("placeholders.minutes"));
        String second = String.valueOf(plugin.lang().get("placeholders.second"));
        String secondsStr = String.valueOf(plugin.lang().get("placeholders.seconds"));

        String mode = plugin.config().timeFormatMode();
        int granularity = plugin.config().timeFormatGranularity();

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + (days == 1 ? day : daysStr));
        if (hours > 0) parts.add(hours + (hours == 1 ? hour : hoursStr));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? minute : minutesStr));
        if (seconds > 0 || parts.isEmpty()) parts.add(seconds + (seconds == 1 ? second : secondsStr));

        return switch (mode.toUpperCase()) {
            case "SEQUENTIAL" -> parts.getFirst();
            case "CUSTOM" -> String.join(" ", parts.subList(0, Math.min(granularity, parts.size())));
            default -> String.join(" ", parts);
        };
    }

    public static String formatDate(AirAuctions plugin, long timestamp) {
        String pattern = plugin.config().getDateFormat();
        try {
            return new SimpleDateFormat(pattern).format(new Date(timestamp));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid date-format in config: " + pattern);
            return new SimpleDateFormat("dd/MM/yy").format(new Date(timestamp));
        }
    }
}