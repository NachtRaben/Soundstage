package com.nachtraben.soundstage.audio.video;

import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.PictureSize;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface VideoCapableTrack {
    void setVideoSink(@Nullable VideoSink sink);

    default void setPictureTarget(PictureSize.Target target) {}

    default boolean surroundPending() {
        return false;
    }

    default boolean isStill() {
        return false;
    }

    enum PictureRetake {
        NONE,
        SEEK,
        REPLAY
    }

    default PictureRetake pictureRetake() {
        return PictureRetake.NONE;
    }

    default void setSoundChoices(@Nullable SoundChoices choices) {
    }

    default @Nullable List<AudioChoice.Entry> audioEntries() {
        return null;
    }

    default @Nullable String audioKey() {
        return null;
    }

    default PictureRetake applyAudio() {
        return PictureRetake.NONE;
    }
}
