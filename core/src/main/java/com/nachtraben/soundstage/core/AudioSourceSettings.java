package com.nachtraben.soundstage.core;

import java.util.List;

public interface AudioSourceSettings {

    List<String> DEFAULT_YOUTUBE_CLIENTS = List.of(
            "IosWithThumbnail", "AndroidWithThumbnail", "AndroidVrWithThumbnail",
            "MusicWithThumbnail", "WebWithThumbnail");

    default String oauthRefreshToken() { return ""; }

    default String poToken() { return ""; }

    default String visitorData() { return ""; }

    default boolean allowYoutubeSearch() { return true; }

    default List<String> youtubeClients() {
        return DEFAULT_YOUTUBE_CLIENTS;
    }

    default boolean enableYoutube() { return true; }

    default boolean enableSoundCloud() { return true; }

    default boolean enableBandcamp() { return true; }

    default boolean enableVimeo() { return true; }

    default boolean enableTwitch() { return true; }

    default boolean enableHttp() { return false; }

    default boolean allowPrivateNetworkHosts() { return false; }
}
