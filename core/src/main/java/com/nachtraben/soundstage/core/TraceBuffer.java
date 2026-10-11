package com.nachtraben.soundstage.core;

import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentSkipListMap;

public final class TraceBuffer {

    private final ConcurrentSkipListMap<Long, float[]> frames = new ConcurrentSkipListMap<>();

    private volatile boolean reportedRenumber;
    private final int retainedFrames;

    public TraceBuffer(int retainedFrames) {
        this.retainedFrames = retainedFrames;
    }

    public void record(long sequence, float[] trace) {
        Long newest = frames.isEmpty() ? null : frames.lastKey();
        if (newest != null && sequence < newest - retainedFrames) {
            if (!reportedRenumber) {
                reportedRenumber = true;
                LoggerFactory.getLogger("Soundstage").debug(
                        "SOUNDSTAGE-TRACE-RENUMBER: incoming={} newest={} held={} resetting=true",
                        sequence, newest, frames.size());
            }
            frames.clear();
        } else {
            reportedRenumber = false;
        }
        frames.put(sequence, trace);
        while (frames.size() > retainedFrames) {
            frames.pollFirstEntry();
        }
    }

    public int size() { return frames.size(); }
    public long lowestSequence() { return frames.isEmpty() ? -1 : frames.firstKey(); }
    public long highestSequence() { return frames.isEmpty() ? -1 : frames.lastKey(); }

    public void clear() {
        frames.clear();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public float sample(double sequence) {
        if (sequence < 0) {
            return 0.0f;
        }

        long frame = (long) Math.floor(sequence);
        double withinFrame = (sequence - frame) * ConeExcursionModel.TRACE_SAMPLES_PER_FRAME;
        int index = (int) Math.floor(withinFrame);
        float blend = (float) (withinFrame - index);

        float current = at(frame, index);
        float next = index + 1 < ConeExcursionModel.TRACE_SAMPLES_PER_FRAME
                ? at(frame, index + 1)
                : at(frame + 1, 0);

        return current * (1.0f - blend) + next * blend;
    }

    public float peak(double sequence, int framesBack) {
        if (sequence < 0) {
            return 0.0f;
        }

        long end = (long) Math.floor(sequence);
        long start = Math.max(0L, end - framesBack);
        float peak = 0.0f;
        for (long frame = start; frame <= end; frame++) {
            float[] trace = frames.get(frame);
            if (trace == null) {
                continue;
            }
            for (float value : trace) {
                if (value > peak) {
                    peak = value;
                }
            }
        }
        return peak;
    }

    private float at(long frame, int index) {
        float[] trace = frames.get(frame);
        if (trace == null || index < 0 || index >= trace.length) {
            return 0.0f;
        }
        return trace[index];
    }
}
