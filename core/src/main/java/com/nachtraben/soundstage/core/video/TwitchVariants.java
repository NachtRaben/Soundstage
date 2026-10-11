package com.nachtraben.soundstage.core.video;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TwitchVariants {

    static final double HOUSE_MAX_FRAME_RATE = 30.5;

    public record Variant(String name, int width, int height, double frameRate, String codecs, String uri) {
        public boolean hasPicture() {
            return height > 0;
        }

        public boolean isH264() {
            return codec("avc1", "avc3") != null;
        }

        public boolean isHevc() {
            return codec("hvc1", "hev1") != null;
        }

        public boolean isReadable() {
            return hasPicture() && (isH264() || isHevc());
        }

        public int h264Profile() {
            String codec = codec("avc1", "avc3");
            if (codec == null || codec.length() < 7 || codec.charAt(4) != '.') return -1;
            try {
                return Integer.parseInt(codec.substring(5, 7), 16);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        public boolean looksHouse() {
            return h264Profile() == HouseFormat.MAIN_PROFILE && atMostThirty();
        }

        boolean atMostThirty() {
            return frameRate > 0 && frameRate <= HOUSE_MAX_FRAME_RATE;
        }

        private String codec(String... names) {
            for (String codec : codecs.split(",")) {
                String trimmed = codec.strip().toLowerCase(Locale.ROOT);
                for (String name : names) {
                    if (trimmed.startsWith(name)) return trimmed;
                }
            }
            return null;
        }
    }

    private TwitchVariants() {}

    public static List<Variant> parse(String[] lines) {
        List<Variant> variants = new ArrayList<>();
        String streamInfo = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                streamInfo = line.substring("#EXT-X-STREAM-INF:".length());
            } else if (!line.startsWith("#") && streamInfo != null) {
                variants.add(variant(streamInfo, line));
                streamInfo = null;
            }
        }
        return variants;
    }

    public static Optional<Variant> choose(List<Variant> variants, int maxHeight, boolean reencodable) {
        if (!reencodable) {
            return variants.stream()
                    .filter(v -> v.isReadable() && v.looksHouse() && v.height() <= maxHeight)
                    .max(Comparator.<Variant>comparingInt(Variant::height).thenComparingDouble(v -> -v.frameRate()));
        }
        List<Variant> readable = variants.stream().filter(Variant::isReadable).toList();
        if (readable.isEmpty()) return Optional.empty();
        List<Variant> fitting = readable.stream().filter(v -> v.height() <= maxHeight).toList();
        int height = fitting.isEmpty()
                ? readable.stream().mapToInt(Variant::height).min().orElseThrow()
                : fitting.stream().mapToInt(Variant::height).max().orElseThrow();
        return readable.stream()
                .filter(v -> v.height() == height)
                .max(Comparator.<Variant>comparingInt(v -> v.looksHouse() ? 1 : 0)
                        .thenComparingInt(v -> v.atMostThirty() ? 1 : 0)
                        .thenComparingInt(v -> v.isH264() ? 1 : 0)
                        .thenComparingDouble(v -> -v.frameRate()));
    }

    private static Variant variant(String attributes, String uri) {
        String name = "";
        int width = 0;
        int height = 0;
        double frameRate = 0;
        String codecs = "";
        for (String[] attribute : attributes(attributes)) {
            String value = attribute[1];
            switch (attribute[0]) {
                case "VIDEO" -> name = value;
                case "CODECS" -> codecs = value;
                case "FRAME-RATE" -> frameRate = parseDouble(value);
                case "RESOLUTION" -> {
                    int x = value.indexOf('x');
                    if (x > 0) {
                        width = parseInt(value.substring(0, x));
                        height = parseInt(value.substring(x + 1));
                        if (width <= 0 || height <= 0) width = height = 0;
                    }
                }
                default -> {
                }
            }
        }
        return new Variant(name, width, height, frameRate, codecs, uri);
    }

    private static List<String[]> attributes(String text) {
        List<String[]> pairs = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int equals = text.indexOf('=', i);
            if (equals < 0) break;
            String key = text.substring(i, equals).strip();
            int at = equals + 1;
            String value;
            if (at < text.length() && text.charAt(at) == '"') {
                int close = text.indexOf('"', at + 1);
                if (close < 0) close = text.length();
                value = text.substring(at + 1, close);
                at = close + 1;
            } else {
                int comma = text.indexOf(',', at);
                if (comma < 0) comma = text.length();
                value = text.substring(at, comma).strip();
                at = comma;
            }
            pairs.add(new String[]{key, value});
            int comma = text.indexOf(',', at);
            if (comma < 0) break;
            i = comma + 1;
        }
        return pairs;
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String text) {
        try {
            double value = Double.parseDouble(text.strip());
            return Double.isFinite(value) && value > 0 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
