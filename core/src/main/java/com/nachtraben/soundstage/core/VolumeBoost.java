package com.nachtraben.soundstage.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public final class VolumeBoost {

    private static final double FULL_SCALE = 32767.0;

    private VolumeBoost() {
    }

    public static double extraGainFor(int receiverVolume) {
        if (receiverVolume <= 100) return 1.0;
        return receiverVolume / 100.0;
    }

    public static int lavaPlayerMultiplier(int volume) {
        return (int) ((float) Math.tan(volume * 0.0079f) * 10000);
    }

    public static double lavaPlayerGain(int receiverVolume) {
        if (receiverVolume >= 100) return 1.0;
        if (receiverVolume <= 0) return 0.0;
        return lavaPlayerMultiplier(receiverVolume) / 10000.0;
    }

    public static void apply(byte[] bigEndianStereoPcm, double[] gains) {
        ShortBuffer shorts = ByteBuffer.wrap(bigEndianStereoPcm).order(ByteOrder.BIG_ENDIAN).asShortBuffer();
        int pairs = Math.min(shorts.limit() / 2, gains.length);
        for (int i = 0; i < pairs; i++) {
            shorts.put(i * 2, scaled(shorts.get(i * 2), gains[i]));
            shorts.put(i * 2 + 1, scaled(shorts.get(i * 2 + 1), gains[i]));
        }
    }

    public static short scaled(int sample, double gain) {
        long out = Math.round(sample * gain);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, out));
    }

    public static final class Limiter {
        public static final double CEILING = 0.95;
        static final int HOLD_SAMPLES = 48_000 * 80 / 1000;
        static final double RELEASE_SECONDS = 0.4;

        private final double releasePerSample = Math.exp(-1.0 / (48_000 * RELEASE_SECONDS));
        private double envelope;
        private int held;
        private double[] gains = new double[0];

        public void reset() {
            envelope = 0;
            held = 0;
        }

        public double[] gains(byte[] bigEndianStereo, short[] six, double boost) {
            return gains(bigEndianStereo, six, boost, null);
        }

        public double[] gains(byte[] bigEndianStereo, short[] six, double boost, double[] preGains) {
            ShortBuffer stereo = ByteBuffer.wrap(bigEndianStereo).order(ByteOrder.BIG_ENDIAN).asShortBuffer();
            int samples = stereo.limit() / 2;
            if (gains.length != samples) gains = new double[samples];
            for (int i = 0; i < samples; i++) {
                int peak = Math.max(Math.abs((int) stereo.get(i * 2)), Math.abs((int) stereo.get(i * 2 + 1)));
                if (six != null && six.length >= (i + 1) * 6) {
                    for (int c = 0; c < 6; c++) peak = Math.max(peak, Math.abs((int) six[i * 6 + c]));
                }
                double gain = preGains == null ? boost : boost * preGains[i];
                double level = peak / FULL_SCALE * gain;
                if (level >= envelope) {
                    envelope = level;
                    held = HOLD_SAMPLES;
                } else if (held > 0) {
                    held--;
                } else {
                    envelope = Math.max(level, envelope * releasePerSample);
                }
                gains[i] = envelope > CEILING ? gain * CEILING / envelope : gain;
            }
            return gains;
        }
    }
}
