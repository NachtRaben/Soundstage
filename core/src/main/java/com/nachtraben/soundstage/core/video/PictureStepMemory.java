package com.nachtraben.soundstage.core.video;

public final class PictureStepMemory {

    public static final long KEEP_NANOS = 10L * 60 * 1_000_000_000L;

    public static final int NO_PICTURE = -1;

    private int height;
    private long steppedNanos;

    public synchronized void stepped(int height, long nanos) {
        this.height = height;
        this.steppedNanos = nanos;
    }

    public synchronized int remembered(long nanos) {
        if (height == 0 || nanos - steppedNanos > KEEP_NANOS) return 0;
        return height;
    }
}
