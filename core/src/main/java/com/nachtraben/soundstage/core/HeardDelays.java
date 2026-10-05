package com.nachtraben.soundstage.core;

import java.util.Arrays;

public final class HeardDelays {

    public static final double UNKNOWN = Double.NEGATIVE_INFINITY;

    private final double[] fromSamples;
    private final double[] delays;

    HeardDelays(double[] fromSamples, double[] delays) {
        assert ascending(fromSamples) : "stretches out of order: " + Arrays.toString(fromSamples);
        this.fromSamples = fromSamples;
        this.delays = delays;
    }

    private static boolean ascending(double[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) return false;
        }
        return true;
    }

    public double delayAt(double groupSample) {
        int at = Arrays.binarySearch(fromSamples, groupSample);
        int index;
        if (at >= 0) {
            index = at;
            while (index + 1 < fromSamples.length && fromSamples[index + 1] == groupSample) index++;
        } else {
            index = -at - 2;
        }
        return index < 0 ? UNKNOWN : delays[index];
    }

    public int stretches() {
        return delays.length;
    }
}
