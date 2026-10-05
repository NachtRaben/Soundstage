package com.nachtraben.soundstage.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioDSP {
    public enum FilterType {
        NONE, LOW_PASS, HIGH_PASS, BAND_PASS
    }

    private FilterType type;
    private final float sampleRate;

    private double a0, a1, a2, b0, b1, b2;
    private double z1 = 0.0, z2 = 0.0;

    private volatile float lastFrameRms = 0.0f;
    private volatile float lastFramePeak = 0.0f;

    public AudioDSP(FilterType type, float sampleRate, float frequency, float q) {
        this.type = type;
        this.sampleRate = sampleRate;
        if (type != FilterType.NONE) {
            calculateCoefficients(frequency, q);
        }
    }
    
    public synchronized void setType(FilterType newType, float frequency, float q) {
        this.type = newType;
        if (newType != FilterType.NONE) {
            calculateCoefficients(frequency, q);
        }
    }

    private synchronized void calculateCoefficients(float frequency, float q) {
        double w0 = 2 * Math.PI * frequency / sampleRate;
        double alpha = Math.sin(w0) / (2 * q);
        double cosW0 = Math.cos(w0);

        if (type == FilterType.LOW_PASS) {
            b0 = (1 - cosW0) / 2;
            b1 = 1 - cosW0;
            b2 = (1 - cosW0) / 2;
            a0 = 1 + alpha;
            a1 = -2 * cosW0;
            a2 = 1 - alpha;
        } else if (type == FilterType.HIGH_PASS) {
            b0 = (1 + cosW0) / 2;
            b1 = -(1 + cosW0);
            b2 = (1 + cosW0) / 2;
            a0 = 1 + alpha;
            a1 = -2 * cosW0;
            a2 = 1 - alpha;
        } else if (type == FilterType.BAND_PASS) {
            b0 = alpha;
            b1 = 0;
            b2 = -alpha;
            a0 = 1 + alpha;
            a1 = -2 * cosW0;
            a2 = 1 - alpha;
        }
        
        b0 /= a0;
        b1 /= a0;
        b2 /= a0;
        a1 /= a0;
        a2 /= a0;
    }

    public synchronized float process(ByteBuffer pcmData) {
        pcmData.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuffer = pcmData.asShortBuffer();

        int count = shortBuffer.capacity();
        float maxAmp = 0;
        double sumSquares = 0.0;

        for (int i = 0; i < count; i++) {
            double in = shortBuffer.get(i);

            double out = in;
            if (type != FilterType.NONE) {
                out = in * b0 + z1;
                z1 = in * b1 + z2 - a1 * out;
                z2 = in * b2 - a2 * out;

                if (out > 32767.0) out = 32767.0;
                else if (out < -32768.0) out = -32768.0;
            }

            double abs = Math.abs(out);
            if (abs > maxAmp) {
                maxAmp = (float) abs;
            }
            sumSquares += out * out;

            shortBuffer.put(i, (short) out);
        }

        lastFramePeak = maxAmp / 32768.0f;
        lastFrameRms = count > 0 ? (float) (Math.sqrt(sumSquares / count) / 32768.0) : 0.0f;
        return lastFrameRms;
    }

    public float getLastFrameRms() {
        return lastFrameRms;
    }

    public float getLastFramePeak() {
        return lastFramePeak;
    }
}
