package com.nachtraben.soundstage.core;

import java.util.Arrays;

public final class AudibleClock {

    public static final double NANOS_PER_FRAME = 20_000_000.0;

    public static final double SAMPLES_PER_FRAME = 960.0;

    private static final double SAMPLES_PER_NANO = SAMPLES_PER_FRAME / NANOS_PER_FRAME;

    public static final double SILENT = -1;

    public static final long PUMP_STALL_NANOS = 400_000_000L;

    public static final double SNAP_FRAMES = 5.0;

    public static final long MEASURED_SETTLE_NANOS = 250_000_000L;

    public static final long ESTIMATED_SETTLE_NANOS = 2_000_000_000L;

    public static final double DEFAULT_FILL_LEAD_FRAMES = 18.5;

    private static final double FILL_LEAD_LEARNING = 0.2;

    public enum Source { SILENT, MIXER, FILL, LINE }

    public record Reading(long pumped, boolean pumpIsFill, double measured, double measuredCap,
                          double outputLatencyFrames) {
    }

    private boolean anchored;
    private double anchorSequence;
    private long anchorNanos;
    private long lastReconcileNanos;
    private long lastPumped = Long.MIN_VALUE;
    private long lastPumpMoveNanos;
    private double fillLeadFrames = Double.NaN;
    private Source source = Source.SILENT;

    public double sample(long nowNanos, Reading reading) {
        long pumped = reading.pumped();
        if (pumped == -1) {
            reset();
            return SILENT;
        }
        boolean pumpMoved = pumped != lastPumped;
        if (pumpMoved) {
            lastPumped = pumped;
            lastPumpMoveNanos = nowNanos;
        } else if (nowNanos - lastPumpMoveNanos > PUMP_STALL_NANOS) {
            anchored = false;
            source = Source.SILENT;
            return SILENT;
        }

        Source previous = source;
        double target;
        double cap;
        long settleNanos;
        boolean reconcile;
        if (!Double.isNaN(reading.measured())) {
            source = Source.MIXER;
            target = reading.measured();
            cap = reading.measuredCap();
            settleNanos = MEASURED_SETTLE_NANOS;
            reconcile = true;
            if (pumpMoved && reading.pumpIsFill()) {
                double lead = pumped - target;
                fillLeadFrames = Double.isNaN(fillLeadFrames)
                        ? lead
                        : fillLeadFrames + (lead - fillLeadFrames) * FILL_LEAD_LEARNING;
            }
        } else if (reading.pumpIsFill()) {
            source = Source.FILL;
            double lead = Double.isNaN(fillLeadFrames)
                    ? DEFAULT_FILL_LEAD_FRAMES + reading.outputLatencyFrames()
                    : fillLeadFrames;
            target = pumped - lead;
            cap = pumped;
            settleNanos = ESTIMATED_SETTLE_NANOS;
            reconcile = pumpMoved;
        } else {
            source = Source.LINE;
            target = pumped - reading.outputLatencyFrames();
            cap = pumped;
            settleNanos = ESTIMATED_SETTLE_NANOS;
            reconcile = pumpMoved;
        }
        if (source != previous) {
            lastReconcileNanos = nowNanos;
        }

        if (!anchored) {
            anchor(target, nowNanos);
        } else if (reconcile) {
            double error = target - (anchorSequence + (nowNanos - anchorNanos) / NANOS_PER_FRAME);
            if (Math.abs(error) > SNAP_FRAMES) {
                anchor(target, nowNanos);
            } else {
                double elapsed = Math.max(0L, nowNanos - lastReconcileNanos);
                anchorSequence += error * (1.0 - Math.exp(-elapsed / settleNanos));
                lastReconcileNanos = nowNanos;
            }
        }

        double value = anchorSequence + (nowNanos - anchorNanos) / NANOS_PER_FRAME;
        if (value > cap) {
            anchor(cap, nowNanos);
            value = cap;
        }
        return value;
    }

    private void anchor(double sequence, long nowNanos) {
        anchored = true;
        anchorSequence = sequence;
        anchorNanos = nowNanos;
        lastReconcileNanos = nowNanos;
    }

    public Source source() {
        return source;
    }

    public double fillLeadFrames() {
        return fillLeadFrames;
    }

    public void reset() {
        anchored = false;
        lastPumped = Long.MIN_VALUE;
        source = Source.SILENT;
    }

    public static final class Positions {

        private double[] positions = new double[8];
        private int count;
        private double capSample = Double.NEGATIVE_INFINITY;

        public void clear() {
            count = 0;
            capSample = Double.NEGATIVE_INFINITY;
        }

        public void add(double groupSample, long heardAtNanos, double handedEndSample, long nowNanos) {
            double at = groupSample + (nowNanos - heardAtNanos) * SAMPLES_PER_NANO;
            if (count == positions.length) positions = Arrays.copyOf(positions, count * 2);
            positions[count++] = Math.min(at, handedEndSample);
            capSample = Math.max(capSample, handedEndSample);
        }

        public int count() {
            return count;
        }

        public double medianSequence() {
            if (count == 0) return Double.NaN;
            Arrays.sort(positions, 0, count);
            int middle = count / 2;
            double median = count % 2 == 0
                    ? (positions[middle - 1] + positions[middle]) / 2.0
                    : positions[middle];
            return median / SAMPLES_PER_FRAME;
        }

        public double capSequence() {
            return count == 0 ? Double.POSITIVE_INFINITY : capSample / SAMPLES_PER_FRAME;
        }
    }
}
