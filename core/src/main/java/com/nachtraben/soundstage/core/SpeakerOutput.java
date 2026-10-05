package com.nachtraben.soundstage.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public final class SpeakerOutput {

    private static final int BYTES_PER_STEREO_SAMPLE = 4;

    private SpeakerOutput() {
    }

    public static ByteBuffer render(byte[] stereoPcm, ChannelMode mode, AudioDSP dsp) {
        return render(stereoPcm, mode, dsp, 1.0f);
    }

    public static ByteBuffer render(byte[] stereoPcm, ChannelMode mode, AudioDSP dsp, float gain) {
        return render(stereoPcm, null, SurroundDownmix.Layout.NONE, mode, dsp, gain);
    }

    public static ByteBuffer render(byte[] stereoPcm, short[] surround, SurroundDownmix.Layout layout,
                                    ChannelMode mode, AudioDSP dsp, float gain) {
        int samples = stereoPcm.length / BYTES_PER_STEREO_SAMPLE;
        ShortBuffer stereo = ByteBuffer.wrap(stereoPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

        ByteBuffer mono = ByteBuffer.allocate(samples * 2);
        mono.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer monoShorts = mono.asShortBuffer();

        if (surround != null && surround.length >= samples * SurroundDownmix.CHANNELS) {
            for (int i = 0; i < samples; i++) {
                monoShorts.put(i, SurroundDownmix.sample(surround, i * SurroundDownmix.CHANNELS, mode, layout));
            }
        } else {
            for (int i = 0; i < samples; i++) {
                monoShorts.put(i, ChannelDownmix.sample(
                        stereo.get(i * 2), stereo.get(i * 2 + 1), mode));
            }
        }

        dsp.process(mono);

        float safeGain = Float.isFinite(gain) ? Math.max(0.0f, Math.min(1.0f, gain)) : 1.0f;
        if (safeGain != 1.0f) {
            ShortBuffer output = mono.asShortBuffer();
            for (int i = 0; i < output.capacity(); i++) {
                output.put(i, (short) Math.round(output.get(i) * safeGain));
            }
        }
        mono.position(0);
        return mono;
    }
}
