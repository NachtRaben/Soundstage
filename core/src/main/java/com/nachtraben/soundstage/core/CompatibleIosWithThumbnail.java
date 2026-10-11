package com.nachtraben.soundstage.core;

import com.nachtraben.soundstage.core.video.YoutubeAudioTracks;
import com.nachtraben.soundstage.core.video.YoutubeCaptions;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.IosWithThumbnail;
import dev.lavalink.youtube.clients.skeleton.Client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class CompatibleIosWithThumbnail extends IosWithThumbnail implements YoutubeCaptions.Source,
        YoutubeAudioTracks.Source {

    private static final int REMEMBERED_VIDEOS = 64;

    private final Map<String, List<YoutubeCaptions.Track>> captions = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<YoutubeCaptions.Track>> eldest) {
                    return size() > REMEMBERED_VIDEOS;
                }
            });

    private final Map<String, List<YoutubeAudioTracks.Track>> audioTracks =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, List<YoutubeAudioTracks.Track>> eldest) {
                    return size() > REMEMBERED_VIDEOS;
                }
            });

    private final Map<String, List<Consumer<List<YoutubeAudioTracks.Track>>>>
            watchers = new ConcurrentHashMap<>();

    @Override
    public List<YoutubeAudioTracks.Track> audioTracksOf(String videoId) {
        return audioTracks.get(videoId);
    }

    @Override
    public AutoCloseable whenSeen(String videoId,
                                  Consumer<List<YoutubeAudioTracks.Track>> seen) {
        watchers.computeIfAbsent(videoId, id -> new CopyOnWriteArrayList<>()).add(seen);
        return () -> watchers.computeIfPresent(videoId, (id, list) -> {
            list.remove(seen);
            return list.isEmpty() ? null : list;
        });
    }

    @Override
    protected JsonBrowser loadTrackInfoFromInnertube(YoutubeAudioSourceManager source, HttpInterface httpInterface,
                                                     String videoId, Client.PlayabilityStatus status,
                                                     boolean validatePlayabilityStatus)
            throws CannotBeLoaded, IOException {
        JsonBrowser response = super.loadTrackInfoFromInnertube(source, httpInterface, videoId, status,
                validatePlayabilityStatus);
        if (response != null && videoId != null) {
            captions.put(videoId, YoutubeCaptions.tracksIn(response));
            List<YoutubeAudioTracks.Track> tracks =
                    YoutubeAudioTracks.tracksIn(response);
            audioTracks.put(videoId, tracks);
            List<Consumer<List<YoutubeAudioTracks.Track>>> waiting =
                    watchers.get(videoId);
            if (waiting != null) {
                for (Consumer<List<YoutubeAudioTracks.Track>> seen
                        : waiting) {
                    seen.accept(tracks);
                }
            }
        }
        return response;
    }

    @Override
    public List<YoutubeCaptions.Track> captionsOf(String videoId) {
        List<YoutubeCaptions.Track> tracks = captions.get(videoId);
        return tracks == null ? List.of() : tracks;
    }

    @Override
    protected List<AudioTrack> extractSearchResults(YoutubeAudioSourceManager source,
                                                    JsonBrowser json) {
        List<AudioTrack> tracks = super.extractSearchResults(source, json);
        if (!tracks.isEmpty()) return tracks;

        List<AudioTrack> compatible = new ArrayList<>();
        for (SearchEntry entry : extractElementSearchEntries(json)) {
            AudioTrackInfo info = new AudioTrackInfo(
                    entry.title(), entry.author(), entry.durationMillis(), entry.videoId(), false,
                    WATCH_URL + entry.videoId(), entry.thumbnailUrl(), null);
            compatible.add(source.buildAudioTrack(info));
        }
        return compatible;
    }

    static List<SearchEntry> extractElementSearchEntries(JsonBrowser json) {
        List<SearchEntry> entries = new ArrayList<>();
        for (JsonBrowser section : json.get("contents")
                .get("sectionListRenderer").get("contents").values()) {
            for (JsonBrowser item : section.get("itemSectionRenderer").get("contents").values()) {
                JsonBrowser data = item.get("elementRenderer").get("newElement").get("type")
                        .get("componentType").get("model").get("compactVideoModel")
                        .get("compactVideoData");
                JsonBrowser video = data.get("videoData");
                if (video.isNull()) continue;

                String videoId = firstText(data,
                        new String[] {"onTap", "innertubeCommand",
                                "coWatchWatchEndpointWrapperCommand", "watchEndpoint",
                                "watchEndpoint", "videoId"},
                        new String[] {"onTap", "innertubeCommand", "watchEndpoint", "videoId"});
                String title = video.get("metadata").get("title").text();
                String author = video.get("metadata").get("byline").text();
                String durationText = video.get("thumbnail").get("timestampText").text();
                if (videoId == null || title == null || durationText == null) continue;
                if (author == null || author.isBlank()) author = "Unknown artist";

                String thumbnail = null;
                for (JsonBrowser source : video.get("thumbnail").get("image")
                        .get("sources").values()) {
                    String candidate = source.get("url").text();
                    if (candidate != null) thumbnail = candidate;
                }
                entries.add(new SearchEntry(videoId, title, author,
                        DataFormatTools.durationTextToMillis(durationText), thumbnail));
            }
        }
        return entries;
    }

    private static String firstText(JsonBrowser root, String[]... paths) {
        for (String[] path : paths) {
            JsonBrowser value = root;
            for (String part : path) value = value.get(part);
            String text = value.text();
            if (text != null) return text;
        }
        return null;
    }

    record SearchEntry(String videoId, String title, String author, long durationMillis,
                       String thumbnailUrl) {}
}
