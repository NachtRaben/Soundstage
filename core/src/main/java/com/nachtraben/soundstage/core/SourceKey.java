package com.nachtraben.soundstage.core;

import java.util.Locale;

public final class SourceKey {

    private SourceKey() {}

    public static final String OTHER = "other";

    public static final String UNKNOWN = "unknown";

    public static String forUrl(String url) {
        if (url == null || url.isBlank()) {
            return UNKNOWN;
        }

        String s = url.trim().toLowerCase(Locale.ROOT);

        if (s.startsWith("ytsearch:") || s.startsWith("ytmsearch:")) {
            return "youtube";
        }
        if (s.startsWith("scsearch:")) {
            return "soundcloud";
        }
        if (s.startsWith("media:")) {
            return "media";
        }

        String host = hostOf(s);
        if (host.isEmpty()) {
            return UNKNOWN;
        }

        if (endsWithLabel(host, "youtube.com") || endsWithLabel(host, "youtu.be")
                || endsWithLabel(host, "youtube-nocookie.com")) {
            return "youtube";
        }
        if (endsWithLabel(host, "soundcloud.com")) {
            return "soundcloud";
        }
        if (endsWithLabel(host, "bandcamp.com")) {
            return "bandcamp";
        }
        if (endsWithLabel(host, "twitch.tv")) {
            return "twitch";
        }
        if (endsWithLabel(host, "vimeo.com")) {
            return "vimeo";
        }
        if (endsWithLabel(host, "twitter.com") || endsWithLabel(host, "x.com")) {
            return "twitter";
        }
        return OTHER;
    }

    private static String hostOf(String url) {
        int start = url.indexOf("://");
        start = start < 0 ? 0 : start + 3;

        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }

        String authority = url.substring(start, end);

        int at = authority.lastIndexOf('@');
        String hostAndPort = at >= 0 ? authority.substring(at + 1) : authority;

        int colon = hostAndPort.indexOf(':');
        return colon >= 0 ? hostAndPort.substring(0, colon) : hostAndPort;
    }

    private static boolean endsWithLabel(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
