package com.nachtraben.soundstage.audio.media;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MediaBrowseThrottle {

    private static final long MIN_INTERVAL_MS = 1000;

    private static final Map<UUID, Long> lastRequestAt = new ConcurrentHashMap<>();

    private MediaBrowseThrottle() {}

    static boolean tryAcquire(UUID player) {
        long now = System.currentTimeMillis();
        Long previous = lastRequestAt.put(player, now);
        return previous == null || now - previous >= MIN_INTERVAL_MS;
    }
}
