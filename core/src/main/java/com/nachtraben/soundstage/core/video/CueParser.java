package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CueParser {

    public static final int MAX_CUE_TEXT_LENGTH = 500;
    public static final int MAX_CUES = 10_000;
    public static final long MIN_CUE_MILLIS = 50;

    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{\\\\[^}]*}");
    private static final Pattern HTML_TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern TIMESTAMP_TAG = Pattern.compile("<(?:(\\d+):)?(\\d{2}):(\\d{2})\\.(\\d{3})>");
    private static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]{1,6}|#\\d{1,7}|[a-zA-Z]{2,8});");
    private static final Pattern TIMING_LINE = Pattern.compile(
            "(?:(\\d+):)?(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*(?:(\\d+):)?(\\d{2}):(\\d{2})[.,](\\d{3})");

    private CueParser() {
    }

    public static List<SubtitleCue> parseWebVtt(String content) {
        return parse(content, true);
    }

    public static List<SubtitleCue> parseSrt(String content) {
        return parse(content, false);
    }

    private static List<SubtitleCue> parse(String content, boolean webVtt) {
        List<SubtitleCue> cues = new ArrayList<>();
        if (content == null || content.isEmpty()) return cues;
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int i = 0;
        while (i < lines.length && cues.size() < MAX_CUES) {
            Matcher timing = TIMING_LINE.matcher(lines[i]);
            if (!timing.find()) {
                i++;
                continue;
            }
            long start = timeMs(timing, 1);
            long end = timeMs(timing, 5);
            i++;
            StringBuilder text = new StringBuilder();
            while (i < lines.length && !(webVtt ? lines[i].isEmpty() : lines[i].isBlank())
                    && !TIMING_LINE.matcher(lines[i]).find()) {
                if (text.length() > 0) text.append('\n');
                text.append(lines[i]);
                i++;
            }
            String raw = text.toString();
            String cleaned = clean(raw);
            if (!cleaned.isEmpty() && end - start >= MIN_CUE_MILLIS) {
                if (cleaned.length() > MAX_CUE_TEXT_LENGTH) cleaned = cleaned.substring(0, MAX_CUE_TEXT_LENGTH);
                cues.add(new SubtitleCue(start, end, cleaned, webVtt ? reveals(raw, start, cleaned) : List.of()));
            }
        }
        return cues;
    }

    private static List<SubtitleCue.Reveal> reveals(String raw, long start, String cleaned) {
        Matcher tag = TIMESTAMP_TAG.matcher(raw);
        List<SubtitleCue.Reveal> reveals = new ArrayList<>();
        long at = start;
        while (tag.find() && reveals.size() < SubtitleCue.MAX_REVEALS - 1) {
            String shown = clean(raw.substring(0, tag.start()));
            if (!cleaned.startsWith(shown)) return List.of();
            reveals.add(new SubtitleCue.Reveal(at, shown.length()));
            at = Math.max(at, timeMs(tag, 1));
        }
        if (reveals.isEmpty()) return List.of();
        reveals.add(new SubtitleCue.Reveal(at, cleaned.length()));
        return reveals;
    }

    private static long timeMs(Matcher m, int groupStart) {
        String hours = m.group(groupStart);
        long h = hours == null ? 0 : Long.parseLong(hours);
        long min = Long.parseLong(m.group(groupStart + 1));
        long sec = Long.parseLong(m.group(groupStart + 2));
        long ms = Long.parseLong(m.group(groupStart + 3));
        return ((h * 60 + min) * 60 + sec) * 1000 + ms;
    }

    private static String clean(String text) {
        String stripped = ASS_OVERRIDE.matcher(text).replaceAll("");
        stripped = TIMESTAMP_TAG.matcher(stripped).replaceAll("");
        stripped = HTML_TAG.matcher(stripped).replaceAll("");
        stripped = decodeEntities(stripped);
        StringBuilder lines = new StringBuilder();
        for (String line : stripped.split("\n")) {
            if (line.isBlank()) continue;
            if (lines.length() > 0) lines.append('\n');
            lines.append(line.strip());
        }
        return lines.toString();
    }

    static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) return text;
        Matcher m = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String decoded = switch (name) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "lrm", "rlm" -> "";
                default -> numeric(name);
            };
            m.appendReplacement(out, Matcher.quoteReplacement(decoded == null ? m.group() : decoded));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String numeric(String name) {
        if (!name.startsWith("#")) return null;
        try {
            int codePoint = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')
                    ? Integer.parseInt(name.substring(2), 16)
                    : Integer.parseInt(name.substring(1));
            if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) return null;
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
