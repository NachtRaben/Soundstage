package com.nachtraben.soundstage.core.video;

import com.nachtraben.soundstage.core.SeekTracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class VideoRelayState<V> {

    public static final long LOOKAHEAD_MILLIS = 600;

    public static final long CLOCK_INTERVAL_NANOS = 500_000_000L;

    public static final long MAX_QUEUED_MILLIS = 20_000;

    static final int MAX_QUEUED_UNITS = VideoUnitQueue.MAX_UNITS;
    static final long MAX_QUEUED_BYTES = VideoUnitQueue.MAX_BYTES;

    private static final byte[] NONE = new byte[0];

    private static final AtomicInteger GENERATIONS = new AtomicInteger();

    public enum Kind { START, FRAME, CLOCK, STOP }

    public record Send<P>(Kind kind, int generation, long millis, boolean flag, byte[] data, List<P> to) {}

    public enum Accepted {
        QUEUED,
        IGNORED,
        TOO_LARGE
    }

    private final int maxUnitBytes;
    private final BooleanSupplier paused;

    private Object attached;
    private LongSupplier position;
    private Run run;
    private final List<Send<V>> pendingStops = new ArrayList<>();
    private boolean closed;
    private long tooLarge;
    private int firstTooLargeBytes;
    private long shed;

    public VideoRelayState(int maxUnitBytes, BooleanSupplier paused) {
        this.maxUnitBytes = maxUnitBytes;
        this.paused = paused;
    }

    public synchronized void attach(Object track, LongSupplier position) {
        if (closed) return;
        closeRun();
        attached = track;
        this.position = position;
    }

    public synchronized void detach(Object track, boolean reclaimed) {
        if (closed || track != attached) return;
        attached = null;
        position = null;
        if (run == null) return;
        if (reclaimed && !run.voided) {
            run.frozen = true;
            run.finished = false;
            clearQueue(run);
        } else {
            closeRun();
        }
    }

    public synchronized void seeking(Object track) {
        if (closed || track != attached || run == null) return;
        run.voided = true;
        clearQueue(run);
    }

    public synchronized void abandon(Object track) {
        if (closed || track != attached) return;
        closeRun();
    }

    public synchronized void endPicture() {
        if (closed) return;
        attached = null;
        position = null;
        closeRun();
    }

    public synchronized void begin(Object track, int trackGeneration, long startMillis) {
        if (closed || track != attached) return;
        run = new Run(trackGeneration, startMillis, paused.getAsBoolean());
    }

    public synchronized Accepted accept(Object track, int trackGeneration, long ptsMillis, boolean keyframe,
                                        byte[] data) {
        Run current = run;
        if (closed || track != attached || current == null || current.trackGeneration != trackGeneration
                || current.voided || current.finished) {
            return Accepted.IGNORED;
        }
        if (data.length > maxUnitBytes) {
            if (tooLarge++ == 0) firstTooLargeBytes = data.length;
            current.gapPending = true;
            return Accepted.TOO_LARGE;
        }
        current.queue.addLast(new Unit(ptsMillis, keyframe, data, current.gapPending));
        current.gapPending = false;
        current.queuedBytes += data.length;
        while (current.overBound()) shedOldestGroup(current);
        return Accepted.QUEUED;
    }

    public synchronized void end(Object track, int trackGeneration) {
        Run current = run;
        if (closed || track != attached || current == null || current.trackGeneration != trackGeneration
                || current.voided) {
            return;
        }
        current.finished = true;
    }

    public synchronized boolean wantsAudience() {
        return !closed && run != null;
    }

    public synchronized void poll(long nowNanos, Collection<? extends V> audience, List<Send<V>> out) {
        out.addAll(pendingStops);
        pendingStops.clear();
        Run current = run;
        if (closed || current == null) return;
        if (!current.voided && !current.frozen) {
            current.position = current.tracker.reportedPosition(position.getAsLong());
        }

        current.viewers.keySet().retainAll(audience);
        List<V> arrivals = new ArrayList<>();
        for (V viewer : audience) {
            if (!current.viewers.containsKey(viewer)) {
                current.viewers.put(viewer, new Viewer());
                arrivals.add(viewer);
            }
        }
        if (!arrivals.isEmpty()) {
            out.add(new Send<>(Kind.START, current.generation, 0L, false, NONE, arrivals));
            current.clockDue = true;
        }
        if (current.voided) return;

        sendDuePictures(current, out);

        boolean isPaused = paused.getAsBoolean();
        if (isPaused != current.wasPaused) {
            current.wasPaused = isPaused;
            current.clockDue = true;
        }
        if (current.clockDue || nowNanos - current.nextClockNanos >= 0) {
            current.clockDue = false;
            current.nextClockNanos = nowNanos + CLOCK_INTERVAL_NANOS;
            if (!current.viewers.isEmpty()) {
                out.add(new Send<>(Kind.CLOCK, current.generation, current.position, isPaused, NONE,
                        viewersOf(current)));
            }
        }

        if (current.finished && current.queue.isEmpty() && current.position >= current.lastSentMillis) {
            stop(current, out);
            run = null;
        }
    }

    public synchronized void close(List<Send<V>> out) {
        if (closed) return;
        closeRun();
        out.addAll(pendingStops);
        pendingStops.clear();
        closed = true;
        attached = null;
        position = null;
    }

    public synchronized long tooLargeUnits() {
        return tooLarge;
    }

    public synchronized int firstTooLargeBytes() {
        return firstTooLargeBytes;
    }

    public synchronized long shedUnits() {
        return shed;
    }

    private void sendDuePictures(Run current, List<Send<V>> out) {
        long due = current.position + LOOKAHEAD_MILLIS;
        Unit unit;
        while ((unit = current.queue.peekFirst()) != null && unit.ptsMillis() <= due) {
            current.queue.pollFirst();
            current.queuedBytes -= unit.data().length;
            current.lastSentMillis = Math.max(current.lastSentMillis, unit.ptsMillis());
            List<V> to = new ArrayList<>(current.viewers.size());
            for (Map.Entry<V, Viewer> entry : current.viewers.entrySet()) {
                Viewer viewer = entry.getValue();
                if (unit.keyframe()) {
                    viewer.awaitingKeyframe = false;
                } else if (unit.afterGap()) {
                    viewer.awaitingKeyframe = true;
                }
                if (!viewer.awaitingKeyframe) to.add(entry.getKey());
            }
            if (!to.isEmpty()) {
                out.add(new Send<>(Kind.FRAME, current.generation, unit.ptsMillis(), unit.keyframe(), unit.data(), to));
            }
        }
    }

    private void shedOldestGroup(Run current) {
        do {
            Unit dropped = current.queue.pollFirst();
            current.queuedBytes -= dropped.data().length;
            shed++;
        } while (!current.queue.isEmpty() && !current.queue.peekFirst().keyframe());
        if (current.queue.isEmpty()) current.gapPending = true;
    }

    private void clearQueue(Run current) {
        current.queue.clear();
        current.queuedBytes = 0;
    }

    private void closeRun() {
        if (run != null) {
            stop(run, pendingStops);
            run = null;
        }
    }

    private void stop(Run current, List<Send<V>> out) {
        if (!current.viewers.isEmpty()) {
            out.add(new Send<>(Kind.STOP, current.generation, 0L, false, NONE, viewersOf(current)));
        }
    }

    private List<V> viewersOf(Run current) {
        return new ArrayList<>(current.viewers.keySet());
    }

    private record Unit(long ptsMillis, boolean keyframe, byte[] data, boolean afterGap) {}

    private static final class Viewer {
        boolean awaitingKeyframe = true;
    }

    private final class Run {
        final int generation = GENERATIONS.incrementAndGet();
        final int trackGeneration;
        final SeekTracker tracker = new SeekTracker();
        final ArrayDeque<Unit> queue = new ArrayDeque<>();
        long queuedBytes;
        final Map<V, Viewer> viewers = new LinkedHashMap<>();
        boolean finished;
        boolean voided;
        boolean frozen;
        boolean gapPending;
        long position;
        long lastSentMillis = Long.MIN_VALUE;
        boolean clockDue = true;
        long nextClockNanos;
        boolean wasPaused;

        Run(int trackGeneration, long startMillis, boolean paused) {
            this.trackGeneration = trackGeneration;
            this.position = startMillis;
            this.wasPaused = paused;
            tracker.requested(startMillis);
        }

        boolean overBound() {
            if (queue.size() > MAX_QUEUED_UNITS || queuedBytes > MAX_QUEUED_BYTES) return true;
            return queue.size() > 1 && queue.peekLast().ptsMillis() - queue.peekFirst().ptsMillis() > MAX_QUEUED_MILLIS;
        }
    }
}
