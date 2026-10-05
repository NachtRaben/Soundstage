package com.nachtraben.soundstage.core.video;

import java.util.Locale;
import java.util.Optional;

public enum StreamContainer {
    MP4("mp4"),
    WEBM("webm");

    private final String demuxer;

    StreamContainer(String demuxer) {
        this.demuxer = demuxer;
    }

    public String demuxer() {
        return demuxer;
    }

    public static Optional<StreamContainer> ofMime(String mimeType) {
        if (mimeType == null) return Optional.empty();
        return switch (mimeType.strip().toLowerCase(Locale.ROOT)) {
            case "video/mp4", "audio/mp4" -> Optional.of(MP4);
            case "video/webm", "audio/webm" -> Optional.of(WEBM);
            default -> Optional.empty();
        };
    }
}
