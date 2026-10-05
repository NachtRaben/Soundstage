package com.nachtraben.soundstage.core.video;

import com.nachtraben.soundstage.core.VolumeBoost;

public final class SurroundFrame {

    public static final int SAMPLES = 960;

    private final short[] six = new short[SAMPLES * SurroundBuffer.CHANNELS];
    private final short[] frontPair = new short[SAMPLES * 2];
    private final short[] centrePair = new short[SAMPLES * 2];
    private final short[] surroundPair = new short[SAMPLES * 2];

    public short[] samples() {
        return six;
    }

    public void applyVolume(int receiverVolume) {
        int lava = Math.min(receiverVolume, 100);
        if (lava == 100) return;
        int multiplier = VolumeBoost.lavaPlayerMultiplier(lava);
        for (int i = 0; i < six.length; i++) {
            six[i] = lava == 0 ? 0 : (short) Math.max(-32768, Math.min(32767, six[i] * multiplier / 10000));
        }
    }

    public void split(double[] gains) {
        if (gains != null) {
            for (int i = 0; i < six.length; i++) {
                int sample = i / SurroundBuffer.CHANNELS;
                if (sample < gains.length) six[i] = VolumeBoost.scaled(six[i], gains[sample]);
            }
        }
        for (int s = 0; s < SAMPLES; s++) {
            int at = s * SurroundBuffer.CHANNELS;
            frontPair[s * 2] = six[at];
            frontPair[s * 2 + 1] = six[at + 1];
            centrePair[s * 2] = six[at + 2];
            centrePair[s * 2 + 1] = six[at + 3];
            surroundPair[s * 2] = six[at + 4];
            surroundPair[s * 2 + 1] = six[at + 5];
        }
    }

    public short[] front() {
        return frontPair;
    }

    public short[] centreAndLfe() {
        return centrePair;
    }

    public short[] surround() {
        return surroundPair;
    }
}
