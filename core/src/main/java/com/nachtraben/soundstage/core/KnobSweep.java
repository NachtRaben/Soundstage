package com.nachtraben.soundstage.core;

public final class KnobSweep {
    private KnobSweep() {}

    public static final float SWEEP_DEGREES = 270f;

    public static float clockwiseDegrees(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return (clamped - 0.5f) * SWEEP_DEGREES;
    }

    public static float[] direction(float value) {
        double radians = Math.toRadians(clockwiseDegrees(value));
        return new float[]{(float) Math.sin(radians), (float) Math.cos(radians)};
    }
}
