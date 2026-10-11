package com.nachtraben.soundstage.core.video;

import java.util.List;

public final class PictureSize {

    static final int[] STEPS = {1080, 720, 480};

    static final double MOST_OF_A_STEP = 0.9;

    private PictureSize() {}

    public record Display(int widthBlocks, int heightBlocks, boolean fill) {}

    public record Copy(int width, int height, int kbps) {
        public long pixels() {
            return (long) width * height;
        }

        public int equivalentHeight() {
            return PictureSize.equivalentHeight(width, height);
        }
    }

    public record Target(List<Display> displays, boolean known) {
        public static final Target UNKNOWN = new Target(List.of(), false);

        public Target {
            displays = List.copyOf(displays);
        }

        public static Target of(List<Display> displays) {
            return new Target(displays, true);
        }

        public int tallestBlocks() {
            if (!known) return DisplayResolution.UNKNOWN;
            int tallest = 0;
            for (Display display : displays) tallest = Math.max(tallest, display.heightBlocks());
            return tallest;
        }

        public int formatHeight(int capHeight) {
            int tallest = tallestBlocks();
            return DisplayResolution.targetHeight(tallest, capHeight);
        }

        public Copy copy(int width, int height, int capHeight) {
            if (width <= 0 || height <= 0) {
                height = capHeight;
                width = sixteenByNineWidth(capHeight);
            }
            double scale = Double.POSITIVE_INFINITY;
            if (known) {
                scale = 0;
                if (displays.isEmpty()) {
                    double floor = DisplayResolution.MIN_LINES;
                    scale = Math.min(floor * 16 / 9 / width, floor / height);
                } else {
                    for (Display display : displays) scale = Math.max(scale, scaleFor(display, width, height));
                }
            }
            scale = Math.min(scale, 1);
            double capPixels = (double) capHeight * capHeight * 16 / 9;
            scale = Math.min(scale, Math.sqrt(capPixels / ((double) width * height)));
            int w = even(width * scale);
            int h = even(height * scale);
            return new Copy(w, h, FfmpegCommand.pictureBitrateKbps(equivalentHeight(w, h)));
        }
    }

    private static double scaleFor(Display display, int width, int height) {
        if (display.widthBlocks() <= 0 || display.heightBlocks() <= 0 || width <= 0 || height <= 0) return 0;
        double perBlock = DisplayResolution.LINES_PER_BLOCK;
        double floor = DisplayResolution.MIN_LINES;
        double boxWidth = display.widthBlocks() * perBlock;
        double boxHeight = display.heightBlocks() * perBlock;
        if (boxHeight < floor) {
            boxWidth *= floor / boxHeight;
            boxHeight = floor;
        }
        double across = boxWidth / width;
        double down = boxHeight / height;
        return display.fill() ? Math.max(across, down) : Math.min(across, down);
    }

    public static int sixteenByNineWidth(int height) {
        return (int) Math.round(height * 16 / 9.0 / 2) * 2;
    }

    public static int equivalentHeight(int width, int height) {
        return (int) Math.round(Math.sqrt((double) width * height * 9 / 16));
    }

    public static int stepBelow(Copy copy) {
        int height = copy.equivalentHeight();
        for (int step : STEPS) {
            if (step <= height * MOST_OF_A_STEP) return step;
        }
        return 0;
    }

    private static int even(double pixels) {
        return Math.max(2, (int) Math.floor(pixels / 2 + 1e-6) * 2);
    }
}
