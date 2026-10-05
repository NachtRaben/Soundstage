package com.nachtraben.soundstage.core;

public final class SpectrumBands {

    public static final int FFT_SIZE = 2048;

    public static final double MIN_HZ = 40.0;
    public static final double MAX_HZ = 16_000.0;

    public static final double MIN_DB = -120.0;
    public static final double MAX_DB = 0.0;

    private SpectrumBands() {
    }

    public static double[] bandsDb(float[] mono, int sampleRate, int bandCount) {
        if (mono.length != FFT_SIZE) {
            throw new IllegalArgumentException("expected " + FFT_SIZE + " samples, got " + mono.length);
        }
        double[] re = new double[FFT_SIZE];
        double[] im = new double[FFT_SIZE];
        for (int i = 0; i < FFT_SIZE; i++) {
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
            re[i] = mono[i] * w;
            im[i] = 0.0;
        }
        fft(re, im);

        double[] magnitude = new double[FFT_SIZE / 2];
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] = Math.hypot(re[i], im[i]);
        }

        double[] bands = new double[bandCount];
        double logMin = Math.log(MIN_HZ);
        double logMax = Math.log(MAX_HZ);
        double binHz = (double) sampleRate / FFT_SIZE;
        for (int b = 0; b < bandCount; b++) {
            double loHz = Math.exp(logMin + (logMax - logMin) * b / bandCount);
            double hiHz = Math.exp(logMin + (logMax - logMin) * (b + 1) / bandCount);
            int loBin = Math.max(1, (int) Math.floor(loHz / binHz + 0.5));
            int hiBin = Math.min(magnitude.length - 1, (int) Math.floor(hiHz / binHz + 0.5));
            double power = 0.0;
            for (int bin = loBin; bin <= hiBin; bin++) {
                double overlap = Math.min(hiHz, (bin + 0.5) * binHz) - Math.max(loHz, (bin - 0.5) * binHz);
                if (overlap > 0) power += magnitude[bin] * magnitude[bin] * (overlap / binHz);
            }
            double normalized = power / (1.5 * (FFT_SIZE * 0.25) * (FFT_SIZE * 0.25));
            double db = normalized > 0 ? 10.0 * Math.log10(normalized) : MIN_DB;
            bands[b] = clamp(db, MIN_DB, MAX_DB);
        }
        return bands;
    }

    public static final class Leveller {
        static final double RANGE_DB = 55.0;
        static final double HEADROOM_DB = 6.0;
        static final double FLOOR_DB = -45.0;
        static final double ATTACK_SECONDS = 0.5;
        static final double RELEASE_SECONDS = 4.0;

        private final double[] heights;
        private double reference = Double.NaN;

        public Leveller(int bandCount) {
            heights = new double[bandCount];
        }

        public double[] heights(double[] bandsDb, double dtSeconds) {
            double loudest = MIN_DB;
            for (int b = 0; b < heights.length && b < bandsDb.length; b++) {
                loudest = Math.max(loudest, bandsDb[b]);
            }
            if (Double.isNaN(reference)) {
                reference = Math.max(FLOOR_DB, loudest);
            } else {
                double tau = loudest > reference ? ATTACK_SECONDS : RELEASE_SECONDS;
                reference += (loudest - reference) * (1.0 - Math.exp(-Math.max(0.0, dtSeconds) / tau));
                reference = Math.max(FLOOR_DB, reference);
            }
            double bottom = reference + HEADROOM_DB - RANGE_DB;
            for (int b = 0; b < heights.length; b++) {
                double db = b < bandsDb.length ? bandsDb[b] : MIN_DB;
                heights[b] = clamp((db - bottom) / RANGE_DB, 0.0, 1.0);
            }
            return heights;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int start = 0; start < n; start += len) {
                double curWr = 1.0, curWi = 0.0;
                int half = len / 2;
                for (int k = 0; k < half; k++) {
                    int a = start + k;
                    int b = start + k + half;
                    double ur = re[a], ui = im[a];
                    double vr = re[b] * curWr - im[b] * curWi;
                    double vi = re[b] * curWi + im[b] * curWr;
                    re[a] = ur + vr;
                    im[a] = ui + vi;
                    re[b] = ur - vr;
                    im[b] = ui - vi;
                    double nextWr = curWr * wr - curWi * wi;
                    double nextWi = curWr * wi + curWi * wr;
                    curWr = nextWr;
                    curWi = nextWi;
                }
            }
        }
    }

    public static final class Smoother {
        private static final double RISE_TAU_SECONDS = 0.05;
        private static final double FALL_TAU_SECONDS = 0.15;

        private final double[] value;

        public Smoother(int bandCount) {
            this.value = new double[bandCount];
        }

        public void update(double[] targetHeights, double dtSeconds) {
            double dt = Math.max(0.0, dtSeconds);
            for (int i = 0; i < value.length && i < targetHeights.length; i++) {
                double target = targetHeights[i];
                double tau = target > value[i] ? RISE_TAU_SECONDS : FALL_TAU_SECONDS;
                double alpha = tau > 0 ? 1.0 - Math.exp(-dt / tau) : 1.0;
                value[i] += (target - value[i]) * alpha;
            }
        }

        public double[] heights() {
            return value;
        }
    }
}
