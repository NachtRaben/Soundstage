package com.nachtraben.soundstage.core.video;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class StillImage {

    public static final int MAX_SIDE = 8192;

    public static final long MAX_PIXELS = (long) MAX_SIDE * MAX_SIDE;

    public static final int MAX_HEADER_BYTES = 1 << 20;

    private static final int[] PNG_SIGNATURE = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public enum Type {
        PNG("png_pipe"),
        JPEG("jpeg_pipe");

        public final String demuxer;

        Type(String demuxer) {
            this.demuxer = demuxer;
        }
    }

    public record Header(Type type, int width, int height) {
        public boolean fits() {
            return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE;
        }
    }

    private StillImage() {}

    public static boolean isImageName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    public static Type ofContentType(String contentType) {
        if (contentType == null) return null;
        int semicolon = contentType.indexOf(';');
        String type = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip()
                .toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/png", "image/x-png" -> Type.PNG;
            case "image/jpeg", "image/jpg", "image/pjpeg" -> Type.JPEG;
            default -> null;
        };
    }

    public static Header read(InputStream in) throws IOException {
        Bounded stream = new Bounded(in);
        int first = stream.read();
        if (first == 0xFF) {
            return stream.read() == 0xD8 ? jpeg(stream) : null;
        }
        if (first != PNG_SIGNATURE[0]) return null;
        for (int i = 1; i < PNG_SIGNATURE.length; i++) {
            if (stream.read() != PNG_SIGNATURE[i]) return null;
        }
        stream.int32();
        if (stream.read() != 'I' || stream.read() != 'H' || stream.read() != 'D' || stream.read() != 'R') {
            return new Header(Type.PNG, 0, 0);
        }
        long width = stream.int32() & 0xFFFFFFFFL;
        long height = stream.int32() & 0xFFFFFFFFL;
        return new Header(Type.PNG, (int) Math.min(Integer.MAX_VALUE, width),
                (int) Math.min(Integer.MAX_VALUE, height));
    }

    private static Header jpeg(Bounded stream) throws IOException {
        while (true) {
            int marker = stream.read();
            if (marker < 0) return new Header(Type.JPEG, 0, 0);
            if (marker != 0xFF) continue;
            while (marker == 0xFF) marker = stream.read();
            if (marker < 0 || marker == 0xD9 || marker == 0xDA) return new Header(Type.JPEG, 0, 0);
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) continue;
            int length = stream.int16();
            if (length < 2) return new Header(Type.JPEG, 0, 0);
            if (isStartOfFrame(marker)) {
                stream.read();
                int height = stream.int16();
                int width = stream.int16();
                return new Header(Type.JPEG, width, height);
            }
            stream.skip(length - 2);
        }
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private static final class Bounded {
        private final InputStream in;
        private int consumed;

        Bounded(InputStream in) {
            this.in = in;
        }

        int read() throws IOException {
            if (consumed >= MAX_HEADER_BYTES) return -1;
            int value = in.read();
            if (value >= 0) consumed++;
            return value;
        }

        int int16() throws IOException {
            int high = read(), low = read();
            if (high < 0 || low < 0) return -1;
            return (high << 8) | low;
        }

        int int32() throws IOException {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int b = read();
                if (b < 0) return -1;
                value = (value << 8) | b;
            }
            return value;
        }

        void skip(int count) throws IOException {
            for (int i = 0; i < count; i++) {
                if (read() < 0) return;
            }
        }
    }
}
