package com.nachtraben.soundstage.core;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public final class PcmRing {

    public static final int RETAINED_FRAMES = 150;

    public static final int MAX_DELAY_FRAMES = 25;

    public static final int MAX_OUTPUT_LATENCY_FRAMES = 25;

    private final ConcurrentSkipListMap<Long, byte[]> frames = new ConcurrentSkipListMap<>();

    private final ConcurrentSkipListMap<Long, short[]> surround = new ConcurrentSkipListMap<>();

    public void add(long sequence, byte[] stereoPcm) {
        add(sequence, stereoPcm, null);
    }

    public void add(long sequence, byte[] stereoPcm, short[] surroundPcm) {
        if (surroundPcm != null) {
            surround.put(sequence, surroundPcm);
        } else {
            surround.remove(sequence);
        }
        frames.put(sequence, stereoPcm);
        while (frames.size() > RETAINED_FRAMES) {
            Map.Entry<Long, byte[]> dropped = frames.pollFirstEntry();
            if (dropped == null) break;
            surround.headMap(dropped.getKey(), true).clear();
        }
    }

    public byte[] get(long sequence) {
        return frames.get(sequence);
    }

    public short[] surround(long sequence) {
        return surround.get(sequence);
    }

    public long lowestSequence() {
        Map.Entry<Long, byte[]> first = frames.firstEntry();
        return first == null ? -1 : first.getKey();
    }

    public long highestSequence() {
        Map.Entry<Long, byte[]> last = frames.lastEntry();
        return last == null ? -1 : last.getKey();
    }

    public int size() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public void clear() {
        frames.clear();
        surround.clear();
    }
}
