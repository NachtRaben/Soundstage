package com.nachtraben.soundstage.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FailureBreakers {

    private final Map<String, FailureBreaker> breakers = new ConcurrentHashMap<>();

    private FailureBreaker of(String sourceKey) {
        return breakers.computeIfAbsent(sourceKey, k -> new FailureBreaker());
    }

    public boolean recordFailure(String sourceKey, String detail, boolean fatal, long nowMillis) {
        return of(sourceKey).recordFailure(detail, fatal, nowMillis);
    }

    public void recordSuccess(String sourceKey) {
        of(sourceKey).recordSuccess();
    }

    public boolean isSuspended(String sourceKey, long nowMillis) {
        return of(sourceKey).isSuspended(nowMillis);
    }

    public String reason(String sourceKey) {
        return of(sourceKey).reason();
    }

    public long remainingSeconds(String sourceKey, long nowMillis) {
        return of(sourceKey).remainingSeconds(nowMillis);
    }

    public void clearAll() {
        for (FailureBreaker breaker : breakers.values()) {
            breaker.clear();
        }
    }

    public int consecutiveFailures(String sourceKey) {
        return of(sourceKey).consecutiveFailures();
    }
}
