package com.nachtraben.soundstage.core.video;

public final class LivePace {

    public static final long GRACE_NANOS = 10_000_000_000L;

    public static final long WINDOW_NANOS = 6_000_000_000L;

    private static final long NONE = Long.MIN_VALUE;

    private long startNanos = NONE;
    private long behindSince = NONE;

    public synchronized void start(long nanos) {
        startNanos = nanos;
        behindSince = NONE;
    }

    public synchronized boolean behind(long nanos, long newestMillis, long playedMillis) {
        if (startNanos == NONE || nanos - startNanos < GRACE_NANOS) return false;
        if (newestMillis < 0) return true;
        if (newestMillis >= playedMillis) {
            behindSince = NONE;
            return false;
        }
        if (behindSince == NONE) behindSince = nanos;
        return nanos - behindSince >= WINDOW_NANOS;
    }
}
