package com.nachtraben.soundstage.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SubtitleChoiceThrottle {

    static final long MIN_INTERVAL_MS = 250;

    private static final Map<UUID, Long> lastAcceptedAt = new ConcurrentHashMap<>();

    private SubtitleChoiceThrottle() {}

    static boolean tryAcquire(UUID player, long nowMillis) {
        boolean[] accepted = {false};
        lastAcceptedAt.compute(player, (id, previous) -> {
            if (previous != null && nowMillis - previous < MIN_INTERVAL_MS) return previous;
            accepted[0] = true;
            return nowMillis;
        });
        return accepted[0];
    }
}
