package com.nachtraben.soundstage.config;

import com.nachtraben.soundstage.core.AudioSourceSettings;

import java.util.List;

public interface SoundstageSettings extends AudioSourceSettings {

    default void setOauthRefreshToken(String token) {}

    default List<String> urlAllowPatterns() { return List.of(); }

    default List<String> urlDenyPatterns() { return List.of(); }

    default int maxPlaylistTracks() { return 25; }

    default int maxQueueSize() { return 100; }

    default int maxPlayingReceivers() { return 16; }

    default boolean enableLiveInput() { return true; }

    default boolean enableVideo() { return true; }

    default boolean enableMediaFolder() { return true; }

    default String ffmpegPath() { return "ffmpeg"; }

    default boolean downloadFfmpeg() { return false; }

    default int maxVideoHeight() { return 480; }
}
