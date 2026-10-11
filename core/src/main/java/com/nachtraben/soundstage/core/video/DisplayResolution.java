package com.nachtraben.soundstage.core.video;

public final class DisplayResolution {

    public static final int LINES_PER_BLOCK = 240;

    public static final int MIN_LINES = 480;

    public static final int UNKNOWN = -1;

    private DisplayResolution() {}

    public static int targetHeight(int tallestBlocks, int maxVideoHeight) {
        if (tallestBlocks < 0) return maxVideoHeight;
        long wanted = Math.max((long) tallestBlocks * LINES_PER_BLOCK, MIN_LINES);
        return (int) Math.min(wanted, maxVideoHeight);
    }
}
