package com.nachtraben.soundstage.core;

public final class YoutubeThumbnail {

    private YoutubeThumbnail() {
    }

    private static final String PATTERN = "https://i.ytimg.com/vi/%s/hqdefault.jpg";

    private static final int ID_LENGTH = 11;

    public static String forTrack(String identifier, String uri) {
        String id = looksLikeVideoId(identifier) ? identifier : videoIdFromUri(uri);
        return id == null ? "" : String.format(PATTERN, id);
    }

    public static String videoIdFromUri(String uri) {
        if (uri == null || uri.isEmpty()) return null;

        int shortIndex = uri.indexOf("youtu.be/");
        if (shortIndex != -1) {
            return trimId(uri.substring(shortIndex + "youtu.be/".length()));
        }
        for (String path : new String[]{"/shorts/", "/embed/"}) {
            int index = uri.indexOf(path);
            if (index != -1) {
                return trimId(uri.substring(index + path.length()));
            }
        }
        int query = uri.indexOf("?");
        if (query != -1) {
            for (String pair : uri.substring(query + 1).split("&")) {
                if (pair.startsWith("v=")) {
                    return trimId(pair.substring(2));
                }
            }
        }
        return null;
    }

    private static String trimId(String raw) {
        int end = raw.length();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '?' || c == '&' || c == '/' || c == '#') {
                end = i;
                break;
            }
        }
        String id = raw.substring(0, end);
        return looksLikeVideoId(id) ? id : null;
    }

    public static boolean looksLikeVideoId(String candidate) {
        if (candidate == null || candidate.length() != ID_LENGTH) return false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }
}
