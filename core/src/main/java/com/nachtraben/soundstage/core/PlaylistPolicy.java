package com.nachtraben.soundstage.core;

public final class PlaylistPolicy {

    private PlaylistPolicy() {
    }

    public enum Decision {
        SELECTED_ONLY,
        ALL
    }

    public static boolean isGeneratedMix(String url) {
        if (url == null) return false;
        if (url.contains("start_radio=1")) return true;
        String list = queryParameter(url, "list");
        return list != null && list.startsWith("RD");
    }

    public static boolean namesAList(String url) {
        return queryParameter(url, "list") != null;
    }

    public static Decision decide(String url, boolean isSearchResult, boolean hasSelectedTrack) {
        if (isSearchResult) return Decision.SELECTED_ONLY;

        if (!hasSelectedTrack) return Decision.ALL;

        if (!namesAList(url)) return Decision.SELECTED_ONLY;

        if (isGeneratedMix(url)) return Decision.SELECTED_ONLY;

        return Decision.ALL;
    }

    public static int[] order(int size, int selectedIndex) {
        if (size <= 0) return new int[0];
        int start = (selectedIndex < 0 || selectedIndex >= size) ? 0 : selectedIndex;
        int[] order = new int[size];
        for (int i = 0; i < size; i++) {
            order[i] = (start + i) % size;
        }
        return order;
    }

    private static String queryParameter(String url, String name) {
        if (url == null) return null;
        int query = url.indexOf('?');
        if (query == -1) return null;
        String rest = url.substring(query + 1);
        int fragment = rest.indexOf('#');
        if (fragment != -1) rest = rest.substring(0, fragment);
        for (String pair : rest.split("&")) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            if (pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
