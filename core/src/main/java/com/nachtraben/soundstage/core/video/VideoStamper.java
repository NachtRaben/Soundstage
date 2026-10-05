package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;

public final class VideoStamper implements TsDemuxer.Listener {

    public static final int MAX_HELD = 120;

    public static final long AAC_DECODER_DELAY_MILLIS = 15;

    private static final long WRAP = 1L << 33;

    static final long ADTS_FRAME_TICKS = 1024L * 90_000 / 48_000;

    public interface Output {
        void stamped(long ptsMillis, VideoAccessUnit unit);
    }

    private final long startMillis;
    private final Output output;
    private final ArrayDeque<VideoAccessUnit> held = new ArrayDeque<>();
    private int dropped;

    private boolean anchored;
    private long anchorPts;
    private boolean measuring;
    private long lastPts;
    private long lastTicks;
    private long lastAudioPts;
    private long lastAudioTicks;
    private long audioEndTicks;

    public VideoStamper(long startMillis, Output output) {
        this.startMillis = startMillis;
        this.output = output;
    }

    @Override
    public void onVideo(VideoAccessUnit unit) {
        if (!anchored) {
            if (held.size() == MAX_HELD) {
                held.poll();
                dropped++;
            }
            held.add(unit);
            return;
        }
        emit(unit);
    }

    @Override
    public void onAudio(long pts90k, int adtsFrameCount) {
        long ticks = anchored ? lastAudioTicks + signed33(pts90k - lastAudioPts) : 0;
        lastAudioPts = pts90k;
        lastAudioTicks = ticks;
        audioEndTicks = Math.max(audioEndTicks, ticks + adtsFrameCount * ADTS_FRAME_TICKS);
        if (anchored) return;
        anchored = true;
        anchorPts = pts90k;
        while (!held.isEmpty()) {
            emit(held.poll());
        }
    }

    public int dropped() {
        return dropped;
    }

    public boolean heardAudio() {
        return anchored;
    }

    public long audioMillis() {
        return Math.floorDiv(audioEndTicks, 90);
    }

    private void emit(VideoAccessUnit unit) {
        long pts = unit.pts90k();
        lastTicks = measuring ? lastTicks + signed33(pts - lastPts) : signed33(pts - anchorPts);
        lastPts = pts;
        measuring = true;
        output.stamped(startMillis + AAC_DECODER_DELAY_MILLIS + Math.floorDiv(lastTicks, 90), unit);
    }

    private static long signed33(long delta) {
        long d = delta & (WRAP - 1);
        return d >= WRAP / 2 ? d - WRAP : d;
    }
}
