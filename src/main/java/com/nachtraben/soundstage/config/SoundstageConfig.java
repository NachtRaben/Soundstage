package com.nachtraben.soundstage.config;

import com.nachtraben.soundstage.core.UrlPolicy;

import java.util.List;

public final class SoundstageConfig {

    private SoundstageConfig() {}

    private static final SoundstageSettings DEFAULTS = new SoundstageSettings() {};

    private static volatile SoundstageSettings settings = DEFAULTS;

    public static void install(SoundstageSettings platformSettings) {
        settings = platformSettings == null ? DEFAULTS : platformSettings;
    }

    public static SoundstageSettings settings() {
        return settings;
    }

    private static UrlPolicy filterCache = UrlPolicy.of(List.of(), List.of());
    private static List<? extends String> allowSource = null;
    private static List<? extends String> denySource = null;

    private static synchronized UrlPolicy filter() {
        List<? extends String> allow = settings.urlAllowPatterns();
        List<? extends String> deny = settings.urlDenyPatterns();
        if (allow != allowSource || deny != denySource) {
            filterCache = UrlPolicy.of(allow, deny);
            allowSource = allow;
            denySource = deny;
        }
        return filterCache;
    }

    public static boolean isUrlAllowed(String url) {
        return filter().allows(url);
    }

    public static boolean enableYoutube() { return settings.enableYoutube(); }
    public static boolean enableSoundCloud() { return settings.enableSoundCloud(); }
    public static boolean enableBandcamp() { return settings.enableBandcamp(); }
    public static boolean enableVimeo() { return settings.enableVimeo(); }
    public static boolean enableTwitch() { return settings.enableTwitch(); }
    public static boolean enableHttp() { return settings.enableHttp(); }
    public static boolean allowPrivateNetworkHosts() { return settings.allowPrivateNetworkHosts(); }
    public static int maxPlaylistTracks() { return settings.maxPlaylistTracks(); }
    public static int maxQueueSize() { return settings.maxQueueSize(); }
    public static int maxPlayingReceivers() { return settings.maxPlayingReceivers(); }
    public static boolean enableLiveInput() { return settings.enableLiveInput(); }
    public static boolean enableVideo() { return settings.enableVideo(); }
    public static boolean enableMediaFolder() { return settings.enableMediaFolder(); }
    public static String ffmpegPath() { return settings.ffmpegPath(); }
    public static boolean downloadFfmpeg() { return settings.downloadFfmpeg(); }
    public static int maxVideoHeight() { return settings.maxVideoHeight(); }
    public static boolean allowYoutubeSearch() { return settings.allowYoutubeSearch(); }

    public static String getOauthRefreshToken() { return settings.oauthRefreshToken(); }
    public static String getPoToken() { return settings.poToken(); }
    public static String getVisitorData() { return settings.visitorData(); }

    public static void setOauthRefreshToken(String token) {
        settings.setOauthRefreshToken(token);
    }

    public static void load() {
    }
}
