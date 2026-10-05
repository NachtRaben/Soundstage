package com.nachtraben.soundstage.core;

public final class TubeThermalModel {

    public static final float FILAMENT_GLOW = 0.12f;

    public static final float HEAT_TAU_SECONDS = 1.6f;
    public static final float COOL_TAU_SECONDS = 4.5f;

    public static final float REDPLATE_THRESHOLD = 0.82f;

    private volatile float plateTemperature = 0f;

    public static float drive(float leftLevel, float rightLevel, int speakerCount) {
        float power = (leftLevel * leftLevel + rightLevel * rightLevel) * 0.5f;
        float load = 1f - (float) Math.exp(-Math.max(0, speakerCount) / 3.0f);
        return Math.min(1f, power * (0.55f + 0.75f * load));
    }

    public void advance(float drive, float deltaSeconds) {
        if (deltaSeconds <= 0f) return;
        float clamped = Math.max(0f, Math.min(1f, drive));
        float tau = clamped > plateTemperature ? HEAT_TAU_SECONDS : COOL_TAU_SECONDS;
        float alpha = 1f - (float) Math.exp(-deltaSeconds / tau);
        plateTemperature += (clamped - plateTemperature) * alpha;
    }

    public float plateTemperature() {
        return plateTemperature;
    }

    public float glow() {
        return FILAMENT_GLOW + (1f - FILAMENT_GLOW) * plateTemperature;
    }

    public float redness() {
        if (plateTemperature <= REDPLATE_THRESHOLD) return 0f;
        return (plateTemperature - REDPLATE_THRESHOLD) / (1f - REDPLATE_THRESHOLD);
    }

    public void reset() {
        plateTemperature = 0f;
    }
}
