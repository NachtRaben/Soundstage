package com.nachtraben.soundstage.core.video;

public enum ColorMatrix {
    BT601(0.299, 0.114),
    BT709(0.2126, 0.0722);

    public final double kr;
    public final double kb;

    ColorMatrix(double kr, double kb) {
        this.kr = kr;
        this.kb = kb;
    }

    public static ColorMatrix of(int matrixCoefficients, boolean colourDescriptionPresent,
            int heightPixels) {
        if (colourDescriptionPresent) {
            if (matrixCoefficients == 1) return BT709;
            if (matrixCoefficients == 5 || matrixCoefficients == 6 || matrixCoefficients == 7) {
                return BT601;
            }
        }
        return heightPixels >= 720 ? BT709 : BT601;
    }

    public Coefficients coefficients(boolean fullRange) {
        double yScale = fullRange ? 1.0 : 255.0 / 219.0;
        double cScale = fullRange ? 1.0 : 255.0 / 224.0;
        int yOffset = fullRange ? 0 : 16;
        double kg = 1.0 - kr - kb;
        return new Coefficients(
                (int) Math.round(yScale * 256),
                yOffset,
                (int) Math.round(cScale * 2 * (1 - kr) * 256),
                (int) Math.round(cScale * (2 * (1 - kb) * kb / kg) * 256),
                (int) Math.round(cScale * (2 * (1 - kr) * kr / kg) * 256),
                (int) Math.round(cScale * 2 * (1 - kb) * 256));
    }

    public record Coefficients(int yScale256, int yOffset, int rCr256, int gCb256, int gCr256,
                                int bCb256) {
    }
}
