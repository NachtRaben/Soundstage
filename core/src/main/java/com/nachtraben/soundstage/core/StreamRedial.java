package com.nachtraben.soundstage.core;

public final class StreamRedial {

    private StreamRedial() {}

    public static boolean shouldRedial(boolean live, long playedMillis, long workingMillis) {
        if (!live) return false;
        return playedMillis >= workingMillis;
    }
}
