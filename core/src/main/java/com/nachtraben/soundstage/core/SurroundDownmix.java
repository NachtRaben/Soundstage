package com.nachtraben.soundstage.core;

import java.util.Collection;

public final class SurroundDownmix {

    public static final int CHANNELS = 6;

    static final float FOLD = 0.70710678f;

    static final float SUB_GAIN = 3.0f;

    static final float LFE_IN_BAND_GAIN = 3.1622777f / 2f;

    private SurroundDownmix() {
    }

    public record Layout(boolean centre, boolean surroundLeft, boolean surroundRight) {
        public static final Layout NONE = new Layout(false, false, false);

        public static Layout of(Collection<ChannelMode> modes) {
            return new Layout(modes.contains(ChannelMode.CENTER), modes.contains(ChannelMode.SURROUND_LEFT),
                    modes.contains(ChannelMode.SURROUND_RIGHT));
        }
    }

    public static short sample(short[] six, int at, ChannelMode mode, Layout layout) {
        float fl = six[at], fr = six[at + 1], c = six[at + 2], lfe = six[at + 3], sl = six[at + 4], sr = six[at + 5];
        float value = switch (mode) {
            case CENTER -> c;
            case SURROUND_LEFT -> sl;
            case SURROUND_RIGHT -> sr;
            case LEFT -> left(fl, c, sl, layout);
            case RIGHT -> right(fr, c, sr, layout);
            case LFE -> SUB_GAIN * ((fl + fr) / 2 + FOLD * c + FOLD * (sl + sr) / 2 + LFE_IN_BAND_GAIN * lfe);
            default -> (left(fl, c, sl, layout) + right(fr, c, sr, layout)) / 2;
        };
        return (short) Math.max(-32768, Math.min(32767, Math.round(value)));
    }

    private static float left(float fl, float c, float sl, Layout layout) {
        return fl + (layout.centre() ? 0 : FOLD * c) + (layout.surroundLeft() ? 0 : FOLD * sl);
    }

    private static float right(float fr, float c, float sr, Layout layout) {
        return fr + (layout.centre() ? 0 : FOLD * c) + (layout.surroundRight() ? 0 : FOLD * sr);
    }
}
