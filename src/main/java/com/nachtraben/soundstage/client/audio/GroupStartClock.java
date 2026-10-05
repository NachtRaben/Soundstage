package com.nachtraben.soundstage.client.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class GroupStartClock {

    static final long HOLD_LIMIT_NANOS = 150_000_000L;

    private final AtomicLong deviceNanos = new AtomicLong(-1L);
    private final AtomicInteger played = new AtomicInteger();

    private final List<Integer> held = new ArrayList<>();
    private int expected;
    private boolean sealed;
    private long firstHeldNanos;

    long claim(long proposed) {
        deviceNanos.compareAndSet(-1L, proposed);
        return deviceNanos.get();
    }

    int nextIndex() {
        return played.getAndIncrement();
    }

    synchronized void expectOne() {
        expected++;
    }

    synchronized void seal() {
        sealed = true;
    }

    synchronized int[] hold(int source, long nowNanos) {
        held.add(source);
        if (firstHeldNanos == 0) firstHeldNanos = nowNanos;
        return sealed && held.size() >= expected ? drain() : null;
    }

    synchronized int[] releaseIfDue(long nowNanos, long limitNanos) {
        if (held.isEmpty()) return null;
        if ((sealed && held.size() >= expected) || nowNanos - firstHeldNanos >= limitNanos) return drain();
        return null;
    }

    private int[] drain() {
        int[] sources = new int[held.size()];
        for (int i = 0; i < sources.length; i++) sources[i] = held.get(i);
        held.clear();
        return sources;
    }
}
