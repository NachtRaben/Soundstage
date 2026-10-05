package com.nachtraben.soundstage.core;

import java.nio.ShortBuffer;

public class ConeExcursionModel {

    public static final int TRACE_SAMPLES_PER_FRAME = 16;

    private final double fullTravelReference;

    private final double b0, b1, b2, a1, a2;
    private double z1 = 0.0, z2 = 0.0;

    public ConeExcursionModel(float sampleRate, double resonanceHz, double q, double fullTravelReference) {
        this.fullTravelReference = fullTravelReference;

        double w0 = 2.0 * Math.PI * resonanceHz / sampleRate;
        double alpha = Math.sin(w0) / (2.0 * q);
        double cosW0 = Math.cos(w0);

        double a0 = 1.0 + alpha;
        this.b0 = ((1.0 - cosW0) / 2.0) / a0;
        this.b1 = (1.0 - cosW0) / a0;
        this.b2 = ((1.0 - cosW0) / 2.0) / a0;
        this.a1 = (-2.0 * cosW0) / a0;
        this.a2 = (1.0 - alpha) / a0;
    }

    public float[] trace(ShortBuffer mono) {
        int count = mono.capacity();
        if (count <= 0) {
            return new float[0];
        }

        float[] trace = new float[TRACE_SAMPLES_PER_FRAME];
        int stride = Math.max(1, count / TRACE_SAMPLES_PER_FRAME);
        int written = 0;

        for (int i = 0; i < count; i++) {
            double in = mono.get(i) / 32768.0;

            double out = in * b0 + z1;
            z1 = in * b1 + z2 - a1 * out;
            z2 = in * b2 - a2 * out;

            if (i % stride == 0 && written < TRACE_SAMPLES_PER_FRAME) {
                trace[written++] = (float) (out / fullTravelReference);
            }
        }

        float last = written > 0 ? trace[written - 1] : 0.0f;
        while (written < TRACE_SAMPLES_PER_FRAME) {
            trace[written++] = last;
        }
        return trace;
    }
}
