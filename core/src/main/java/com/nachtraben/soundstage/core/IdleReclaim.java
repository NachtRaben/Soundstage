package com.nachtraben.soundstage.core;

public final class IdleReclaim {

    private IdleReclaim() {}

    public static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;

    public enum Decision {
        ACTIVE,
        WAITING,
        RECLAIM
    }

    public static Decision decide(boolean hasTrack, boolean paused, long idleSinceMillis, long now) {
        if (hasTrack && !paused) {
            return Decision.ACTIVE;
        }
        long idleFor = now - idleSinceMillis;
        return idleFor >= TIMEOUT_MILLIS ? Decision.RECLAIM : Decision.WAITING;
    }
}
