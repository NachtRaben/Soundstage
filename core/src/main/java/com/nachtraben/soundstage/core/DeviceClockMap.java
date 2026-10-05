package com.nachtraben.soundstage.core;

public final class DeviceClockMap {

    public static final long WINDOW_NANOS = 1_000_000_000L;

    public static final long MAX_LATENCY_NANOS = PcmRing.MAX_OUTPUT_LATENCY_FRAMES * 20_000_000L;

    public static final long UNKNOWN = Long.MIN_VALUE;

    private long[] maximaAt = new long[64];
    private long[] maximaValue = new long[64];
    private int maximaHead;
    private int maximaSize;
    private long lastClockNanos = Long.MIN_VALUE;
    private long latencyNanos;

    public void offer(long clockNanos, long latencyNanos, long sampledAtNanos) {
        if (clockNanos < lastClockNanos) reset();
        lastClockNanos = clockNanos;
        long latency = Math.max(0L, Math.min(MAX_LATENCY_NANOS, latencyNanos));
        this.latencyNanos = latency;
        long offset = clockNanos - latency - sampledAtNanos;
        while (maximaSize > 0 && maximaValue[slot(maximaSize - 1)] <= offset) maximaSize--;
        if (maximaSize == maximaAt.length) grow();
        int tail = slot(maximaSize);
        maximaAt[tail] = sampledAtNanos;
        maximaValue[tail] = offset;
        maximaSize++;
        while (maximaAt[maximaHead] < sampledAtNanos - WINDOW_NANOS) {
            maximaHead = slot(1);
            maximaSize--;
        }
    }

    private int slot(int fromOldest) {
        return (maximaHead + fromOldest) % maximaAt.length;
    }

    private void grow() {
        long[] at = new long[maximaAt.length * 2];
        long[] value = new long[at.length];
        for (int i = 0; i < maximaSize; i++) {
            at[i] = maximaAt[slot(i)];
            value[i] = maximaValue[slot(i)];
        }
        maximaAt = at;
        maximaValue = value;
        maximaHead = 0;
    }

    public long heardOffsetNanos() {
        return maximaSize == 0 ? UNKNOWN : maximaValue[maximaHead];
    }

    public long latencyNanos() {
        return latencyNanos;
    }

    public void reset() {
        maximaHead = 0;
        maximaSize = 0;
        lastClockNanos = Long.MIN_VALUE;
        latencyNanos = 0L;
    }
}
