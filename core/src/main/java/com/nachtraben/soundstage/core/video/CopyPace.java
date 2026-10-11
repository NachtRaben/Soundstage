package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class CopyPace {

    public static final long FULL_AHEAD_MILLIS = 3_000;

    public static final long WINDOW_NANOS = 4_000_000_000L;

    public static final double KEEP_UP_SPEED = 1.2;

    private final ArrayDeque<long[]> readings = new ArrayDeque<>();
    private double speed;

    public synchronized void restart() {
        readings.clear();
        speed = 0;
    }

    public synchronized boolean behind(long nanos, long madeMillis, long aheadMillis) {
        if (aheadMillis >= FULL_AHEAD_MILLIS) {
            readings.clear();
            return false;
        }
        readings.addLast(new long[]{nanos, madeMillis});
        while (readings.size() > 2) {
            Iterator<long[]> it = readings.iterator();
            it.next();
            if (nanos - it.next()[0] < WINDOW_NANOS) break;
            readings.removeFirst();
        }
        long[] first = readings.peekFirst();
        long span = nanos - first[0];
        if (span < WINDOW_NANOS) return false;
        speed = (madeMillis - first[1]) * 1e6 / span;
        return speed < KEEP_UP_SPEED;
    }

    public synchronized double speed() {
        return speed;
    }
}
