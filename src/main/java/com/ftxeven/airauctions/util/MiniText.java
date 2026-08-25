package com.ftxeven.airauctions.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.concurrent.ConcurrentHashMap;

public final class MiniText {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int MAX_CACHE_SIZE = 1024;
    private static final ConcurrentHashMap<String, Component> CACHE = new ConcurrentHashMap<>();

    private MiniText() {
    }

    public static MiniMessage mini() {
        return MINI;
    }

    public static Component parse(String miniMessageText) {
        Component cached = CACHE.get(miniMessageText);
        if (cached != null) {
            return cached;
        }
        Component parsed = MINI.deserialize(miniMessageText);
        CACHE.put(miniMessageText, parsed);
        if (CACHE.size() > MAX_CACHE_SIZE) {
            CACHE.clear();
        }
        return parsed;
    }

    public static String plain(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    public static String plain(String miniMessageText) {
        return plain(parse(miniMessageText));
    }
}