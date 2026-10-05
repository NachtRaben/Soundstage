package com.nachtraben.soundstage.core;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

public final class HandedFrameMap {

    public static final int FRAME_SAMPLES = 960;

    static final int HISTORY_FRAMES = PcmRing.MAX_OUTPUT_LATENCY_FRAMES + 3;

    static final int MAX_STRETCHES = 128;

    private static final long NO_CONTENT = Long.MIN_VALUE;

    private static final class Buffer {
        final int samples;
        final int frames;
        final long[] group;
        final long[] content;
        final boolean[] advances;

        Buffer(int samples, int frames, long[] group, long[] content, boolean[] advances) {
            this.samples = samples;
            this.frames = frames;
            this.group = group;
            this.content = content;
            this.advances = advances;
        }
    }

    private final ArrayDeque<Buffer> queued = new ArrayDeque<>();

    private int pendingCount;
    private long[] pendingGroup = new long[8];
    private long[] pendingContent = new long[8];
    private boolean[] pendingAdvances = new boolean[8];

    private boolean holding;
    private long heldContent;
    private long heldGroup;

    private final long[] stretchStart = new long[MAX_STRETCHES];
    private final double[] stretchDelay = new double[MAX_STRETCHES];
    private int stretchOldest;
    private int stretchCount;
    private HeardDelays heardDelays;

    private boolean anyHanded;
    private long handedEndGroup;

    private long retiredSamples;
    private long handedSamples;

    private double locatedGroupSample = Double.NaN;
    private double locatedContentSample = Double.NaN;
    private double locatedDelayFrames = Double.NaN;

    public void produceContent(long sequence, long groupFrame) {
        append(groupFrame, sequence, true);
    }

    public void hold(long sequence, long groupFrame) {
        holding = true;
        heldContent = sequence;
        heldGroup = groupFrame;
    }

    public void produceHeld() {
        if (!holding) return;
        holding = false;
        append(heldGroup, heldContent, true);
    }

    public void produceFiller(long groupFrameIfNothingHeld, boolean advances) {
        if (holding) {
            append(heldGroup, NO_CONTENT, advances);
            if (advances) heldGroup++;
        } else {
            append(groupFrameIfNothingHeld, NO_CONTENT, advances);
        }
    }

    public boolean handed(int samples) {
        int frames = pendingCount;
        queued.addLast(new Buffer(samples, frames,
                Arrays.copyOf(pendingGroup, frames), Arrays.copyOf(pendingContent, frames),
                Arrays.copyOf(pendingAdvances, frames)));
        handedSamples += samples;
        if (frames > 0) {
            int last = frames - 1;
            anyHanded = true;
            handedEndGroup = pendingGroup[last] + (pendingAdvances[last] ? 1 : 0);
        }
        pendingCount = 0;
        return (long) frames * FRAME_SAMPLES == samples;
    }

    public boolean retired(int count) {
        for (int i = 0; i < count; i++) {
            Buffer buffer = queued.pollFirst();
            if (buffer == null) return false;
            retiredSamples += buffer.samples;
        }
        return true;
    }

    public int queuedBuffers() {
        return queued.size();
    }

    public long samplesBeyondHead() {
        long total = 0;
        Iterator<Buffer> it = queued.iterator();
        if (it.hasNext()) it.next();
        while (it.hasNext()) total += it.next().samples;
        return total;
    }

    public long retiredSamples() {
        return retiredSamples;
    }

    public long handedSamples() {
        return handedSamples;
    }

    public double handedEndSample() {
        return anyHanded ? handedEndGroup * (double) FRAME_SAMPLES : Double.NaN;
    }

    public boolean locate(double queueOffset) {
        if (!anyHanded) {
            clearLocated();
            return false;
        }
        double remaining = Math.max(0.0, queueOffset);
        for (Buffer buffer : queued) {
            if (remaining < buffer.samples && buffer.frames > 0) {
                int frame = Math.min(buffer.frames - 1, (int) (remaining / FRAME_SAMPLES));
                double within = remaining - (double) frame * FRAME_SAMPLES;
                long group = buffer.group[frame];
                long content = buffer.content[frame];
                locatedGroupSample = group * (double) FRAME_SAMPLES + (buffer.advances[frame] ? within : 0.0);
                locatedContentSample = content == NO_CONTENT
                        ? Double.NaN : content * (double) FRAME_SAMPLES + within;
                locatedDelayFrames = delayOf(group, content);
                forgetStretchesBefore(group - HISTORY_FRAMES);
                return true;
            }
            remaining -= buffer.samples;
        }
        locatedGroupSample = handedEndGroup * (double) FRAME_SAMPLES;
        locatedContentSample = Double.NaN;
        locatedDelayFrames = Double.NaN;
        forgetStretchesBefore(handedEndGroup - HISTORY_FRAMES);
        return true;
    }

    public double locatedGroupSample() {
        return locatedGroupSample;
    }

    public double locatedContentSample() {
        return locatedContentSample;
    }

    public double locatedDelayFrames() {
        return locatedDelayFrames;
    }

    public HeardDelays heardDelays() {
        if (heardDelays == null && stretchCount > 0) {
            double[] from = new double[stretchCount];
            double[] delay = new double[stretchCount];
            for (int i = 0; i < stretchCount; i++) {
                int at = (stretchOldest + i) % MAX_STRETCHES;
                from[i] = stretchStart[at] * (double) FRAME_SAMPLES;
                delay[i] = stretchDelay[at];
            }
            heardDelays = new HeardDelays(from, delay);
        }
        return heardDelays;
    }

    public double queueOffsetOf(double groupSample) {
        double offset = 0.0;
        boolean first = true;
        for (Buffer buffer : queued) {
            for (int frame = 0; frame < buffer.frames; frame++) {
                if (buffer.advances[frame]) {
                    double start = buffer.group[frame] * (double) FRAME_SAMPLES;
                    if (first && groupSample < start) return Double.NaN;
                    first = false;
                    if (groupSample < start + FRAME_SAMPLES) {
                        return offset + Math.max(0.0, groupSample - start);
                    }
                }
                offset += FRAME_SAMPLES;
            }
            offset += buffer.samples - (double) buffer.frames * FRAME_SAMPLES;
        }
        return Double.NaN;
    }

    public double legacyGroupSampleAt(double queueOffset, long firstSequence, int realisedDelayFrames) {
        return firstSequence * (double) FRAME_SAMPLES + retiredSamples + queueOffset
                + realisedDelayFrames * (double) FRAME_SAMPLES;
    }

    public double legacyHandedEndSample(long firstSequence, int realisedDelayFrames) {
        return firstSequence * (double) FRAME_SAMPLES + handedSamples
                + realisedDelayFrames * (double) FRAME_SAMPLES;
    }

    public double legacyQueueOffsetOf(double groupSample, long firstSequence, int realisedDelayFrames) {
        return groupSample - realisedDelayFrames * (double) FRAME_SAMPLES
                - firstSequence * (double) FRAME_SAMPLES;
    }

    private void append(long group, long content, boolean advances) {
        if (pendingCount == pendingGroup.length) {
            int size = pendingCount * 2;
            pendingGroup = Arrays.copyOf(pendingGroup, size);
            pendingContent = Arrays.copyOf(pendingContent, size);
            pendingAdvances = Arrays.copyOf(pendingAdvances, size);
        }
        double delay = delayOf(group, content);
        int newest = (stretchOldest + stretchCount - 1) % MAX_STRETCHES;
        if (stretchCount == 0 || !sameDelay(delay, stretchDelay[newest])) {
            if (stretchCount > 0 && group < stretchStart[newest]) {
                stretchOldest = 0;
                stretchCount = 0;
            }
            if (stretchCount == MAX_STRETCHES) {
                stretchOldest = (stretchOldest + 1) % MAX_STRETCHES;
                stretchCount--;
            }
            int at = (stretchOldest + stretchCount) % MAX_STRETCHES;
            stretchStart[at] = group;
            stretchDelay[at] = delay;
            stretchCount++;
            heardDelays = null;
        }
        pendingGroup[pendingCount] = group;
        pendingContent[pendingCount] = content;
        pendingAdvances[pendingCount] = advances;
        pendingCount++;
    }

    private void forgetStretchesBefore(long groupFrame) {
        boolean dropped = false;
        while (stretchCount > 1 && stretchStart[(stretchOldest + 1) % MAX_STRETCHES] <= groupFrame) {
            stretchOldest = (stretchOldest + 1) % MAX_STRETCHES;
            stretchCount--;
            dropped = true;
        }
        if (dropped) heardDelays = null;
    }

    private void clearLocated() {
        locatedGroupSample = Double.NaN;
        locatedContentSample = Double.NaN;
        locatedDelayFrames = Double.NaN;
    }

    private static double delayOf(long group, long content) {
        return content == NO_CONTENT ? Double.NaN : (double) (group - content);
    }

    private static boolean sameDelay(double a, double b) {
        return Double.isNaN(a) ? Double.isNaN(b) : a == b;
    }
}
