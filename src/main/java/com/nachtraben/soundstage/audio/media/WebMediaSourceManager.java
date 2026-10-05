package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.LoopbackRelay;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.nachtraben.soundstage.core.video.WebMediaCommand;
import com.nachtraben.soundstage.core.video.WebMediaFetch;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.nachtraben.soundstage.core.ScreenedHttpSource;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.DnsResolver;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public final class WebMediaSourceManager extends ScreenedHttpSource {

    interface Streaming {
        boolean ffmpegAvailable() throws InterruptedException;

        MediaProbe probe(URI relay, String name) throws InterruptedException;

        List<String> missingToneMapFilters() throws InterruptedException;

        Ffmpeg.Transcode start(URI relay, String name, long startMillis, int maxHeight, MediaProbe probe,
                               boolean toneMap) throws IOException;
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
        public Ffmpeg.Transcode start(URI relay, String name, long startMillis, int maxHeight, MediaProbe probe,
                                      boolean toneMap) throws IOException {
            List<String> command = WebMediaCommand.transcode(Ffmpeg.path(), relay, startMillis, maxHeight, probe,
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
