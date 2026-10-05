package com.nachtraben.soundstage.core;

public final class TrackDuration {

    private TrackDuration() {}

    public static long reported(long rawMillis) {
        if (rawMillis <= 0 || rawMillis == Long.MAX_VALUE) return 0;
        return rawMillis;
    }

    public static boolean isKnown(long rawMillis) {
        return reported(rawMillis) > 0;
    }
}
