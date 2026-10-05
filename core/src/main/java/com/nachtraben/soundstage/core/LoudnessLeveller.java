package com.nachtraben.soundstage.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public final class LoudnessLeveller {

    static final double THRESHOLD_DB = -20.0;
    static final double RATIO = 3.0;
    static final double MAKEUP_DB = 6.0;
    static final double ATTACK_SECONDS = 0.02;
    static final double RELEASE_SECONDS = 0.25;
    static final double RMS_SECONDS = 0.03;
    static final double SIDECHAIN_HZ = 150.0;

    private static final int RATE = 48_000;
    private static final double FULL_SCALE = 32767.0;
    private static final double DB_TO_NEPERS = Math.log(10) / 20;

    private final double attack = Math.exp(-1.0 / (RATE * ATTACK_SECONDS));
    private final double release = Math.exp(-1.0 / (RATE * RELEASE_SECONDS));
    private final double rms = Math.exp(-1.0 / (RATE * RMS_SECONDS));
    private final double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;
    private double meanSquare;
    private double gainDb;
    private double[] gains = new double[0];

    public LoudnessLeveller() {
        double w = 2 * Math.PI * SIDECHAIN_HZ / RATE;
        double cos = Math.cos(w), alpha = Math.sin(w) / Math.sqrt(2);
        double a0 = 1 + alpha;
        b0 = (1 + cos) / 2 / a0;
        b1 = -(1 + cos) / a0;
        b2 = (1 + cos) / 2 / a0;
        a1 = -2 * cos / a0;
        a2 = (1 - alpha) / a0;
    }

    public double[] gains(byte[] bigEndianStereo, double volumeGain) {
        ShortBuffer stereo = ByteBuffer.wrap(bigEndianStereo).order(ByteOrder.BIG_ENDIAN).asShortBuffer();
        int samples = stereo.limit() / 2;
        if (gains.length != samples) gains = new double[samples];
        double unscale = volumeGain > 0 ? 1.0 / volumeGain : 0.0;
        for (int i = 0; i < samples; i++) {
            double mono = (stereo.get(i * 2) + stereo.get(i * 2 + 1)) / (2 * FULL_SCALE) * unscale;
            double high = b0 * mono + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = mono;
            y2 = y1;
            y1 = high;
            meanSquare = high * high + (meanSquare - high * high) * rms;
            double target = targetGainDb(meanSquare);
            gainDb = target + (gainDb - target) * (target < gainDb ? attack : release);
            gains[i] = Math.exp(gainDb * DB_TO_NEPERS);
        }
        return gains;
    }

    public static double steadyGainDb(double rmsDb) {
        return targetGainDb(Math.pow(10, rmsDb / 10));
    }

    static double targetGainDb(double meanSquare) {
        double levelDb = 10 * Math.log10(Math.max(meanSquare, 1e-12));
        double over = Math.max(0.0, levelDb - THRESHOLD_DB);
        return MAKEUP_DB - over * (1 - 1 / RATIO);
    }
}
