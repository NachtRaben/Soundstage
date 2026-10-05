package com.nachtraben.soundstage.core.video;

public final class VideoClock {

    public static final long HEARTBEAT_LAG_MILLIS = 300;

    public static final long HEARD_BEHIND_LIMIT_MILLIS = 5000;

    public static final long HEARD_AHEAD_LIMIT_MILLIS = 500;

    public static final long TRANSITION_NANOS = 3_000_000_000L;

    public static final long HEARD_FRESH_NANOS = 1_000_000_000L;

    private enum Heard { NOTHING, FOLLOWING, HOLDING }

    private final long offsetMillis;

    private long generationStartNanos;

    private boolean haveHeartbeat;
    private long heartbeatMillis;
    private boolean heartbeatPaused;
    private long heartbeatNanos;

    private Heard heard = Heard.NOTHING;
    private double heardMillis;
    private long heardNanos;

    public VideoClock(long offsetMillis) {
        this.offsetMillis = offsetMillis;
    }

    public synchronized void start(long nowNanos) {
        generationStartNanos = nowNanos;
        haveHeartbeat = false;
        heard = Heard.NOTHING;
    }

    public synchronized void heartbeat(long trackMillis, boolean paused, long nowNanos) {
        haveHeartbeat = true;
        heartbeatMillis = trackMillis;
        heartbeatPaused = paused;
        heartbeatNanos = nowNanos;
    }

    public synchronized void heard(double trackMillis, long nowNanos) {
        if (Double.isNaN(trackMillis)) {
            heard = Heard.NOTHING;
            return;
        }
        boolean transition = nowNanos - generationStartNanos < TRANSITION_NANOS;
        if (haveHeartbeat) {
            double server = serverMillisAt(nowNanos);
            boolean agrees = trackMillis >= server - HEARD_BEHIND_LIMIT_MILLIS
                    && trackMillis <= server + HEARD_AHEAD_LIMIT_MILLIS;
            heard = agrees ? Heard.FOLLOWING : transition ? Heard.HOLDING : Heard.NOTHING;
        } else {
            heard = transition ? Heard.HOLDING : Heard.FOLLOWING;
        }
        heardMillis = trackMillis;
        heardNanos = nowNanos;
    }

    public synchronized double millisAt(long nowNanos) {
        if (heard != Heard.NOTHING && nowNanos - heardNanos <= HEARD_FRESH_NANOS) {
            if (heard == Heard.HOLDING) return Double.NaN;
            return heardMillis + millisBetween(heardNanos, nowNanos) + offsetMillis;
        }
        if (!haveHeartbeat) return Double.NaN;
        return serverMillisAt(nowNanos) - HEARTBEAT_LAG_MILLIS + offsetMillis;
    }

    private double serverMillisAt(long nowNanos) {
        return heartbeatPaused ? heartbeatMillis : heartbeatMillis + millisBetween(heartbeatNanos, nowNanos);
    }

    private static double millisBetween(long fromNanos, long toNanos) {
        return Math.max(0L, toNanos - fromNanos) / 1_000_000.0;
    }
}
