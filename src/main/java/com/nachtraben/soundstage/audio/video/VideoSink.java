package com.nachtraben.soundstage.audio.video;

import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.SurroundBuffer;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import org.jetbrains.annotations.Nullable;

public interface VideoSink {
    void begin(int generation, long startMillis);

    void accept(int generation, long ptsMillis, VideoAccessUnit unit);

    void end(int generation);

    default void abandon() {
    }

    default boolean hasScreens() {
        return true;
    }

    default boolean wantsPicture() {
        return true;
    }

    default boolean takePicturePlace() {
        return true;
    }

    default void releasePicturePlace() {
    }

    default int rememberedStep() {
        return 0;
    }

    default void stepped(int height) {
    }

    default SurroundBuffer surround() {
        return null;
    }

    default void subtitles(CueTrack cues) {
    }

    default @Nullable String playerLanguage() {
        return null;
    }

    default void subtitleSource(SubtitleSource source) {
        String key = source.autoKey(playerLanguage());
        if (key != null) source.start(key, this::subtitles, () -> true);
    }
}
