package com.nachtraben.soundstage.audio.media;

public final class SoundOnlyFallbacks {

    @FunctionalInterface
    public interface Listener {
        void playedWithSoundOnly(String identifier, String reason);
    }

    public static volatile Listener listener;

    private SoundOnlyFallbacks() {}

    static void report(String identifier, String reason) {
        Listener heard = listener;
        if (heard == null) return;
        try {
            heard.playedWithSoundOnly(identifier, reason);
        } catch (RuntimeException ignored) {
        }
    }
}
