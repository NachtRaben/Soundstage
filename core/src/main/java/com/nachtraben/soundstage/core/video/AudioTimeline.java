package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class AudioTimeline {

    public static final double FRAME_MILLIS = 20.0;

    public static final double CODEC_DELAY_MILLIS = com.nachtraben.soundstage.core.OpusCodec.LOOKAHEAD_SAMPLES
            * 1000.0 / com.nachtraben.soundstage.core.OpusCodec.SAMPLE_RATE;

    static final long BREAK_TOLERANCE_MILLIS = 30;

    static final long RETAINED_FRAMES = 500;

    private record Segment(long startSequence, long startMillis) {
        double millisAt(double sequence) {
            return startMillis + (sequence - startSequence) * FRAME_MILLIS;
        }
    }

    private final ArrayDeque<Segment> segments = new ArrayDeque<>();
    private long lastSequence = Long.MIN_VALUE;

    public synchronized void record(long sequence, long timecodeMillis) {
        if (timecodeMillis < 0) return;
        if (lastSequence != Long.MIN_VALUE) {
            if (sequence == lastSequence) return;
            if (sequence < lastSequence) segments.clear();
        }
        Segment last = segments.peekLast();
        if (last == null || Math.abs(last.millisAt(sequence) - timecodeMillis) > BREAK_TOLERANCE_MILLIS) {
            segments.addLast(new Segment(sequence, timecodeMillis));
        }
        lastSequence = sequence;
        while (segments.size() > 1) {
            Iterator<Segment> it = segments.iterator();
            it.next();
            if (it.next().startSequence() > sequence - RETAINED_FRAMES) break;
            segments.removeFirst();
        }
    }

    public synchronized double trackMillisAt(double sequence) {
        Iterator<Segment> it = segments.descendingIterator();
        while (it.hasNext()) {
            Segment segment = it.next();
            if (segment.startSequence() <= sequence) return segment.millisAt(sequence) - CODEC_DELAY_MILLIS;
        }
        return Double.NaN;
    }

    public synchronized void clear() {
        segments.clear();
        lastSequence = Long.MIN_VALUE;
    }
}
