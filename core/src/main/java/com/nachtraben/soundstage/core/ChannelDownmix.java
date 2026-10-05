package com.nachtraben.soundstage.core;

public final class ChannelDownmix {

    private ChannelDownmix() {
    }

    public static short sample(short left, short right, ChannelMode mode) {
        int mono;
        float multiplier = 1.0f;
        switch (mode) {
            case LEFT -> mono = left;
            case RIGHT -> mono = right;
            case SURROUND_LEFT -> {
                mono = left - right;
                multiplier = 0.6f;
            }
            case SURROUND_RIGHT -> {
                mono = right - left;
                multiplier = 0.6f;
            }
            case CENTER -> mono = (left + right) / 2;
            case LFE -> {
                mono = (left + right) / 2;
                multiplier = 3.0f;
            }
            default -> mono = (left + right) / 2;
        }
        int scaled = (int) (mono * multiplier);
        return (short) Math.max(-32768, Math.min(32767, scaled));
    }
}
