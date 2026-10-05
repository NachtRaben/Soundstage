package com.nachtraben.soundstage.core;

public record SpeakerId(int x, int y, int z) {

    private static final int BITS_XZ = 26;
    private static final int BITS_Y = 12;
    private static final long MASK_XZ = (1L << BITS_XZ) - 1L;
    private static final long MASK_Y = (1L << BITS_Y) - 1L;

    public static SpeakerId of(int x, int y, int z) {
        return new SpeakerId(x, y, z);
    }

    public long asLong() {
        return ((long) x & MASK_XZ) << (BITS_Y + BITS_XZ)
                | ((long) y & MASK_Y)
                | ((long) z & MASK_XZ) << BITS_Y;
    }

    public static SpeakerId fromLong(long packed) {
        return new SpeakerId(
                (int) (packed << (64 - BITS_XZ - BITS_Y - BITS_XZ) >> (64 - BITS_XZ)),
                (int) (packed << (64 - BITS_Y) >> (64 - BITS_Y)),
                (int) (packed << (64 - BITS_XZ - BITS_Y) >> (64 - BITS_XZ))
        );
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
