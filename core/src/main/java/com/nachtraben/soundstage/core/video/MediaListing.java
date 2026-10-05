package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MediaListing {

    public static final int MAX_ENTRIES = 2000;

    private MediaListing() {}

    public static Result cap(List<String> names, int max) {
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        boolean truncated = sorted.size() > max;
        List<String> capped = truncated ? List.copyOf(sorted.subList(0, max)) : List.copyOf(sorted);
        return new Result(capped, truncated);
    }

    public record Result(List<String> names, boolean truncated) {}

    public static List<String> filter(List<String> names, String query) {
        if (query == null || query.isBlank()) return names;
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).contains(needle)) out.add(name);
        }
        return out;
    }
}
