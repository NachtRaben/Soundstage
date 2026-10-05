package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.natives.aac.AacDecoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public final class SurroundDecoder implements TsDemuxer.Listener, AutoCloseable {

    static final int MAX_EARLY_PES = 64;

    private static final int[] ADTS_SAMPLE_RATES = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};

    private final TsDemuxer.Listener delegate;
    private final SurroundBuffer buffer;
    private final int generation;
    private final long startSample;

    private AacDecoder decoder;
    private int configuredProfile = -1;
    private int configuredRate;
    private int configuredChannels;
    private ByteBuffer input;
    private ShortBuffer output;
    private short[] interleaved;

    private boolean mainAnchored;
    private long mainFirstPts;
    private boolean placed;
    private long nextSample;
    private final java.util.ArrayDeque<Object[]> early = new java.util.ArrayDeque<>();
    private boolean failed;

    public SurroundDecoder(TsDemuxer.Listener delegate, SurroundBuffer buffer, long startMillis) {
        this.delegate = delegate;
        this.buffer = buffer;
        this.generation = buffer.begin(startMillis);
        this.startSample = startMillis * (SurroundBuffer.SAMPLE_RATE / 1000);
    }

    @Override
    public void onVideo(VideoAccessUnit unit) {
        if (delegate != null) delegate.onVideo(unit);
    }

    @Override
    public void onAudio(long pts90k, int adtsFrameCount) {
        onAudio(pts90k, adtsFrameCount, 0, -1);
    }

    @Override
    public void onAudio(long pts90k, int adtsFrameCount, int sampleRate, int configuration) {
        if (delegate != null) delegate.onAudio(pts90k, adtsFrameCount, sampleRate, configuration);
        if (mainAnchored) return;
        mainAnchored = true;
        mainFirstPts = pts90k;
        while (!early.isEmpty()) {
            Object[] held = early.poll();
            decode((Long) held[0], (byte[]) held[1]);
        }
    }

    @Override
    public void onSecondAudio(long pts90k, byte[] adts) {
        if (failed) return;
        if (!mainAnchored) {
            if (early.size() == MAX_EARLY_PES) early.poll();
            early.add(new Object[]{pts90k, adts});
            return;
        }
        decode(pts90k, adts);
    }

    private void decode(long pts90k, byte[] adts) {
        if (!placed) {
            long ticks = signed33(pts90k - mainFirstPts);
            nextSample = startSample + Math.floorDiv(ticks * SurroundBuffer.SAMPLE_RATE, 90_000);
            placed = true;
        }
        try {
            int i = 0;
            while (i + 7 <= adts.length) {
                if ((adts[i] & 0xff) != 0xFF || (adts[i + 1] & 0xF0) != 0xF0) break;
                boolean protectionAbsent = (adts[i + 1] & 1) == 1;
                int profile = ((adts[i + 2] & 0xff) >> 6) + 1;
                int rateIndex = (adts[i + 2] >> 2) & 0x0f;
                int channels = ((adts[i + 2] & 1) << 2) | ((adts[i + 3] & 0xff) >> 6);
                int frameLength = ((adts[i + 3] & 3) << 11) | ((adts[i + 4] & 0xff) << 3) | ((adts[i + 5] & 0xff) >> 5);
                int header = protectionAbsent ? 7 : 9;
                if (frameLength < header || i + frameLength > adts.length || rateIndex >= ADTS_SAMPLE_RATES.length) break;
                configure(profile, ADTS_SAMPLE_RATES[rateIndex], channels);
                decodeFrame(adts, i + header, frameLength - header);
                i += frameLength;
            }
        } catch (RuntimeException e) {
            failed = true;
            buffer.clear();
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn(
                    "Soundstage: the 5.1 track could not be decoded ({}); this copy plays stereo.", e.toString());
        }
    }

    private void configure(int profile, int rate, int channels) {
        if (decoder != null && profile == configuredProfile && rate == configuredRate && channels == configuredChannels) {
            return;
        }
        if (decoder != null) decoder.close();
        decoder = new AacDecoder();
        decoder.configure(profile, rate, channels);
        configuredProfile = profile;
        configuredRate = rate;
        configuredChannels = channels;
        output = null;
    }

    private void decodeFrame(byte[] adts, int offset, int length) {
        if (input == null || input.capacity() < length) {
            input = ByteBuffer.allocateDirect(Math.max(length, 2048)).order(ByteOrder.nativeOrder());
        }
        input.clear();
        input.put(adts, offset, length);
        input.flip();
        decoder.fill(input);
        if (output == null) {
            AacDecoder.StreamInfo info = decoder.resolveStreamInfo();
            if (info == null) return;
            if (info.channels != SurroundBuffer.CHANNELS || info.sampleRate != SurroundBuffer.SAMPLE_RATE) {
                throw new IllegalStateException("sidecar is " + info.channels + " channels at " + info.sampleRate + " Hz");
            }
            output = ByteBuffer.allocateDirect(2 * info.frameSize * info.channels)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            interleaved = new short[info.frameSize * info.channels];
        }
        while (decoder.decode(output, false)) {
            output.clear();
            output.get(interleaved);
            int samples = interleaved.length / SurroundBuffer.CHANNELS;
            buffer.write(generation, nextSample, interleaved, samples);
            nextSample += samples;
            output.clear();
        }
    }

    @Override
    public void close() {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }

    private static long signed33(long delta) {
        long wrap = 1L << 33;
        long d = delta & (wrap - 1);
        return d >= wrap / 2 ? d - wrap : d;
    }
}
