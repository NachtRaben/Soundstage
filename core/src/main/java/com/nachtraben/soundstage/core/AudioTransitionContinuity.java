package com.nachtraben.soundstage.core;

import java.util.Arrays;

public final class AudioTransitionContinuity {

    private AudioTransitionContinuity() {}

    public static double minimumRmsRatio(short[] samples, int windowLength,
                                         int referenceFrom, int referenceTo,
                                         int eventFrom, int eventTo) {
        if (samples == null || windowLength <= 0) return Double.NaN;
        double[] reference = windowRms(samples, windowLength, referenceFrom, referenceTo);
        double[] event = windowRms(samples, windowLength, eventFrom, eventTo);
        if (reference.length == 0 || event.length == 0) return Double.NaN;

        Arrays.sort(reference);
        double median = reference.length % 2 == 0
                ? (reference[reference.length / 2 - 1] + reference[reference.length / 2]) / 2.0
                : reference[reference.length / 2];
        if (median <= 0.0) return Double.NaN;

        double minimum = Double.POSITIVE_INFINITY;
        for (double rms : event) minimum = Math.min(minimum, rms);
        return minimum / median;
    }

    public static double rmsRatio(short[] samples, int referenceFrom, int referenceTo,
                                  int eventFrom, int eventTo) {
        if (samples == null) return Double.NaN;
        double reference = rms(samples, referenceFrom, referenceTo);
        double event = rms(samples, eventFrom, eventTo);
        return reference > 0.0 && Double.isFinite(event) ? event / reference : Double.NaN;
    }

    private static double rms(short[] samples, int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(samples.length, to);
        if (end <= start) return Double.NaN;
        double energy = 0.0;
        for (int i = start; i < end; i++) {
            energy += (double) samples[i] * samples[i];
        }
        return Math.sqrt(energy / (end - start));
    }

    private static double[] windowRms(short[] samples, int windowLength, int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(samples.length, to);
        if (end - start < windowLength) return new double[0];
        int count = (end - start) / windowLength;
        double[] out = new double[count];
        for (int window = 0; window < count; window++) {
            int base = start + window * windowLength;
            double energy = 0.0;
            for (int i = base; i < base + windowLength; i++) {
                energy += (double) samples[i] * samples[i];
            }
            out[window] = Math.sqrt(energy / windowLength);
        }
        return out;
    }
}
