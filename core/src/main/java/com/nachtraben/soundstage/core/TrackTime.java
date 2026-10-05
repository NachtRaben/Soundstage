package com.nachtraben.soundstage.core;

public final class TrackTime {

    private TrackTime() {
    }

    public static String format(long millis, long lengthMillis) {
        long seconds = Math.max(0, millis) / 1000;
        boolean hours = seconds >= 3600 || lengthMillis >= 3_600_000L && lengthMillis != Long.MAX_VALUE;
        long h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        return hours ? h + ":" + two(m) + ":" + two(s) : (seconds / 60) + ":" + two(s);
    }

    public static String format(long millis) {
        return format(millis, -1);
    }

    private static String two(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }
}
