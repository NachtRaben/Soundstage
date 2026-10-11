package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;

public final class VideoUnitQueue {

    public static final long DECODE_AHEAD_MILLIS = 250;
    public static final long SKIP_BEHIND_MILLIS = 1000;

    static final int MAX_UNITS = 1800;
    static final long MAX_BYTES = 32L * 1024 * 1024;

    public record Unit(long ptsMillis, boolean keyframe, byte[] data) {}

    private final ArrayDeque<Unit> units = new ArrayDeque<>();
    private long bytes;
    private boolean awaitingKeyframe = true;
    private long skips;

    public void offer(Unit unit) {
        if (awaitingKeyframe && units.isEmpty() && !unit.keyframe()) return;
        units.addLast(unit);
        bytes += unit.data().length;
        if (units.size() > MAX_UNITS || bytes > MAX_BYTES) shed();
    }

    public Unit next(double clockMillis) {
        return next(clockMillis, DECODE_AHEAD_MILLIS);
    }

    public Unit next(double clockMillis, long aheadMillis) {
        dropUntilKeyframe();
        Unit head = units.peekFirst();
        if (head == null || Double.isNaN(clockMillis)) return null;
        if (clockMillis - head.ptsMillis() > SKIP_BEHIND_MILLIS && skipTo(clockMillis)) {
            head = units.peekFirst();
        }
        if (head.ptsMillis() > clockMillis + aheadMillis) return null;
        return poll();
    }

    public Unit nextKeyframe(double clockMillis) {
        if (Double.isNaN(clockMillis)) return null;
        while (!units.isEmpty()) {
            Unit head = units.peekFirst();
            if (head.keyframe() && head.ptsMillis() >= clockMillis) {
                awaitingKeyframe = false;
                return poll();
            }
            poll();
        }
        return null;
    }

    public double millisUntilDue(double clockMillis) {
        return millisUntilDue(clockMillis, DECODE_AHEAD_MILLIS);
    }

    public double millisUntilDue(double clockMillis, long aheadMillis) {
        Unit head = units.peekFirst();
        if (head == null || awaitingKeyframe && !head.keyframe() || Double.isNaN(clockMillis)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, head.ptsMillis() - aheadMillis - clockMillis);
    }

    public void keepFromLatestKeyframe(double clockMillis) {
        if (Double.isNaN(clockMillis)) return;
        int newest = newestKeyframePastHead(clockMillis);
        if (newest > 0) {
            for (int i = 0; i < newest; i++) poll();
        } else if (!units.isEmpty() && !units.peekFirst().keyframe()) {
            awaitKeyframe();
        }
    }

    public void awaitKeyframe() {
        awaitingKeyframe = true;
        dropUntilKeyframe();
    }

    public void clear() {
        units.clear();
        bytes = 0;
        awaitingKeyframe = true;
    }

    public boolean isEmpty() {
        return units.isEmpty();
    }

    public int size() {
        return units.size();
    }

    public long skips() {
        return skips;
    }

    private Unit poll() {
        Unit unit = units.pollFirst();
        if (unit != null) bytes -= unit.data().length;
        return unit;
    }

    private void dropUntilKeyframe() {
        if (!awaitingKeyframe) return;
        while (!units.isEmpty() && !units.peekFirst().keyframe()) poll();
        if (!units.isEmpty()) awaitingKeyframe = false;
    }

    private boolean skipTo(double clockMillis) {
        int target = newestKeyframePastHead(clockMillis);
        if (target < 0) return false;
        for (int i = 0; i < target; i++) poll();
        skips++;
        return true;
    }

    private void shed() {
        int newest = newestKeyframePastHead(Double.POSITIVE_INFINITY);
        if (newest < 0) {
            clear();
            return;
        }
        for (int i = 0; i < newest; i++) poll();
    }

    private int newestKeyframePastHead(double atOrBeforeMillis) {
        int newest = -1;
        int index = 0;
        for (Unit unit : units) {
            if (index > 0 && unit.keyframe() && unit.ptsMillis() <= atOrBeforeMillis) newest = index;
            index++;
        }
        return newest;
    }
}
