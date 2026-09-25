package com.ftxeven.airauctions.core.message;

public final class ChatWidth {

    private ChatWidth() {}

    public static final int DEFAULT_CHAT_WIDTH = 308;

    private static final int CHAR_SPACING = 1;
    private static final int FALLBACK_WIDTH = 6;

    public static int measure(String plainText) {
        int width = 0;
        for (int i = 0; i < plainText.length(); i++) {
            width += glyphWidth(plainText.charAt(i)) + CHAR_SPACING;
        }
        return width;
    }

    public static int spaceAdvance() {
        return glyphWidth(' ') + CHAR_SPACING;
    }

    private static int glyphWidth(char c) {
        return switch (c) {
            case 'i', 'l', '.', ',', ':', ';', '\'', '|', '!' -> 1;
            case '`' -> 2;
            case ' ', 'I', '[', ']', '"' -> 3;
            case '(', ')', 'f', 'k', 't', '<', '>', '{', '}' -> 4;
            case '@' -> 6;
            default -> {
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                    yield 5;
                }
                yield FALLBACK_WIDTH;
            }
        };
    }
}