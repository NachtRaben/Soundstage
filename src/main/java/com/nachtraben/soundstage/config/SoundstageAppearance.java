package com.nachtraben.soundstage.config;

public final class SoundstageAppearance {

    private SoundstageAppearance() {}

    private static final AppearanceSettings DEFAULTS = new AppearanceSettings() {};

    private static volatile AppearanceSettings settings = DEFAULTS;

    public static void install(AppearanceSettings platformSettings) {
        settings = platformSettings == null ? DEFAULTS : platformSettings;
    }

    public static float driverExcursionGain() {
        return settings.driverExcursionGain();
    }

    public static boolean trackChangeToast() {
        return settings.trackChangeToast();
    }

    public static boolean trackChangeToastArtwork() {
        return settings.trackChangeToastArtwork();
    }

    public static boolean showSubtitles() {
        return settings.showSubtitles();
    }

    public static void setShowSubtitles(boolean showSubtitles) {
        settings.setShowSubtitles(showSubtitles);
    }

    public static AppearanceSettings.SubtitleSize subtitleSize() {
        return settings.subtitleSize();
    }

    public static void setSubtitleSize(AppearanceSettings.SubtitleSize subtitleSize) {
        settings.setSubtitleSize(subtitleSize);
    }

    public static String shareDevice() {
        return settings.shareDevice();
    }

    public static void setShareDevice(String device) {
        settings.setShareDevice(device == null ? "" : device);
    }
}
