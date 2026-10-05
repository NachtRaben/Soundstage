package com.nachtraben.soundstage.core;

public final class PlacementFacing {

    private PlacementFacing() {}

    public static final int ROTATION_STEPS = 16;

    public static final float VERTICAL_PITCH = 70.0f;

    public static float pitchToward(float placerXRot) {
        if (!(Math.abs(placerXRot) > VERTICAL_PITCH)) {
            return 0.0f;
        }
        return Math.signum(placerXRot) * 90.0f;
    }

    public record Aim(float pitch, float yawOffset) {
        public static final Aim LEVEL = new Aim(0.0f, 0.0f);
    }

    public static Aim aimAfterPlacement(float placerXRot, Aim carried) {
        float vertical = pitchToward(placerXRot);
        if (vertical != 0.0f || carried == null || Math.abs(carried.pitch()) >= 90.0f) {
            return new Aim(vertical, 0.0f);
        }
        return carried;
    }

    public static int rotationToward(float placerYawDegrees, int facings) {
        if (facings <= 0 || ROTATION_STEPS % facings != 0 || Integer.bitCount(facings) != 1) {
            throw new IllegalArgumentException("facings must be 1, 2, 4, 8 or 16: " + facings);
        }
        int step = (int) Math.floor(-placerYawDegrees * facings / 360.0 + 0.5) & (facings - 1);
        return step * (ROTATION_STEPS / facings);
    }
}
