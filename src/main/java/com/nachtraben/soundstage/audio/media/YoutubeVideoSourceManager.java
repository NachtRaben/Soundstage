package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.YoutubeAudioTracks;
import com.nachtraben.soundstage.core.video.YoutubeCaptions;
import com.nachtraben.soundstage.core.video.YoutubeStreams;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.track.YoutubeAudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public final class YoutubeVideoSourceManager extends YoutubeAudioSourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    interface Streaming {
        boolean ffmpegAvailable() throws InterruptedException;

        YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                       Object userData) throws IOException;

        default YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                               Object userData, boolean surround) throws IOException {
            return resolve(source, videoId, targetHeight, userData);
        }

        Ffmpeg.Transcode start(YoutubeStreams.Streams streams, String name, long startMillis,
                               PictureSize.Copy picture) throws IOException;

        default List<YoutubeAudioTracks.Track> audioTracks(
                YoutubeAudioSourceManager source, String videoId, Object userData) {
            return List.of();
        }

        default List<YoutubeAudioTracks.Track> audioTracksKept(
                YoutubeAudioSourceManager source, String videoId) {
            return null;
        }

        default AutoCloseable whenAudioTracksSeen(YoutubeAudioSourceManager source, String videoId,
                Consumer<List<YoutubeAudioTracks.Track>> seen) {
            return () -> {
            };
        }

        default YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                               Object userData, boolean surround,
                                               YoutubeAudioTracks.Track track)
                throws IOException {
            if (track == null) return resolve(source, videoId, targetHeight, userData, surround);
            throw new IOException("this source of streams cannot choose an audio track");
        }

        default YoutubeStreams.Streams resolveSound(YoutubeAudioSourceManager source, String videoId, Object userData,
                                                    boolean surround,
                                                    YoutubeAudioTracks.Track track)
                throws IOException {
            throw new IOException("this source of streams has no sound-only copies");
        }

        default Ffmpeg.Transcode startSound(YoutubeStreams.Streams streams, String name, long startMillis)
                throws IOException {
            throw new IOException("this source of streams has no sound-only copies");
        }

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
        public Ffmpeg.Transcode start(YoutubeStreams.Streams streams, String name, long startMillis,
                                      PictureSize.Copy picture) throws IOException {
            return Ffmpeg.transcode(streams, name, startMillis, picture);
        }

        @Override
        public List<YoutubeAudioTracks.Track> audioTracks(
                YoutubeAudioSourceManager source, String videoId, Object userData) {
            return YoutubeStreams.audioTracks(source, videoId, userData);
        }

        @Override
        public List<YoutubeAudioTracks.Track> audioTracksKept(
                YoutubeAudioSourceManager source, String videoId) {
            return YoutubeStreams.audioTracksKept(source, videoId);
        }

        @Override
        public AutoCloseable whenAudioTracksSeen(YoutubeAudioSourceManager source, String videoId,
                Consumer<List<YoutubeAudioTracks.Track>> seen) {
            return YoutubeStreams.whenAudioTracksSeen(source, videoId, seen);
        }

        @Override
        public YoutubeStreams.Streams resolve(YoutubeAudioSourceManager source, String videoId, int targetHeight,
                                              Object userData, boolean surround,
                                              YoutubeAudioTracks.Track track)
                throws IOException {
            return YoutubeStreams.resolve(source, videoId, targetHeight, userData, surround, track);
        }

        @Override
        public YoutubeStreams.Streams resolveSound(YoutubeAudioSourceManager source, String videoId, Object userData,
                                                   boolean surround,
                                                   YoutubeAudioTracks.Track track)
                throws IOException {
            return YoutubeStreams.resolveSound(source, videoId, userData, surround, track);
        }

        @Override
        public Ffmpeg.Transcode startSound(YoutubeStreams.Streams streams, String name, long startMillis)
                throws IOException {
            return Ffmpeg.transcodeSound(streams, name, startMillis);
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
