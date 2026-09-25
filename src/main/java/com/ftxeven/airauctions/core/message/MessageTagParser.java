package com.ftxeven.airauctions.core.message;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageTagParser {

    private static final Pattern TAG_START = Pattern.compile("<(sound|actionbar|title|subtitle|bossbar):", Pattern.CASE_INSENSITIVE);

    private static final Pattern CENTER_START = Pattern.compile("<center(?::(\\d+))?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CENTER_END = Pattern.compile("</center>", Pattern.CASE_INSENSITIVE);

    private final Logger logger;

    public MessageTagParser(Logger logger) {
        this.logger = logger;
    }

    public String scan(String line, Consumer<MessageTag> consumer) {
        return scan(line, UnaryOperator.identity(), consumer);
    }

    public String scan(String line, UnaryOperator<String> resolvePlaceholders, Consumer<MessageTag> consumer) {
        return scan(line, resolvePlaceholders, consumer, true);
    }

    public String strip(String line) {
        return scan(line, UnaryOperator.identity(), tag -> { }, false);
    }

    private String scan(String line, UnaryOperator<String> resolvePlaceholders, Consumer<MessageTag> consumer, boolean centerText) {
        StringBuilder result = new StringBuilder();
        Matcher tagMatcher = TAG_START.matcher(line);
        Matcher centerMatcher = CENTER_START.matcher(line);
        int pos = 0;

        while (true) {
            boolean hasTag = tagMatcher.find(pos);
            boolean hasCenter = centerMatcher.find(pos);
            if (!hasTag && !hasCenter) {
                break;
            }
            boolean useCenter = hasCenter && (!hasTag || centerMatcher.start() < tagMatcher.start());

            if (useCenter) {
                result.append(line, pos, centerMatcher.start());
                pos = appendCentered(line, centerMatcher, resolvePlaceholders, consumer, centerText, result);
                continue;
            }

            result.append(line, pos, tagMatcher.start());
            String type = tagMatcher.group(1).toLowerCase(Locale.ROOT);
            ParsedTag parsed = parse(line, tagMatcher, type);
            if (parsed == null) {
                result.append(line, tagMatcher.start(), tagMatcher.end());
                pos = tagMatcher.end();
                continue;
            }
            consumer.accept(resolveText(parsed.tag(), resolvePlaceholders));
            pos = parsed.endIndex();
        }
        result.append(line, pos, line.length());
        return result.toString();
    }

    private int appendCentered(String line, Matcher centerMatcher, UnaryOperator<String> resolvePlaceholders,
                               Consumer<MessageTag> consumer, boolean applyPadding, StringBuilder result) {
        String widthParam = centerMatcher.group(1);
        int contentStart = centerMatcher.end();

        Matcher closeMatcher = CENTER_END.matcher(line);
        boolean hasCloseTag = closeMatcher.find(contentStart);
        int contentEnd = hasCloseTag ? closeMatcher.start() : line.length();
        int nextPos = hasCloseTag ? closeMatcher.end() : line.length();

        String rawInner = line.substring(contentStart, contentEnd);
        String innerLeftover = scan(rawInner, resolvePlaceholders, consumer, false);
        String resolvedInner = resolvePlaceholders.apply(innerLeftover);

        result.append(applyPadding ? centerText(resolvedInner, widthParam) : resolvedInner);
        return nextPos;
    }

    private String centerText(String resolvedInner, String widthParam) {
        int widthPx = widthParam != null ? parseWidth(widthParam) : ChatWidth.DEFAULT_CHAT_WIDTH;
        String plain = MiniMessage.miniMessage().stripTags(resolvedInner);
        int textWidth = ChatWidth.measure(plain);
        int padding = (widthPx - textWidth) / 2;
        if (padding <= 0) {
            return resolvedInner;
        }
        int spaces = padding / ChatWidth.spaceAdvance();
        return " ".repeat(spaces) + resolvedInner;
    }

    private int parseWidth(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            logger.warning("Invalid <center> width '" + raw + "', using default (" + ChatWidth.DEFAULT_CHAT_WIDTH + ")");
            return ChatWidth.DEFAULT_CHAT_WIDTH;
        }
    }

    private MessageTag resolveText(MessageTag tag, UnaryOperator<String> resolvePlaceholders) {
        return switch (tag) {
            case MessageTag.Sound sound -> sound;
            case MessageTag.ActionBar actionBar -> new MessageTag.ActionBar(resolvePlaceholders.apply(actionBar.text()));
            case MessageTag.Title title -> new MessageTag.Title(
                    resolvePlaceholders.apply(title.text()), title.fadeInTicks(), title.stayTicks(), title.fadeOutTicks());
            case MessageTag.Subtitle subtitle -> new MessageTag.Subtitle(resolvePlaceholders.apply(subtitle.text()));
            case MessageTag.BossBar bossBar -> new MessageTag.BossBar(
                    resolvePlaceholders.apply(bossBar.text()), bossBar.durationTicks(), bossBar.color(),
                    bossBar.overlay(), bossBar.initialProgress(), bossBar.countdown());
        };
    }

    private record ParsedTag(MessageTag tag, int endIndex) {}
    private record QuotedResult(String text, int closingQuoteIndex) {}

    private ParsedTag parse(String line, Matcher matcher, String type) {
        int contentStart = matcher.end();

        if (type.equals("sound")) {
            int close = line.indexOf('>', contentStart);
            if (close < 0) {
                logger.warning("Unterminated <sound:...> tag in message: " + line);
                return null;
            }
            String[] parts = line.substring(contentStart, close).split(":");
            if (parts.length < 3) {
                logger.warning("Malformed <sound:...> tag (expected name:volume:pitch) in message: " + line);
                return null;
            }
            String key = String.join(":", Arrays.copyOfRange(parts, 0, parts.length - 2));
            float volume = parseFloat(parts[parts.length - 2], 1f, "sound volume");
            float pitch = parseFloat(parts[parts.length - 1], 1f, "sound pitch");
            return new ParsedTag(new MessageTag.Sound(key, volume, pitch), close + 1);
        }

        if (contentStart >= line.length() || line.charAt(contentStart) != '\'') {
            logger.warning("Tag <" + type + ":...> is missing its quoted value in message: " + line);
            return null;
        }

        QuotedResult quoted = scanQuoted(line, contentStart + 1);
        if (quoted == null) {
            logger.warning("Unterminated quoted value in <" + type + ":...> tag in message: " + line);
            return null;
        }

        int afterQuote = quoted.closingQuoteIndex() + 1;
        int close = line.indexOf('>', afterQuote);
        if (close < 0) {
            logger.warning("Unterminated <" + type + ":...> tag in message: " + line);
            return null;
        }

        String paramsRaw = line.substring(afterQuote, close);
        List<String> params = paramsRaw.isEmpty() ? List.of() : Arrays.asList(paramsRaw.substring(1).split(":", -1));

        MessageTag tag = switch (type) {
            case "actionbar" -> new MessageTag.ActionBar(quoted.text());
            case "subtitle" -> new MessageTag.Subtitle(quoted.text());
            case "title" -> parseTitle(quoted.text(), params);
            case "bossbar" -> parseBossBar(quoted.text(), params);
            default -> throw new IllegalStateException("Unreachable - TAG_START only matches known tag types: " + type);
        };
        return new ParsedTag(tag, close + 1);
    }

    private MessageTag.Title parseTitle(String text, List<String> params) {
        if (params.size() != 3) {
            logger.warning("Title tag expects fadeIn:stay:fadeOut timing, using defaults (20/60/20)");
            return new MessageTag.Title(text, 20, 60, 20);
        }
        return new MessageTag.Title(text,
                parseTicks(params.get(0), 20, "title fade-in"),
                parseTicks(params.get(1), 60, "title stay"),
                parseTicks(params.get(2), 20, "title fade-out"));
    }

    private MessageTag.BossBar parseBossBar(String text, List<String> params) {
        long duration = Math.max(1L, parseTicks(paramOr(params, 0), 60, "bossbar duration"));
        BossBar.Color color = parseColor(paramOr(params, 1));
        BossBar.Overlay overlay = parseOverlay(paramOr(params, 2));
        float progress = parseProgress(paramOr(params, 3));
        boolean countdown = parseBoolean(paramOr(params, 4));
        return new MessageTag.BossBar(text, duration, color, overlay, progress, countdown);
    }

    private QuotedResult scanQuoted(String line, int start) {
        StringBuilder text = new StringBuilder();
        int i = start;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\'') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    text.append('\'');
                    i += 2;
                    continue;
                }
                if (isTagTerminator(line, i + 1)) {
                    return new QuotedResult(text.toString(), i);
                }
            }
            text.append(c);
            i++;
        }
        return null;
    }

    private boolean isTagTerminator(String line, int afterQuote) {
        if (afterQuote >= line.length()) {
            return true; // truncated tag
        }
        char next = line.charAt(afterQuote);
        return next == '>' || next == ':';
    }

    private String paramOr(List<String> params, int index) {
        return index < params.size() ? params.get(index) : null;
    }

    private long parseTicks(String raw, long fallback, String label) {
        if (raw == null) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            logger.warning("Invalid " + label + " '" + raw + "', using " + fallback);
            return fallback;
        }
    }

    private float parseFloat(String raw, float fallback, String label) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            logger.warning("Invalid " + label + " '" + raw + "', using " + fallback);
            return fallback;
        }
    }

    private float parseProgress(String raw) {
        if (raw == null) return 1f;
        try {
            return Math.clamp(Float.parseFloat(raw.trim()), 0f, 1f);
        } catch (NumberFormatException e) {
            logger.warning("Invalid bossbar progress '" + raw + "', using 1.0");
            return 1f;
        }
    }

    private boolean parseBoolean(String raw) {
        return raw != null && Boolean.parseBoolean(raw.trim());
    }

    private BossBar.Color parseColor(String raw) {
        if (raw == null) return BossBar.Color.WHITE;
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid bossbar color '" + raw + "', using WHITE");
            return BossBar.Color.WHITE;
        }
    }

    private BossBar.Overlay parseOverlay(String raw) {
        if (raw == null) return BossBar.Overlay.PROGRESS;
        try {
            return BossBar.Overlay.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid bossbar overlay '" + raw + "', using PROGRESS");
            return BossBar.Overlay.PROGRESS;
        }
    }
}