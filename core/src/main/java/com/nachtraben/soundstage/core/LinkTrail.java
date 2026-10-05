package com.nachtraben.soundstage.core;

public final class LinkTrail {
    private LinkTrail() {}

    public static double[] sample(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int budget) {
        if (budget <= 0) return new double[0];
        long dx = (long) toX - fromX, dy = (long) toY - fromY, dz = (long) toZ - fromZ;
        double distance = Math.sqrt((double) (dx * dx + dy * dy + dz * dz));
        if (distance <= 0.0) return new double[0];
        int steps = (int) Math.min(Math.max(1, Math.round(distance)), budget);
        double[] points = new double[steps * 3];
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            points[i * 3] = fromX + 0.5 + dx * t;
            points[i * 3 + 1] = fromY + 0.5 + dy * t;
            points[i * 3 + 2] = fromZ + 0.5 + dz * t;
        }
        return points;
    }
}
