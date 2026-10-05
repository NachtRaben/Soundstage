package com.nachtraben.soundstage.core.video;

public final class SurroundBuffer {

    public static final int CHANNELS = 6;
    public static final int SAMPLE_RATE = 48_000;
    public static final int CAPACITY_SAMPLES = SAMPLE_RATE * 8;

    private volatile java.util.function.BooleanSupplier wanted = () -> false;
    private short[] ring;
    private int generation;
    private long firstSample;
    private long endSample;
    private boolean running;

    public void setWanted(java.util.function.BooleanSupplier wanted) {
        this.wanted = wanted;
    }

    public boolean wanted() {
        return wanted.getAsBoolean();
    }

    public synchronized int begin(long startMillis) {
        generation++;
        firstSample = startMillis * (SAMPLE_RATE / 1000);
        endSample = firstSample;
        running = true;
        return generation;
    }

    public synchronized void clear() {
        generation++;
        running = false;
        endSample = firstSample;
    }

    public synchronized void write(int generation, long at, short[] interleaved, int samples) {
        if (!running || generation != this.generation || samples <= 0) return;
        if (ring == null) ring = new short[CAPACITY_SAMPLES * CHANNELS];
        if (at != endSample) {
            firstSample = at;
            endSample = at;
        }
        for (int s = 0; s < samples; s++) {
            int slot = (int) Math.floorMod(endSample + s, (long) CAPACITY_SAMPLES) * CHANNELS;
            System.arraycopy(interleaved, s * CHANNELS, ring, slot, CHANNELS);
        }
        endSample += samples;
        firstSample = Math.max(firstSample, endSample - CAPACITY_SAMPLES);
    }

    public synchronized boolean read(long at, int samples, short[] out) {
        if (!running || ring == null || at < firstSample || at + samples > endSample) return false;
        for (int s = 0; s < samples; s++) {
            int slot = (int) Math.floorMod(at + s, (long) CAPACITY_SAMPLES) * CHANNELS;
            System.arraycopy(ring, slot, out, s * CHANNELS, CHANNELS);
        }
        return true;
    }
}
