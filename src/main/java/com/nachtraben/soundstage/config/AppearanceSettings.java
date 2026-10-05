package com.nachtraben.soundstage.config;

public interface AppearanceSettings {

    default float driverExcursionGain() { return 1.0f; }

    default boolean trackChangeToast() { return true; }

    default boolean trackChangeToastArtwork() { return true; }

    default boolean showSubtitles() { return true; }

    default void setShowSubtitles(boolean showSubtitles) {}

    enum SubtitleSize { SMALL, MEDIUM, LARGE }

    default SubtitleSize subtitleSize() { return SubtitleSize.MEDIUM; }

    default void setSubtitleSize(SubtitleSize subtitleSize) {}

    default String shareDevice() { return ""; }

    default void setShareDevice(String device) {}
}
