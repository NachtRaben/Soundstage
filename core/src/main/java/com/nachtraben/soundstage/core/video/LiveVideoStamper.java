package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;

public final class LiveVideoStamper implements TsDemuxer.Listener {

    public static final long MAX_SKEW_MILLIS = 1_000;

    public static final long DISCONTINUITY_MILLIS = 200;

    static final int MAX_ANCHORS = 256;

    static final int DEFAULT_SAMPLE_RATE = 48_000;

    private static final long WRAP = 1L << 33;
    private static final long MAX_SKEW_TICKS = MAX_SKEW_MILLIS * 90;
    private static final long DISCONTINUITY_TICKS = DISCONTINUITY_MILLIS * 90;
    private static final int SAMPLES_PER_FRAME = 1024;

    public interface Output {
        void restart(long startMillis);

        void stamped(long ptsMillis, VideoAccessUnit unit);
    }

    private record Anchor(long pts, long framesBefore) {}

    private final Output output;
    private final ArrayDeque<Anchor> anchors = new ArrayDeque<>();
    private final ArrayDeque<VideoAccessUnit> held = new ArrayDeque<>();

    private int configuration = -1;
    private int sampleRate = DEFAULT_SAMPLE_RATE;
    private long frames;
    private long expectedPts;
    private boolean heardAudio;
    private boolean awaitingKeyframe = true;

    private int discontinuities;
    private int restarts;
    private int dropped;

    public LiveVideoStamper(Output output) {
        this.output = output;
    }

    @Override
    public void onAudio(long pts90k, int adtsFrameCount) {
        onAudio(pts90k, adtsFrameCount, 0, -1);
    }

    @Override
    public void onAudio(long pts90k, int adtsFrameCount, int rate, int format) {
        if (format != -1 && configuration != -1 && format != configuration) {
            restarts++;
            frames = 0;
            anchors.clear();
            awaitingKeyframe = true;
            output.restart(0);
        } else if (heardAudio && Math.abs(signed33(pts90k - expectedPts)) > DISCONTINUITY_TICKS) {
            discontinuities++;
            anchors.clear();
            awaitingKeyframe = true;
        }
        if (format != -1) configuration = format;
        if (rate > 0) sampleRate = rate;
        heardAudio = true;

        anchors.addLast(new Anchor(pts90k, frames));
        if (anchors.size() > MAX_ANCHORS) anchors.removeFirst();
        frames += adtsFrameCount;
        expectedPts = (pts90k + framesToTicks(adtsFrameCount)) & (WRAP - 1);

        while (!held.isEmpty()) {
            VideoAccessUnit unit = held.poll();
            Anchor anchor = anchorFor(unit.pts90k());
            if (anchor == null) {
                drop();
            } else {
                place(unit, anchor);
            }
        }
    }

    @Override
    public void onVideo(VideoAccessUnit unit) {
        Anchor anchor = anchors.isEmpty() ? null : anchorFor(unit.pts90k());
        if (anchor == null) {
            if (held.size() == VideoStamper.MAX_HELD) {
                held.poll();
                drop();
            }
            held.add(unit);
            return;
        }
        while (!held.isEmpty()) {
            held.poll();
            drop();
        }
        place(unit, anchor);
    }

    public boolean heardAudio() {
        return heardAudio;
    }

    public int discontinuities() {
        return discontinuities;
    }

    public int restarts() {
        return restarts;
    }

    public int dropped() {
        return dropped;
    }

    private Anchor anchorFor(long pts) {
        Anchor latest = anchors.peekLast();
        if (latest == null || Math.abs(signed33(pts - latest.pts())) > MAX_SKEW_TICKS) return null;
        var descending = anchors.descendingIterator();
        Anchor earliest = latest;
        while (descending.hasNext()) {
            Anchor anchor = descending.next();
            if (signed33(pts - anchor.pts()) >= 0) return anchor;
            earliest = anchor;
        }
        return earliest;
    }

    private void place(VideoAccessUnit unit, Anchor anchor) {
        if (awaitingKeyframe) {
            if (!unit.keyframe()) {
                drop();
                return;
            }
            awaitingKeyframe = false;
        }
        long ticks = framesToTicks(anchor.framesBefore()) + signed33(unit.pts90k() - anchor.pts());
        output.stamped(VideoStamper.AAC_DECODER_DELAY_MILLIS + Math.floorDiv(ticks, 90), unit);
    }

    private void drop() {
        dropped++;
        awaitingKeyframe = true;
    }

    private long framesToTicks(long count) {
        return count * SAMPLES_PER_FRAME * 90_000L / sampleRate;
    }

    private static long signed33(long delta) {
        long d = delta & (WRAP - 1);
        return d >= WRAP / 2 ? d - WRAP : d;
    }
}
