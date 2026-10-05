package com.nachtraben.soundstage.core;

import java.util.Locale;

public final class LoadFailureMessage {

    private LoadFailureMessage() {}

    public static final String GENERIC = "Could not load that track.";

    public static final int MAX_PASSTHROUGH_LENGTH = 100;

    private static final String SIGN_IN_SOURCE = "youtube";

    public static boolean looksLikeAuthFailure(String source, String raw) {
        if (!SIGN_IN_SOURCE.equals(source) || raw == null || raw.isBlank()) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("sign in") || lower.contains("not a bot") || lower.contains("login required");
    }

    public static String forPlayer(String source, String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC;
        }

        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.contains("age") && lower.contains("restrict")) {
            return "That track is age-restricted and cannot be played.";
        }
        if (lower.contains("copyright") || lower.contains("blocked in")) {
            return "That track is blocked and cannot be played.";
        }
        if (looksLikeAuthFailure(source, raw)) {
            return "The source is asking this server to sign in. An operator can run /soundstage oauth.";
        }
        if (lower.contains("unavailable") || lower.contains("not available")
                || lower.contains("private") || lower.contains("removed")) {
            return "That track is unavailable.";
        }
        if (lower.contains("no supported formats") || lower.contains("all clients failed")) {
            return "This source cannot be played right now. The server log has the details.";
        }

        String firstLine = raw.split("\\R", 2)[0].trim();
        if (firstLine.length() <= MAX_PASSTHROUGH_LENGTH && firstLine.length() == raw.trim().length()) {
            return firstLine;
        }
        return GENERIC;
    }

    public static String suspended(String source, String raw, long remainingSeconds) {
        return "Playback is paused for " + remainingSeconds + "s after repeated failures. "
                + forPlayer(source, raw);
    }
}
