package com.nachtraben.soundstage.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class InFlightLimit<K> {

    private final int max;
    private final Map<K, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    public InFlightLimit(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be at least 1: " + max);
        }
        this.max = max;
    }

    public Runnable tryAcquire(K key) {
        AtomicInteger count = inFlight.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > max) {
            count.decrementAndGet();
            return null;
        }
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                count.decrementAndGet();
            }
        };
    }

    public void forget(K key) {
        inFlight.remove(key);
    }

    public void clear() {
        inFlight.clear();
    }
}
