package com.nachtraben.soundstage.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.OptionalDouble;

public final class DeviceClockTimeline {

    private DeviceClockTimeline() {}

    public record Point(double groupSample, long deviceClockNanos) {}

    public static OptionalDouble medianAt(Collection<Point> points, long targetClockNanos,
                                          int sampleRate, long maximumAgeNanos) {
        if (points == null || points.isEmpty() || sampleRate <= 0 || maximumAgeNanos < 0) {
            return OptionalDouble.empty();
        }

        ArrayList<Double> projected = new ArrayList<>(points.size());
        for (Point point : points) {
            if (point == null || !Double.isFinite(point.groupSample())) continue;
            long age = targetClockNanos - point.deviceClockNanos();
            if (age < 0 || age > maximumAgeNanos) continue;
            projected.add(point.groupSample() + age * (sampleRate / 1_000_000_000.0));
        }
        if (projected.isEmpty()) return OptionalDouble.empty();

        projected.sort(Double::compareTo);
        int middle = projected.size() / 2;
        double median = projected.size() % 2 == 0
                ? (projected.get(middle - 1) + projected.get(middle)) / 2.0
                : projected.get(middle);
        return OptionalDouble.of(median);
    }
}
