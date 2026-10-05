package com.nachtraben.soundstage.audio.video;

public interface VideoCapableTrack {
    void setVideoSink(@org.jetbrains.annotations.Nullable VideoSink sink);

    default void setTargetHeight(int height) {}

    default boolean surroundPending() {
        return false;
    }
}
