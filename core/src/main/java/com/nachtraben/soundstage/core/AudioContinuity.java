package com.nachtraben.soundstage.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioContinuity {

    private AudioContinuity() {}

    public static final double DROPOUT_FRACTION = 0.1;

    public static int countDropouts(short[] samples, int windowLength) {
        return countDropouts(samples, windowLength, -1, -1);
    }

    public static int countDropouts(short[] samples, int windowLength,
                                    int exemptFromSample, int exemptToSample) {
        if (samples == null || windowLength <= 0) return 0;
        int windows = samples.length / windowLength;
        if (windows < 3) return 0;

        double[] peaks = new double[windows];
        for (int w = 0; w < windows; w++) {
            int peak = 0;
            int base = w * windowLength;
            for (int i = base; i < base + windowLength; i++) {
                peak = Math.max(peak, Math.abs(samples[i]));
            }
            peaks[w] = peak;
        }

        double median = median(peaks);
        if (median <= 0) return 0;

        int start = 0;
        while (start < windows && peaks[start] <= median * 0.5) start++;

        int dropouts = 0;
        for (int w = start + 1; w < windows; w++) {
            int mid = w * windowLength + windowLength / 2;
            if (exemptFromSample >= 0 && mid >= exemptFromSample && mid < exemptToSample) continue;
            if (peaks[w] < median * DROPOUT_FRACTION) dropouts++;
        }
        return dropouts;
    }

    public record PauseAwareResult(int dropouts, int[] resumeLatencySamples) {}

    public static PauseAwareResult countDropoutsAcrossPauses(short[] samples, int windowLength,
                                                             int[][] pauses, int maxResumeLatencySamples,
                                                             int exemptFromSample, int exemptToSample) {
        int[] latencies = new int[pauses == null ? 0 : pauses.length];
        Arrays.fill(latencies, -1);
        if (samples == null || windowLength <= 0) return new PauseAwareResult(0, latencies);
        int windows = samples.length / windowLength;
        if (windows < 3) return new PauseAwareResult(0, latencies);

        double[] peaks = new double[windows];
        boolean[] paused = new boolean[windows];
        List<Double> active = new ArrayList<>();
        for (int w = 0; w < windows; w++) {
            int peak = 0;
            int base = w * windowLength;
            for (int i = base; i < base + windowLength; i++) {
                peak = Math.max(peak, Math.abs(samples[i]));
            }
            peaks[w] = peak;
            int mid = base + windowLength / 2;
            if (pauses != null) {
                for (int[] pause : pauses) {
                    if (mid >= pause[0] && mid < pause[1] + maxResumeLatencySamples) paused[w] = true;
                }
            }
            if (!paused[w]) active.add((double) peak);
        }
        if (active.size() < 3) return new PauseAwareResult(0, latencies);
        double median = median(active.stream().mapToDouble(Double::doubleValue).toArray());
        if (median <= 0) return new PauseAwareResult(0, latencies);

        boolean[] exempt = new boolean[windows];
        if (pauses != null) {
            for (int p = 0; p < pauses.length; p++) {
                int resumeWindow = -1;
                for (int w = Math.max(0, pauses[p][1] / windowLength); w < windows; w++) {
                    if (w * windowLength - pauses[p][1] > maxResumeLatencySamples) break;
                    if (peaks[w] >= median * 0.5) {
                        resumeWindow = w;
                        break;
                    }
                }
                int exemptUntil = resumeWindow >= 0 ? resumeWindow * windowLength : pauses[p][1];
                if (resumeWindow >= 0) latencies[p] = Math.max(0, resumeWindow * windowLength - pauses[p][1]);
                for (int w = 0; w < windows; w++) {
                    int mid = w * windowLength + windowLength / 2;
                    if (mid >= pauses[p][0] && mid < exemptUntil) exempt[w] = true;
                }
            }
        }

        int start = 0;
        while (start < windows && peaks[start] <= median * 0.5) start++;
        int dropouts = 0;
        for (int w = start + 1; w < windows; w++) {
            int mid = w * windowLength + windowLength / 2;
            if (exempt[w]) continue;
            if (exemptFromSample >= 0 && mid >= exemptFromSample && mid < exemptToSample) continue;
            if (peaks[w] < median * DROPOUT_FRACTION) dropouts++;
        }
        return new PauseAwareResult(dropouts, latencies);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2.0 : sorted[mid];
    }
}
