package com.nachtraben.soundstage.core;

import java.util.ArrayDeque;

public final class ArrivalLateness {

    private static final long FRAME_NANOS = 20_000_000L;

    public static final long BASELINE_WINDOW_NANOS = 10_000_000_000L;

    public static final long PEAK_WINDOW_NANOS = 300_000_000_000L;

    public static final long MARGIN_NANOS = 300_000_000L;

    public static final int MAX_LEAD_FRAMES = 75;

    private record Sample(long nanos, long value) {}

    private final ArrayDeque<Sample> baseline = new ArrayDeque<>();
    private final ArrayDeque<Sample> peaks = new ArrayDeque<>();

    public void observe(long sequence, long nanos) {
        long offset = nanos - sequence * FRAME_NANOS;
        while (!baseline.isEmpty() && nanos - baseline.peekFirst().nanos() > BASELINE_WINDOW_NANOS) {
            baseline.pollFirst();
        }
        while (!baseline.isEmpty() && baseline.peekLast().value() >= offset) {
            baseline.pollLast();
        }
        baseline.addLast(new Sample(nanos, offset));
        long lateness = offset - baseline.peekFirst().value();

        evictPeaks(nanos);
        while (!peaks.isEmpty() && peaks.peekLast().value() <= lateness) {
            peaks.pollLast();
        }
        peaks.addLast(new Sample(nanos, lateness));
    }

    public void rebase() {
        baseline.clear();
    }

    public void reset() {
        baseline.clear();
        peaks.clear();
    }

    public long peakLatenessNanos(long nanos) {
        evictPeaks(nanos);
        return peaks.isEmpty() ? 0L : peaks.peekFirst().value();
    }

    public int leadFrames(long nanos, int minimumFrames) {
        long needed = peakLatenessNanos(nanos) + MARGIN_NANOS;
        long frames = (needed + FRAME_NANOS - 1) / FRAME_NANOS;
        return (int) Math.max(minimumFrames, Math.min(MAX_LEAD_FRAMES, frames));
    }

    private void evictPeaks(long nanos) {
        while (!peaks.isEmpty() && nanos - peaks.peekFirst().nanos() > PEAK_WINDOW_NANOS) {
            peaks.pollFirst();
        }
    }
}
