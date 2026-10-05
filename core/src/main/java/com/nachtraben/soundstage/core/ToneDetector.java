package com.nachtraben.soundstage.core;

public final class ToneDetector {

    private ToneDetector() {}

    public static double power(short[] samples, float sampleRate, double frequencyHz) {
        if (samples == null || samples.length == 0 || sampleRate <= 0) return 0.0;

        double omega = 2.0 * Math.PI * frequencyHz / sampleRate;
        double coefficient = 2.0 * Math.cos(omega);

        double s1 = 0.0;
        double s2 = 0.0;
        for (short sample : samples) {
            double s0 = (sample / (double) Short.MAX_VALUE) + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }

        double magnitudeSquared = s1 * s1 + s2 * s2 - coefficient * s1 * s2;
        return Math.sqrt(Math.max(0.0, magnitudeSquared)) / samples.length;
    }

    public static double dominantFrequency(short[] samples, float sampleRate, double... candidatesHz) {
        if (candidatesHz == null || candidatesHz.length == 0) return 0.0;

        double best = 0.0;
        double bestPower = -1.0;
        for (double candidate : candidatesHz) {
            double p = power(samples, sampleRate, candidate);
            if (p > bestPower) {
                bestPower = p;
                best = candidate;
            }
        }
        return best;
    }

    public static short[] monoFromStereo16(byte[] interleaved) {
        if (interleaved == null || interleaved.length < 4) return new short[0];

        int frames = interleaved.length / 4;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int base = i * 4;
            short left = (short) ((interleaved[base] & 0xFF) | (interleaved[base + 1] << 8));
            short right = (short) ((interleaved[base + 2] & 0xFF) | (interleaved[base + 3] << 8));
            mono[i] = (short) ((left + right) / 2);
        }
        return mono;
    }
}
