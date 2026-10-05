package com.nachtraben.soundstage.core.video;

import java.net.URI;
import java.util.Locale;

public final class YoutubeStreamUrls {

    private static final String DOMAIN = "googlevideo.com";

    private YoutubeStreamUrls() {}

    public static boolean isAcceptable(URI url) {
        if (url == null) return false;
        URI parsed;
        try {
            parsed = new URI(url.toASCIIString());
        } catch (java.net.URISyntaxException e) {
            return false;
        }
        if (!"https".equals(parsed.getScheme()) || parsed.isOpaque()) return false;
        if (parsed.getRawUserInfo() != null || parsed.getRawFragment() != null) return false;
        if (parsed.getPort() != -1 && parsed.getPort() != 443) return false;
        String host = parsed.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals(DOMAIN) || host.endsWith("." + DOMAIN);
    }
}
