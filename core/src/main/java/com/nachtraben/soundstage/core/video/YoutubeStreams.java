package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.http.YoutubeOauth2Handler;
import dev.lavalink.youtube.track.format.StreamFormat;
import dev.lavalink.youtube.track.format.TrackFormats;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class YoutubeStreams {

    public record Streams(URI video, StreamContainer videoContainer, URI audio, StreamContainer audioContainer,
                          int videoHeight, int chosenFor, String client, int audioChannels,
                          List<YoutubeCaptions.Track> captions) {
        public Streams {
            captions = List.copyOf(captions);
        }

        public Streams(URI video, StreamContainer videoContainer, URI audio, StreamContainer audioContainer,
                       int videoHeight, int chosenFor, String client, int audioChannels) {
            this(video, videoContainer, audio, audioContainer, videoHeight, chosenFor, client, audioChannels, List.of());
        }

        public Streams(URI video, StreamContainer videoContainer, URI audio, StreamContainer audioContainer,
                       int videoHeight, int chosenFor, String client) {
            this(video, videoContainer, audio, audioContainer, videoHeight, chosenFor, client, 2);
        }

        public boolean surround() {
            return audioChannels >= 6;
        }
    }

    private YoutubeStreams() {}

    public static Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                  Object userData) throws IOException {
        return resolve(source, videoId, targetHeight, userData, false);
    }

    public static Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                  Object userData, boolean surround) throws IOException {
        List<String> failures = new ArrayList<>();
        try (HttpInterface http = source.getInterface()) {
            injectToken(http, userData);
            for (Client client : source.getClients()) {
                if (!client.supportsFormatLoading()) continue;
                http.getContext().setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, client.supportsOAuth());
                String name = client.getIdentifier();
                try {
                    TrackFormats formats = client.loadFormats(source, http, videoId);
                    if (formats == null) {
                        failures.add(name + ": no formats");
                        continue;
                    }
                    Optional<YoutubeVideoFormats.Choice> choice =
                            YoutubeVideoFormats.choose(formats.getFormats(), targetHeight, surround);
                    if (choice.isEmpty()) {
                        failures.add(name + ": no picture-only and sound-only pair among "
                                + formats.getFormats().size() + " formats");
                        continue;
                    }
                    Optional<StreamContainer> videoContainer = containerOf(choice.get().video());
                    Optional<StreamContainer> audioContainer = containerOf(choice.get().audio());
                    if (videoContainer.isEmpty() || audioContainer.isEmpty()) {
                        failures.add(name + ": a stream in a container other than MP4 or WebM");
                        continue;
                    }
                    URI video = playbackUrl(source, http, client, formats, choice.get().video());
                    URI audio = playbackUrl(source, http, client, formats, choice.get().audio());
                    if (!YoutubeStreamUrls.isAcceptable(video) || !YoutubeStreamUrls.isAcceptable(audio)) {
                        failures.add(name + ": a stream not on https://*.googlevideo.com");
                        continue;
                    }
                    return new Streams(video, videoContainer.get(), audio, audioContainer.get(),
                            choice.get().videoHeight(), targetHeight, name,
                            (int) choice.get().audio().getAudioChannels(), captionsKept(source, videoId));
                } catch (CannotBeLoaded e) {
                    failures.add(name + ": " + describe(e.getCause() != null ? e.getCause() : e));
                } catch (Exception e) {
                    failures.add(name + ": " + describe(e));
                }
            }
        }
        throw new IOException(failures.isEmpty() ? "no client of this YouTube source loads formats"
                : String.join("; ", failures));
    }

    private static List<YoutubeCaptions.Track> captionsKept(YoutubeAudioSourceManager source, String videoId) {
        for (Client client : source.getClients()) {
            if (client instanceof YoutubeCaptions.Source kept) {
                List<YoutubeCaptions.Track> tracks = kept.captionsOf(videoId);
                if (!tracks.isEmpty()) return tracks;
            }
        }
        return List.of();
    }

    private static URI playbackUrl(YoutubeAudioSourceManager source, HttpInterface http, Client client,
                                   TrackFormats formats, StreamFormat format) throws Exception {
        URI url = format.getUrl();
        if (client.requirePlayerScript()) {
            URI deciphered = source.getCipherManager().resolveFormatUrl(http, formats.getPlayerScriptUrl(), format);
            url = client.transformPlaybackUri(format.getUrl(), deciphered);
        }
        return url;
    }

    private static Optional<StreamContainer> containerOf(StreamFormat format) {
        return StreamContainer.ofMime(format.getType().getMimeType());
    }

    private static void injectToken(HttpInterface http, Object userData) {
        if (userData == null) return;
        try {
            JsonBrowser json = JsonBrowser.parse(userData.toString());
            if (!json.get("oauth-token").isNull()) {
                http.getContext().setAttribute(YoutubeOauth2Handler.OAUTH_INJECT_CONTEXT_ATTRIBUTE,
                        json.get("oauth-token").text());
            }
        } catch (IOException ignored) {
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : " (" + FfmpegCommand.withoutUrls(message) + ")");
    }
}
