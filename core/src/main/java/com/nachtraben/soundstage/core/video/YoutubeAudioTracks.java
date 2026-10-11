package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import dev.lavalink.youtube.track.format.StreamFormat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class YoutubeAudioTracks {

    public static final String DESCRIPTIVE = "descriptive";

    public record Track(String id, String language, String label, String content, boolean original) {
        public String key() {
            return "yt:" + id;
        }

        public boolean descriptive() {
            return DESCRIPTIVE.equals(content);
        }
    }

    public record Tags(String language, String content) {}

    public interface Source {
        List<Track> audioTracksOf(String videoId);

        AutoCloseable whenSeen(String videoId, Consumer<List<Track>> seen);
    }

    private YoutubeAudioTracks() {}

    public static List<Track> tracksIn(JsonBrowser playerResponse) {
        Map<String, Track> tracks = new LinkedHashMap<>();
        try {
            for (JsonBrowser format : playerResponse.get("streamingData").get("adaptiveFormats").values()) {
                JsonBrowser track = format.get("audioTrack");
                String id = track.get("id").text();
                if (id == null || tracks.containsKey(id)) continue;
                Tags tags = tagsOf(format.get("url").text());
                if (tags == null) continue;
                String label = track.get("displayName").text();
                tracks.put(id, new Track(id, tags.language(), label == null ? id : label, tags.content(),
                        track.get("audioIsDefault").asBoolean(false)));
            }
        } catch (RuntimeException e) {
            return List.of();
        }
        List<Track> ordered = new ArrayList<>();
        for (Track track : tracks.values()) {
            if (track.original()) ordered.add(track);
        }
        for (Track track : tracks.values()) {
            if (!track.original()) ordered.add(track);
        }
        return List.copyOf(ordered);
    }

    public static Tags tagsOf(StreamFormat format) {
        URI url = format.getUrl();
        return url == null ? null : tagsOf(url.getRawQuery() == null ? null : "?" + url.getRawQuery());
    }

    static Tags tagsOf(String url) {
        if (url == null) return null;
        int query = url.indexOf('?');
        if (query < 0) return null;
        for (String parameter : url.substring(query + 1).split("&")) {
            if (!parameter.startsWith("xtags=")) continue;
            String value = URLDecoder.decode(parameter.substring("xtags=".length()), StandardCharsets.UTF_8);
            String language = null, content = null;
            for (String pair : value.split(":")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) continue;
                String name = pair.substring(0, equals);
                if (name.equals("lang")) language = pair.substring(equals + 1);
                if (name.equals("acont")) content = pair.substring(equals + 1);
            }
            return language == null || content == null ? null : new Tags(language, content);
        }
        return null;
    }

    public static boolean carries(StreamFormat format, Track track) {
        Tags tags = tagsOf(format);
        return tags != null && tags.language().equals(track.language()) && tags.content().equals(track.content());
    }

    public static List<AudioChoice.Entry> choices(List<Track> tracks) {
        List<AudioChoice.Entry> choices = new ArrayList<>(tracks.size());
        for (Track track : tracks) {
            String language = AudioChoice.languageTag(track.language());
            choices.add(new AudioChoice.Entry(track.key(), track.label(), language,
                    track.descriptive() || language == null, track.original()));
        }
        return choices;
    }

    public static Optional<Track> byKey(List<Track> tracks, String key) {
        for (Track track : tracks) {
            if (track.key().equals(key)) return Optional.of(track);
        }
        return Optional.empty();
    }
}
