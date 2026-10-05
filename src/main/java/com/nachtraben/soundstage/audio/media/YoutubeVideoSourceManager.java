package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.YoutubeCaptions;
import com.nachtraben.soundstage.core.video.YoutubeStreams;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.track.YoutubeAudioTrack;

import java.io.DataInput;
import java.io.IOException;

public final class YoutubeVideoSourceManager extends YoutubeAudioSourceManager {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    interface Streaming {
        boolean ffmpegAvailable() throws InterruptedException;

        YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                       Object userData) throws IOException;

        default YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                               Object userData, boolean surround) throws IOException {
            return resolve(source, videoId, targetHeight, userData);
        }

        Ffmpeg.Transcode start(YoutubeStreams.Streams streams, String name, long startMillis, int maxHeight)
                throws IOException;

        default CueTrack captions(YoutubeAudioSourceManager source, YoutubeCaptions.Track track, String videoId) {
            return CueTrack.EMPTY;
        }
    }

    private static final Streaming REAL = new Streaming() {
        @Override
        public boolean ffmpegAvailable() throws InterruptedException {
            return Ffmpeg.available();
        }

        @Override
        public YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                              Object userData) throws IOException {
            return YoutubeStreams.resolve(source, videoId, targetHeight, userData);
        }

        @Override
        public YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                              Object userData, boolean surround) throws IOException {
            return YoutubeStreams.resolve(source, videoId, targetHeight, userData, surround);
        }

        @Override
        public Ffmpeg.Transcode start(YoutubeStreams.Streams streams, String name, long startMillis, int maxHeight)
                throws IOException {
            return Ffmpeg.transcode(streams, name, startMillis, maxHeight);
        }

        @Override
        public CueTrack captions(YoutubeAudioSourceManager source, YoutubeCaptions.Track track, String videoId) {
            try {
                CueTrack cues = YoutubeCaptions.fetch(source, track);
                LOGGER.info("Soundstage: YouTube video {} shows captions \"{}\" ({}): {} cues.", videoId, track.name(),
                        track.languageCode(), cues.cues().size());
                return cues;
            } catch (IOException | RuntimeException e) {
                LOGGER.info("Soundstage: YouTube video {} shows no captions: \"{}\" could not be fetched ({}).",
                        videoId, track.name(), e.getMessage());
                return CueTrack.EMPTY;
            }
        }
    };

    private final Streaming streaming;

    public YoutubeVideoSourceManager(boolean allowSearch, Client... clients) {
        this(allowSearch, clients, REAL);
    }

    YoutubeVideoSourceManager(boolean allowSearch, Client[] clients, Streaming streaming) {
        super(allowSearch, clients);
        this.streaming = streaming;
    }

    Streaming streaming() {
        return streaming;
    }

    @Override
    public YoutubeAudioTrack buildAudioTrack(AudioTrackInfo trackInfo) {
        return new YoutubeVideoTrack(trackInfo, this);
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new YoutubeVideoTrack(trackInfo, this);
    }
}
