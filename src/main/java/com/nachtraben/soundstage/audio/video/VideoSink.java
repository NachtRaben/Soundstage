package com.nachtraben.soundstage.audio.video;

public interface VideoSink {
    void begin(int generation, long startMillis);

    void accept(int generation, long ptsMillis, com.nachtraben.soundstage.core.video.VideoAccessUnit unit);

    void end(int generation);

    default void abandon() {
    }

    default boolean hasScreens() {
        return true;
    }

    default boolean wantsPicture() {
        return true;
    }

    default com.nachtraben.soundstage.core.video.SurroundBuffer surround() {
        return null;
    }

    default void subtitles(com.nachtraben.soundstage.core.video.CueTrack cues) {
    }

    default @org.jetbrains.annotations.Nullable String playerLanguage() {
        return null;
    }

    default void subtitleSource(SubtitleSource source) {
        String key = source.autoKey(playerLanguage());
        if (key != null) source.start(key, this::subtitles, () -> true);
    }
}
