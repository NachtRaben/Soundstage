package com.nachtraben.soundstage.core;

public final class SeekTracker {

    public static final long SETTLED_MILLIS = 1500;

    private long target = -1;

    public void requested(long position) {
        target = position;
    }

    public long reportedPosition(long actualPosition) {
        if (target < 0) {
            return actualPosition;
        }
        if (Math.abs(actualPosition - target) <= SETTLED_MILLIS) {
            target = -1;
            return actualPosition;
        }
        return target;
    }

    public void clear() {
        target = -1;
    }

    public boolean isPending() {
        return target >= 0;
    }
}
