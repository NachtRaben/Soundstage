package com.nachtraben.soundstage.core;

import java.util.ArrayDeque;

public final class LiveInputBuffer {

    public static final int TARGET_FRAMES = 5;
    public static final int HIGH_WATER_FRAMES = 15;
    public static final int MAX_PACKET_BYTES = 1275;
    public static final int PACKETS_PER_SECOND = 60;
    public static final int BURST_PACKETS = 25;
    public static final long IDLE_NANOS = 10_000_000_000L;

    public enum Offer { ACCEPTED, OVERSIZED, TOO_FAST, CLOSED }

    private final ArrayDeque<byte[]> packets = new ArrayDeque<>();
    private boolean priming = true;
    private boolean closed;
    private long lastOfferNanos;
    private long lastRefillNanos;
    private double tokens = BURST_PACKETS;

    private long accepted;
    private long refused;
    private long skipped;
    private long underruns;

    public LiveInputBuffer(long nowNanos) {
        this.lastOfferNanos = nowNanos;
        this.lastRefillNanos = nowNanos;
    }

    public synchronized Offer offer(byte[] packet, long nowNanos) {
        if (closed) return Offer.CLOSED;
        if (packet == null || packet.length == 0 || packet.length > MAX_PACKET_BYTES) {
            refused++;
            return Offer.OVERSIZED;
        }
        tokens = Math.min(BURST_PACKETS,
                tokens + (nowNanos - lastRefillNanos) * PACKETS_PER_SECOND / 1e9);
        lastRefillNanos = nowNanos;
        if (tokens < 1) {
            refused++;
            return Offer.TOO_FAST;
        }
        tokens -= 1;
        lastOfferNanos = nowNanos;
        accepted++;
        packets.addLast(packet);
        if (packets.size() > HIGH_WATER_FRAMES) {
            skipTo(TARGET_FRAMES);
        }
        return Offer.ACCEPTED;
    }

    public synchronized byte[] poll() {
        if (closed) return null;
        if (priming) {
            if (packets.size() < TARGET_FRAMES) return null;
            priming = false;
        }
        byte[] next = packets.pollFirst();
        if (next == null) {
            priming = true;
            underruns++;
        }
        return next;
    }

    public synchronized void rejoinEdge() {
        skipTo(TARGET_FRAMES);
    }

    private void skipTo(int frames) {
        while (packets.size() > frames) {
            packets.removeFirst();
            skipped++;
        }
    }

    public synchronized boolean idle(long nowNanos) {
        return nowNanos - lastOfferNanos > IDLE_NANOS;
    }

    public synchronized void close() {
        closed = true;
        packets.clear();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized int buffered() {
        return packets.size();
    }

    public synchronized long accepted() {
        return accepted;
    }

    public synchronized long refused() {
        return refused;
    }

    public synchronized long skipped() {
        return skipped;
    }

    public synchronized long underruns() {
        return underruns;
    }
}
