package com.nachtraben.soundstage.core;

public final class WooferKinematics {
    private WooferKinematics() {}

    public static final float MAX_TRAVEL = 0.050f;
    public static final float FLANGE_OUTER_HALF = 0.482f;
    public static final float FRAME_OPENING_HALF = 0.430f;
    public static final float SURROUND_OUTER_HALF = 0.438f;
    public static final float CONE_THROAT_RADIUS = 0.175f;
    public static final float CONE_OUTER_RADIUS = 0.345f;
    public static final float DIAPHRAGM_THICKNESS = 0.008f;
    public static final float SPIDER_FOLD_AMPLITUDE = 0.017f;
    public static final float SPIDER_FOLDS = 7.0f;
    public static final float FORMER_RADIUS = 0.125f;
    public static final float COIL_RADIUS = 0.138f;
    public static final float TOP_PLATE_GAP_RADIUS = 0.150f;
    public static final float POLE_RADIUS = 0.112f;
    public static final float SPIDER_INNER_RADIUS = 0.133f;
    public static final float SPIDER_COLLAR_OUTER_RADIUS = 0.145f;
    public static final float SPIDER_OUTER_RADIUS = 0.415f;
    public static final float SPIDER_LANDING_INNER_RADIUS = 0.402f;
    public static final float MAGNET_RADIUS = 0.345f;
    public static final float PLATE_RADIUS = 0.360f;
    public static final float TOP_PLATE_FRONT_OFFSET = -0.025f;
    public static final float TOP_PLATE_BACK_OFFSET = 0.030f;

    public record Pose(
            float displacement,
            float frameZ,
            float surroundOuterZ,
            float surroundInnerZ,
            float coneOuterZ,
            float coneInnerZ,
            float dustCenterZ,
            float formerFrontZ,
            float formerBackZ,
            float coilFrontZ,
            float coilBackZ,
            float spiderOuterZ,
            float spiderInnerZ,
            float magnetFrontZ,
            float magnetBackZ) {
    }

    public static float coneBackZ(Pose pose, float radius) {
        float t = (CONE_OUTER_RADIUS - radius) / (CONE_OUTER_RADIUS - CONE_THROAT_RADIUS);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return pose.coneOuterZ() + (pose.coneInnerZ() - pose.coneOuterZ()) * t + DIAPHRAGM_THICKNESS;
    }

    public static float spiderFrontZ(Pose pose, float radius) {
        float t = (radius - SPIDER_INNER_RADIUS) / (SPIDER_OUTER_RADIUS - SPIDER_INNER_RADIUS);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return pose.spiderInnerZ() + (pose.spiderOuterZ() - pose.spiderInnerZ()) * t - SPIDER_FOLD_AMPLITUDE;
    }

    public static Pose atExcursion(float excursion) {
        float clamped = Math.max(-1.0f, Math.min(1.0f, excursion));
        float moving = -clamped * MAX_TRAVEL;
        return new Pose(
                moving,
                -0.405f,
                -0.400f,
                -0.425f + moving,
                -0.425f + moving,
                -0.300f + moving,
                -0.380f + moving,
                -0.290f + moving,
                -0.050f + moving,
                -0.140f + moving,
                -0.060f + moving,
                -0.200f,
                -0.205f + moving,
                -0.095f,
                 0.205f);
    }
}
