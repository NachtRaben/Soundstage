package com.nachtraben.soundstage.core;

public final class AudioCorrelation {

    private AudioCorrelation() {}

    public record Echo(int lagSamples, double strength) {
        public static final Echo NONE = new Echo(-1, 0.0);
    }

    public static Echo strongestEcho(short[] samples, int minLagSamples, int maxLagSamples) {
        return strongestEchoOutside(samples, minLagSamples, maxLagSamples, -1, -1);
    }

    public static Echo strongestEchoOutside(short[] samples, int minLagSamples, int maxLagSamples,
                                            int excludedFrom, int excludedTo) {
        if (samples == null || samples.length == 0) return Echo.NONE;
        int maxLag = Math.min(maxLagSamples, samples.length - 1);
        if (minLagSamples > maxLag || minLagSamples < 1) return Echo.NONE;

        double energy = 0;
        for (short s : samples) energy += (double) s * s;
        if (energy <= 0) return Echo.NONE;

        int bestLag = -1;
        double best = 0;
        for (int lag = minLagSamples; lag <= maxLag; lag++) {
            if (lag >= excludedFrom && lag <= excludedTo) continue;
            double sum = 0;
            for (int i = 0; i + lag < samples.length; i++) {
                sum += (double) samples[i] * samples[i + lag];
            }
            double overlapEnergy = 0;
            for (int i = 0; i + lag < samples.length; i++) {
                overlapEnergy += (double) samples[i] * samples[i];
            }
            if (overlapEnergy <= 0) continue;
            double r = sum / overlapEnergy;
            if (r > best) {
                best = r;
                bestLag = lag;
            }
        }
        return bestLag == -1 ? Echo.NONE : new Echo(bestLag, best);
    }

    public record Alignment(int lagSamples, double strength) {
        public static final Alignment NONE = new Alignment(0, 0.0);
    }

    public static Alignment bestAlignment(short[] a, short[] b, int maxLagSamples) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return Alignment.NONE;
        int minOverlap = Math.min(a.length, b.length) / 2;
        if (minOverlap <= 0) return Alignment.NONE;
        int maxLag = Math.min(maxLagSamples, Math.max(a.length, b.length) - 1);

        int bestLag = 0;
        double best = 0.0;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            int aFrom = Math.max(0, -lag);
            int bFrom = Math.max(0, lag);
            int overlap = Math.min(a.length - aFrom, b.length - bFrom);
            if (overlap < minOverlap) continue;
            double sum = 0, energyA = 0, energyB = 0;
            for (int i = 0; i < overlap; i++) {
                double x = a[aFrom + i];
                double y = b[bFrom + i];
                sum += x * y;
                energyA += x * x;
                energyB += y * y;
            }
            if (energyA <= 0 || energyB <= 0) continue;
            double r = sum / Math.sqrt(energyA * energyB);
            if (r > best) {
                best = r;
                bestLag = lag;
            }
        }
        return best <= 0 ? Alignment.NONE : new Alignment(bestLag, best);
    }

    public static short[] decimate(short[] samples, int factor) {
        if (samples == null || samples.length == 0 || factor <= 1) return samples;
        int out = samples.length / factor;
        short[] reduced = new short[out];
        for (int i = 0; i < out; i++) {
            int sum = 0;
            for (int j = 0; j < factor; j++) sum += samples[i * factor + j];
            reduced[i] = (short) (sum / factor);
        }
        return reduced;
    }

    public static short[] sweep(double startHz, double endHz, double seconds, float sampleRate) {
        int total = (int) (seconds * sampleRate);
        short[] out = new short[total];
        double phase = 0;
        for (int i = 0; i < total; i++) {
            double t = (double) i / total;
            double f = t < 0.5
                    ? startHz + (endHz - startHz) * (t * 2)
                    : endHz - (endHz - startHz) * ((t - 0.5) * 2);
            phase += 2 * Math.PI * f / sampleRate;
            out[i] = (short) (Math.sin(phase) * 20000);
        }
        return out;
    }
}
