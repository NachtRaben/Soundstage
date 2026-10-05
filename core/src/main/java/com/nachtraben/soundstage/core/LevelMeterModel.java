package com.nachtraben.soundstage.core;

import java.nio.ShortBuffer;

public class LevelMeterModel {

    private static final double WINDOW_SECONDS = 0.005;

    private static final double ATTACK_SECONDS = 0.003;
    private static final double DECAY_SECONDS = 0.100;

    private static final double FLOOR_DB = -32.0;

    private final double windowCoeff;
    private final double attackCoeff;
    private final double decayCoeff;

    private double meanSquare = 0.0;
    private double envelope = 0.0;

    public LevelMeterModel(float sampleRate) {
        this.windowCoeff = poleFor(WINDOW_SECONDS, sampleRate);
        this.attackCoeff = poleFor(ATTACK_SECONDS, sampleRate);
        this.decayCoeff = poleFor(DECAY_SECONDS, sampleRate);
    }

    private static double poleFor(double tau, float sampleRate) {
        return Math.exp(-1.0 / Math.max(1.0, tau * sampleRate));
    }

    public float[] trace(ShortBuffer mono) {
        int count = mono.capacity();
        if (count <= 0) {
            return new float[0];
        }

        float[] trace = new float[ConeExcursionModel.TRACE_SAMPLES_PER_FRAME];
        int stride = Math.max(1, count / ConeExcursionModel.TRACE_SAMPLES_PER_FRAME);
        int written = 0;

        for (int i = 0; i < count; i++) {
            double in = mono.get(i) / 32768.0;
            double square = in * in;

            meanSquare = square + windowCoeff * (meanSquare - square);
            double rms = Math.sqrt(meanSquare);

            double coeff = rms > envelope ? attackCoeff : decayCoeff;
            envelope = rms + coeff * (envelope - rms);

            if (i % stride == 0 && written < ConeExcursionModel.TRACE_SAMPLES_PER_FRAME) {
                trace[written++] = display();
            }
        }

        float last = written > 0 ? trace[written - 1] : 0.0f;
        while (written < ConeExcursionModel.TRACE_SAMPLES_PER_FRAME) {
            trace[written++] = last;
        }
        return trace;
    }

    private float display() {
        if (envelope <= 1.0e-6) {
            return 0.0f;
        }

        double db = 20.0 * Math.log10(envelope);
        double scaled = (db - FLOOR_DB) / -FLOOR_DB;

        if (scaled <= 0.0) {
            return 0.0f;
        }
        return scaled >= 1.0 ? 1.0f : (float) scaled;
    }
}
