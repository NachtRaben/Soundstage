package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.ScreenedHttpSource;
import com.nachtraben.soundstage.core.video.LoopbackRelay;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.StillImage;
import com.nachtraben.soundstage.core.video.WebMediaCommand;
import com.nachtraben.soundstage.core.video.WebMediaFetch;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.DnsResolver;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public final class WebMediaSourceManager extends ScreenedHttpSource {

    interface Streaming {
        boolean ffmpegAvailable() throws InterruptedException;

        MediaProbe probe(URI relay, String name) throws InterruptedException;

        List<String> missingToneMapFilters() throws InterruptedException;

        Ffmpeg.Transcode start(URI relay, String name, long startMillis,
                               PictureSize.Copy picture, MediaProbe probe,
                               boolean toneMap) throws IOException;

        default Ffmpeg.Transcode still(URI relay, String name, StillImage.Type type,
                                       PictureSize.Copy picture)
                throws IOException {
            List<String> command = WebMediaCommand.still(Ffmpeg.path(), relay, type, picture);
            return Ffmpeg.adopt(new ProcessBuilder(command).start(), name);
        }

        default Ffmpeg.Transcode silence(String name, long startMillis, long durationMillis) throws IOException {
            return Ffmpeg.silence(name, startMillis, durationMillis);
        }
    }

    private static final Streaming REAL = new Streaming() {
        @Override
        public boolean ffmpegAvailable() throws InterruptedException {
            return Ffmpeg.available();
        }

        @Override
        public MediaProbe probe(URI relay, String name) throws InterruptedException {
            return Ffmpeg.probe(ffprobe -> WebMediaCommand.probe(ffprobe, relay), name);
        }

        @Override
        public List<String> missingToneMapFilters() throws InterruptedException {
            return Ffmpeg.missingToneMapFilters();
        }

        @Override
        public Ffmpeg.Transcode start(URI relay, String name, long startMillis,
                                      PictureSize.Copy picture, MediaProbe probe,
                                      boolean toneMap) throws IOException {
            List<String> command = WebMediaCommand.transcode(Ffmpeg.path(), relay, startMillis, picture, probe,
                    toneMap);
            return Ffmpeg.adopt(new ProcessBuilder(command).start(), name);
        }
    };

    private final Streaming streaming;

    public WebMediaSourceManager(boolean admitted, DnsResolver screen) {
        this(admitted, screen, REAL);
    }

    WebMediaSourceManager(boolean admitted, DnsResolver screen, Streaming streaming) {
        super(admitted, screen);
        this.streaming = streaming;
    }

    Streaming streaming() {
        return streaming;
    }

    LoopbackRelay relay(URI url, String name) throws IOException {
        return LoopbackRelay.open(from -> WebMediaFetch.open(this::getHttpInterface, url, from), name);
    }

    StillImage.Header readStill(URI url) throws IOException {
        WebMediaFetch.Body body = WebMediaFetch.open(this::getHttpInterface, url, 0);
        try {
            return StillImage.read(new BufferedInputStream(body.stream()));
        } finally {
            body.close();
        }
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        AudioItem item = super.loadItem(manager, reference);
        if (item instanceof WebMediaTrack track && track.soundThroughFfmpeg()
                && track.getInfo().length == Units.DURATION_MS_UNKNOWN) {
            return measured(track);
        }
        if (item instanceof WebMediaTrack track && track.isStill()) {
            try {
                if (!streaming.ffmpegAvailable()) {
                    throw new FriendlyException(MediaFolderSourceManager.IMAGES_NEED_FFMPEG, COMMON, null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FriendlyException("Loading that image was interrupted.", COMMON, e);
            }
        }
        return item;
    }

    private AudioTrack measured(WebMediaTrack track) {
        AudioTrackInfo info = track.getInfo();
        URI url = WebMediaTrack.fetchable(info.identifier);
        if (url == null) throw new FriendlyException("That URL cannot be played.", COMMON, null);
        String name = "web media file from " + url.getHost();
        try {
            if (!streaming.ffmpegAvailable()) {
                throw new FriendlyException(WebMediaTrack.NEEDS_FFMPEG, COMMON, null);
            }
            MediaProbe media;
            try (LoopbackRelay relay = relay(url, name)) {
                media = streaming.probe(relay.url(), name);
            }
            if (media == null) {
                throw new FriendlyException(WebMediaTrack.NEEDS_FFMPEG, COMMON, null);
            }
            if (!media.hasVideo() && !media.hasAudio()) {
                throw new FriendlyException("That file has no picture or sound to play.", COMMON, null);
            }
            long length = media.durationMillis() == MediaProbe.UNKNOWN_DURATION
                    ? Units.DURATION_MS_UNKNOWN : media.durationMillis();
            AudioTrackInfo measured = new AudioTrackInfo(info.title, info.author, length, info.identifier,
                    length == Units.DURATION_MS_UNKNOWN, info.uri, info.artworkUrl, info.isrc);
            return new WebMediaTrack(measured, track.getContainerTrackFactory(), this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FriendlyException("Loading that video was interrupted.", COMMON, e);
        } catch (IOException e) {
            throw new FriendlyException("That video could not be read.", COMMON, e);
        }
    }

    @Override
    protected AudioTrack createTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerDescriptor) {
        return new WebMediaTrack(trackInfo, containerDescriptor, this);
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        AudioTrack decoded = super.decodeTrack(trackInfo, input);
        return decoded instanceof HttpAudioTrack http
                ? new WebMediaTrack(trackInfo, http.getContainerTrackFactory(), this) : decoded;
    }
}
