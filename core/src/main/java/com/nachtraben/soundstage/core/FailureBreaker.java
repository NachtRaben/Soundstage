package com.nachtraben.soundstage.core;

public final class FailureBreaker {

    public static final int TRIP_THRESHOLD = 3;

    public static final long SUSPENSION_MILLIS = 60_000;

    private int consecutiveFailures = 0;
    private long suspendedUntilMillis = 0;
    private String reason = "";

    public synchronized boolean recordFailure(String detail, boolean fatal, long nowMillis) {
        if (fatal) {
            trip(detail, nowMillis);
            return true;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= TRIP_THRESHOLD) {
            trip(detail, nowMillis);
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
    }

    private void trip(String detail, long nowMillis) {
        consecutiveFailures = 0;
        suspendedUntilMillis = nowMillis + SUSPENSION_MILLIS;
        reason = detail;
    }

    public synchronized boolean isSuspended(long nowMillis) {
        if (suspendedUntilMillis == 0) return false;
        if (nowMillis >= suspendedUntilMillis) {
            clear();
            return false;
        }
        return true;
    }

    public synchronized String reason() {
        return reason;
    }

    public synchronized long remainingSeconds(long nowMillis) {
        return Math.max(0, (suspendedUntilMillis - nowMillis) / 1000);
    }

    public synchronized void clear() {
        suspendedUntilMillis = 0;
        reason = "";
        consecutiveFailures = 0;
    }

    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }
}
