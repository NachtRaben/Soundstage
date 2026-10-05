package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.LoopbackRelay;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.nachtraben.soundstage.core.video.WebMediaFetch;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

public final class WebMediaTrack extends HttpAudioTrack implements VideoCapableTrack {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    static final long SHORT_END_MARGIN_MILLIS = 2_000;

    static final Set<String> PICTURE_CONTAINERS = Set.of("mp4", "matroska/webm");

    private final WebMediaSourceManager sourceManager;
    private final FfmpegRuns runs;
    private final String describes;

    WebMediaTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerTrackFactory,
                  WebMediaSourceManager sourceManager) {
        super(trackInfo, containerTrackFactory, sourceManager);
        this.sourceManager = sourceManager;
        this.describes = "web media file from " + host(trackInfo.identifier);
        this.runs = new FfmpegRuns(describes);
    }

    @Override
    public void setVideoSink(VideoSink sink) {
        runs.setVideoSink(sink);
    }

    @Override
    public void setTargetHeight(int height) {
        runs.setTargetHeight(height);
    }

    @Override
    public boolean surroundPending() {
        return runs.surroundPending();
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        VideoSink sink = runs.videoSink();
        URI url = fetchable(trackInfo.identifier);
        if (sink == null || !sink.hasScreens() || !sink.wantsPicture() || !SoundstageConfig.enableVideo() || trackInfo.isStream
                || url == null || !mayCarryPicture(getContainerTrackFactory())) {
            playSoundOnly(executor);
            return;
        }
        WebMediaSourceManager.Streaming streaming = sourceManager.streaming();
        boolean ffmpeg = streaming.ffmpegAvailable();
        if (runs.isStopped()) return;
        if (!ffmpeg) {
            LOGGER.info("Soundstage: {} plays with sound only: ffmpeg is not available.", describes);
            SoundOnlyFallbacks.report(trackInfo.identifier, "ffmpeg is not available");
            playSoundOnly(executor);
            return;
        }

        Throwable failure = null;
        String soundOnly = null;
        LoopbackRelay relay = null;
        try {
            relay = sourceManager.relay(url, describes);
            MediaProbe media = streaming.probe(relay.url(), describes);
            if (runs.isStopped()) return;
            if (media == null) {
                soundOnly = "ffprobe is not available";
            } else if (!media.hasVideo() || !media.hasAudio()) {
                LOGGER.info("Soundstage: {} has {}, so it plays as an ordinary web track.", describes,
                        media.hasVideo() ? "no sound" : "no picture");
            } else {
                LOGGER.info("Soundstage: {} plays its picture through ffmpeg, read through a loopback relay.",
                        describes);
                runs.play(executor, new Copies(relay.url(), media, toneMap(streaming, media)));
                return;
            }
        } catch (Exception | LinkageError e) {
            failure = e;
        } finally {
            if (relay != null) relay.close();
        }
        if (runs.isStopped()) return;
        if (failure != null) {
            long resumeAt = runs.resumePosition(executor);
            String why = reasons(failure);
            LOGGER.warn("Soundstage: {} plays on with sound only, from {} ms: {}", describes, resumeAt, why);
            SoundOnlyFallbacks.report(trackInfo.identifier, why);
            if (resumeAt > 0) executor.setPosition(resumeAt);
        } else if (soundOnly != null) {
            LOGGER.info("Soundstage: {} plays with sound only: {}.", describes, soundOnly);
            SoundOnlyFallbacks.report(trackInfo.identifier, soundOnly);
        }
        playSoundOnly(executor);
    }

    private void playSoundOnly(LocalAudioTrackExecutor executor) throws Exception {
        runs.abandonPicture();
        super.process(executor);
    }

    @Override
    public void setPosition(long position) {
        runs.seek(position, super::setPosition);
    }

    @Override
    public void stop() {
        runs.stop(super::stop);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new WebMediaTrack(trackInfo, getContainerTrackFactory(), sourceManager);
    }

    static boolean mayCarryPicture(MediaContainerDescriptor container) {
        return container != null && container.probe != null
                && PICTURE_CONTAINERS.contains(container.probe.getName());
    }

    private static URI fetchable(String identifier) {
        try {
            URI url = new URI(identifier);
            return WebMediaFetch.isFetchable(url) ? url : null;
        } catch (URISyntaxException | NullPointerException e) {
            return null;
        }
    }

    private static String host(String identifier) {
        try {
            String host = new URI(identifier).getHost();
            return host == null ? "an unnamed host" : host;
        } catch (URISyntaxException | NullPointerException e) {
            return "an unnamed host";
        }
    }

    private boolean toneMap(WebMediaSourceManager.Streaming streaming, MediaProbe media) throws InterruptedException {
        if (!media.colour().isHdr()) return false;
        List<String> missing = streaming.missingToneMapFilters();
        if (missing == null || !missing.isEmpty()) {
            LOGGER.warn("Soundstage: {} is HDR ({}), but this ffmpeg cannot tone-map it, so its picture will look "
                    + "washed out.", describes, media.colour().describe());
            return false;
        }
        return true;
    }

    private static String reasons(Throwable failure) {
        Throwable first = failure instanceof FriendlyException && failure.getCause() != null
                ? failure.getCause() : failure;
        StringBuilder text = new StringBuilder();
        for (Throwable at = first; at != null && text.length() < 600; at = at.getCause()) {
            if (!text.isEmpty()) text.append(" <- ");
            text.append(at.getMessage() == null ? at.getClass().getSimpleName() : at.getMessage());
            if (at.getCause() == at) break;
        }
        return FfmpegCommand.withoutUrls(text.toString());
    }

    private final class Copies implements FfmpegRuns.Source {
        private final URI relay;
        private final MediaProbe media;
        private final boolean toneMap;

        Copies(URI relay, MediaProbe media, boolean toneMap) {
            this.relay = relay;
            this.media = media;
            this.toneMap = toneMap;
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, int maxHeight) throws IOException {
            return start(startMillis, maxHeight, false);
        }

        @Override
        public boolean hasSurroundTrack() {
            return media.surroundSound().isPresent();
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, int maxHeight, boolean surround) throws IOException {
            try {
                return sourceManager.streaming().start(relay, describes, startMillis, maxHeight,
                        surround ? media.withSurround() : media, toneMap);
            } catch (IOException e) {
                Ffmpeg.forget();
                throw new IOException("ffmpeg could not be started", e);
            }
        }

        @Override
        public boolean hasVideo() {
            return true;
        }

        @Override
        public FfmpegRuns.Next ended(FfmpegRuns.RunEnd end) throws IOException {
            boolean failed = end.exit() != 0 && end.exit() != -1;
            if (end.producedSound()) {
                if (!stopsShort(end.soundEndMillis())) {
                    if (failed) LOGGER.warn("Soundstage: ffmpeg exited {} at the end of {}.", end.exit(), describes);
                    return FfmpegRuns.Next.END;
                }
                throw new IOException("its sound stopped at " + end.soundEndMillis() + " ms of " + trackInfo.length
                        + " (ffmpeg exit " + end.exit() + ")");
            }
            if (!failed && !stopsShort(end.startMillis())) {
                return FfmpegRuns.Next.END;
            }
            throw new IOException("a copy from " + end.startMillis() + " ms played nothing (ffmpeg exit "
                    + end.exit() + ")");
        }

        private boolean stopsShort(long atMillis) {
            long length = trackInfo.length;
            return length != Units.DURATION_MS_UNKNOWN && atMillis < length - SHORT_END_MARGIN_MILLIS;
        }
    }
}
