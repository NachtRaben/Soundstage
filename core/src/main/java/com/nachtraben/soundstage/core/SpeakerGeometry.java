package com.nachtraben.soundstage.core;

public final class SpeakerGeometry {
    private SpeakerGeometry() {}

    public static final float MAIN_WOOFER_WIDTH_PX = 9.5f;
    public static final int MAIN_WOOFER_COUNT = 1;

    public static final float SATELLITE_WOOFER_WIDTH_PX = 5.2f;
    public static final int SATELLITE_WOOFER_COUNT = 1;

    public static final float CENTER_WOOFER_WIDTH_PX = 4.5f;
    public static final int CENTER_WOOFER_COUNT = 2;

    public static final float SUBWOOFER_WOOFER_WIDTH_PX = 13.5f;
    public static final int SUBWOOFER_WOOFER_COUNT = 1;

    public static final float PARTY_BOX_WOOFER_WIDTH_PX = 8.0f;
    public static final int PARTY_BOX_WOOFER_COUNT = 1;

    private static final float BLOCK_UNITS_PER_METRE = 16.0f;

    private static final double EFFECTIVE_RADIUS_FACTOR = 0.39;

    public static double apertureRadiusMetres(float widthPixels, int count) {
        double widthBlocks = widthPixels / BLOCK_UNITS_PER_METRE;
        return EFFECTIVE_RADIUS_FACTOR * widthBlocks * Math.sqrt(count);
    }
}
