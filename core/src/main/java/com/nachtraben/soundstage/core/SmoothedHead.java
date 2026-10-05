package com.nachtraben.soundstage.core;

public final class SmoothedHead {

    private static final long FRAME_NANOS = 20_000_000L;

    public static final long REBASE_DEADBAND_FRAMES = 2;

    private long baseSeq = -1;
    private long baseNanos;

    public synchronized void observe(long sequence, long nanoTime) {
        if (baseSeq == -1) {
            baseSeq = sequence;
            baseNanos = nanoTime;
            return;
        }
        long predicted = predict(nanoTime);
        if (Math.abs(sequence - predicted) > REBASE_DEADBAND_FRAMES) {
            baseSeq = sequence;
            baseNanos = nanoTime;
        }
    }

    public synchronized long currentFrame(long nanoTime) {
        if (baseSeq == -1) return -1;
        return predict(nanoTime);
    }

    public synchronized double currentFrameExact(long nanoTime) {
        if (baseSeq == -1) return Double.NaN;
        return baseSeq + (nanoTime - baseNanos) / (double) FRAME_NANOS;
    }

    private long predict(long nanoTime) {
        return baseSeq + (nanoTime - baseNanos) / FRAME_NANOS;
    }

    public synchronized void reset() {
        baseSeq = -1;
    }
}
