package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public record SubtitleCue(long startMs, long endMs, String text, List<Reveal> reveals) {

    public static final int MAX_REVEALS = 64;

    public record Reveal(long atMillis, int length) {}

    public SubtitleCue {
        reveals = reveals == null ? List.of()
                : List.copyOf(reveals.size() > MAX_REVEALS ? reveals.subList(0, MAX_REVEALS) : reveals);
    }

    public SubtitleCue(long startMs, long endMs, String text) {
        this(startMs, endMs, text, List.of());
    }

    public boolean containsMillis(long timeMs) {
        return timeMs >= startMs && timeMs < endMs;
    }

    public String visibleText(long timeMs) {
        if (reveals.isEmpty()) return text;
        int length = reveals.get(0).length();
        for (Reveal reveal : reveals) {
            if (reveal.atMillis() <= timeMs) length = Math.max(length, reveal.length());
        }
        return text.substring(0, Math.max(0, Math.min(length, text.length()))).strip();
    }

    public SubtitleCue shifted(long offsetMillis) {
        List<Reveal> moved = new ArrayList<>(reveals.size());
        for (Reveal reveal : reveals) moved.add(new Reveal(reveal.atMillis() + offsetMillis, reveal.length()));
        return new SubtitleCue(startMs + offsetMillis, endMs + offsetMillis, text, moved);
    }

    public SubtitleCue mapText(UnaryOperator<String> transform) {
        String mapped = transform.apply(text);
        if (mapped == null || mapped.isEmpty()) return null;
        List<Reveal> kept = new ArrayList<>(reveals.size());
        for (Reveal reveal : reveals) {
            String shown = transform.apply(text.substring(0, Math.max(0, Math.min(reveal.length(), text.length()))));
            if (shown != null && mapped.startsWith(shown)) kept.add(new Reveal(reveal.atMillis(), shown.length()));
        }
        return new SubtitleCue(startMs, endMs, mapped, kept);
    }
}
