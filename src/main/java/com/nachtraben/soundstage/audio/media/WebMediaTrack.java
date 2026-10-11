package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.SoundChoices;
import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.LoopbackRelay;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.SilentVideoProbe;
import com.nachtraben.soundstage.core.video.StillImage;
import com.nachtraben.soundstage.core.video.StillImageProbe;
import com.nachtraben.soundstage.core.video.WebMediaFetch;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

public final class WebMediaTrack extends HttpAudioTrack implements VideoCapableTrack {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    static final long SHORT_END_MARGIN_MILLIS = 2_000;

    static final Set<String> PICTURE_CONTAINERS = Set.of("mp4", "matroska/webm");

    static final String NEEDS_FFMPEG = "This server can't play that video.";

    private enum Waits {
        NOTHING,
        SCREEN,
        PLACE
    }

    private final WebMediaSourceManager sourceManager;
    private final FfmpegRuns runs;
    private final String describes;
    private volatile boolean awaitingScreen;
    private volatile boolean awaitingPlace;
    private volatile boolean throughFfmpeg;
    private volatile boolean copyPlaying;
    private final TrackAudioChoice audio;

    WebMediaTrack(AudioTrackInfo trackInfo, MediaContainerDescriptor containerTrackFactory,
                  WebMediaSourceManager sourceManager) {
        super(trackInfo, containerTrackFactory, sourceManager);
        this.sourceManager = sourceManager;
        this.describes = "web media file from " + host(trackInfo.identifier);
        this.runs = new FfmpegRuns(describes, StillImageProbe.type(containerTrackFactory) != null);
        this.audio = new TrackAudioChoice(trackInfo.identifier);
    }

    @Override
    public void setSoundChoices(SoundChoices choices) {
        audio.host(choices);
    }

    @Override
    public List<AudioChoice.Entry> audioEntries() {
        return audio.entries();
    }

    @Override
    public String audioKey() {
        return audio.key();
    }

    @Override
    public PictureRetake applyAudio() {
        if (!audio.reapply()) return PictureRetake.NONE;
        return copyPlaying ? PictureRetake.SEEK : PictureRetake.REPLAY;
    }

    @Override
    public void setVideoSink(VideoSink sink) {
        runs.setVideoSink(sink);
    }

    @Override
    public void setPictureTarget(PictureSize.Target target) {
        runs.setPictureTarget(target);
    }

    @Override
    public boolean surroundPending() {
        return runs.surroundPending();
    }

    boolean soundThroughFfmpeg() {
        return SilentVideoProbe.throughFfmpeg(getContainerTrackFactory());
    }

    @Override
    public boolean isStill() {
        return StillImageProbe.type(getContainerTrackFactory()) != null;
    }

    @Override
    public PictureRetake pictureRetake() {
        if (isStill() || soundThroughFfmpeg() || throughFfmpeg) {
            FfmpegRuns.Step step = isSeekable() ? runs.judgePace() : FfmpegRuns.Step.NONE;
            if (step == FfmpegRuns.Step.NO_PICTURE) runs.givePictureUp();
            boolean again = step != FfmpegRuns.Step.NONE || runs.picturePending() || runs.placeFreed();
            if (!again) return PictureRetake.NONE;
            return isStill() || !isSeekable() ? PictureRetake.REPLAY : PictureRetake.SEEK;
        }
        FfmpegRuns.Step step = runs.judgePace();
        if (step == FfmpegRuns.Step.SMALLER) return PictureRetake.SEEK;
        if (step == FfmpegRuns.Step.NO_PICTURE) {
            runs.giveCopyUp();
            return PictureRetake.NONE;
        }
        VideoSink sink = runs.videoSink();
        boolean showing = sink != null && sink.hasScreens() && sink.wantsPicture();
        if (awaitingScreen && showing) return PictureRetake.REPLAY;
        return awaitingPlace && showing && runs.takePlace() ? PictureRetake.REPLAY : PictureRetake.NONE;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (isStill()) {
            showStill(executor);
            return;
        }
        if (soundThroughFfmpeg()) {
            playThroughFfmpeg(executor);
            return;
        }
        VideoSink sink = runs.videoSink();
        URI url = fetchable(trackInfo.identifier);
        boolean pictureMayCome = sink != null && sink.wantsPicture() && SoundstageConfig.enableVideo()
                && !trackInfo.isStream && url != null && mayCarryPicture(getContainerTrackFactory());
        boolean screen = sink != null && sink.wantsPicture() && sink.hasScreens() && SoundstageConfig.enableVideo();
        boolean languages = audio.wanted() && !trackInfo.isStream && url != null
                && mayCarryPicture(getContainerTrackFactory()) && sourceManager.streaming().ffmpegAvailable();
        if (!screen && languages) {
            playLanguages(executor, pictureMayCome ? Waits.SCREEN : Waits.NOTHING);
            return;
        }
        if (!pictureMayCome) {
            playSoundOnly(executor);
            return;
        }
        WebMediaSourceManager.Streaming streaming = sourceManager.streaming();
        boolean ffmpeg = streaming.ffmpegAvailable();
        if (runs.isStopped()) return;
        if (!sink.hasScreens()) {
            if (!ffmpeg) {
                playSoundOnly(executor);
                return;
            }
            awaitingScreen = true;
            super.process(executor);
            return;
        }
        if (!ffmpeg) {
            LOGGER.info("Soundstage: {} plays with sound only: ffmpeg is not available.", describes);
            SoundOnlyFallbacks.report(trackInfo.identifier, "ffmpeg is not available");
            playSoundOnly(executor);
            return;
        }
        if (runs.pictureGivenUp()) {
            if (languages) {
                playLanguages(executor, Waits.NOTHING);
            } else {
                playSoundOnly(executor);
            }
            return;
        }
        if (!runs.takePlace()) {
            if (languages) {
                playLanguages(executor, Waits.PLACE);
            } else {
                playOwnPath(executor, Waits.PLACE);
            }
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
                audio.begin(media.audioChoices());
                copyPlaying = true;
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

    private void showStill(LocalAudioTrackExecutor executor) throws Exception {
        URI url = fetchable(trackInfo.identifier);
        if (url == null) {
            throw new FriendlyException("That URL cannot be played.", FriendlyException.Severity.COMMON, null);
        }
        WebMediaSourceManager.Streaming streaming = sourceManager.streaming();
        if (!streaming.ffmpegAvailable()) {
            throw new FriendlyException(MediaFolderSourceManager.IMAGES_NEED_FFMPEG, FriendlyException.Severity.COMMON,
                    null);
        }
        StillImage.Header header = sourceManager.readStill(url);
        if (header == null || header.type() != StillImageProbe.type(getContainerTrackFactory())) {
            throw new FriendlyException("That URL no longer serves the image it did.",
                    FriendlyException.Severity.COMMON, null);
        }
        if (!header.fits()) {
            throw new FriendlyException(StillImageProbe.TOO_LARGE, FriendlyException.Severity.COMMON, null);
        }
        if (runs.isStopped()) return;
        if (!runs.pictureMayCome()) runs.abandonPicture();
        LOGGER.debug("Soundstage: an image from {} ({}, {}x{}) shows on a silent soundtrack, read through a loopback "
                + "relay.", host(trackInfo.identifier), header.type(), header.width(), header.height());
        try (LoopbackRelay relay = sourceManager.relay(url, describes)) {
            runs.play(executor, new StillCopies(header,
                    copy -> streaming.still(relay.url(), describes, header.type(), copy),
                    () -> streaming.silence(describes, 0, MediaProbe.UNKNOWN_DURATION)));
        }
    }

    private void playThroughFfmpeg(LocalAudioTrackExecutor executor) throws Exception {
        playThroughFfmpeg(executor, false);
    }

    private boolean playThroughFfmpeg(LocalAudioTrackExecutor executor, boolean languages) throws Exception {
        URI url = fetchable(trackInfo.identifier);
        if (url == null) {
            throw new FriendlyException("That URL cannot be played.", FriendlyException.Severity.COMMON, null);
        }
        WebMediaSourceManager.Streaming streaming = sourceManager.streaming();
        if (!streaming.ffmpegAvailable()) {
            throw new FriendlyException(NEEDS_FFMPEG, FriendlyException.Severity.COMMON, null);
        }
        try (LoopbackRelay relay = sourceManager.relay(url, describes)) {
            MediaProbe media = streaming.probe(relay.url(), describes);
            if (runs.isStopped()) return true;
            if (media == null) {
                throw new FriendlyException(NEEDS_FFMPEG, FriendlyException.Severity.COMMON, null);
            }
            if (!media.hasVideo() && !media.hasAudio()) {
                throw new FriendlyException("That file has no picture or sound to play.",
                        FriendlyException.Severity.COMMON, null);
            }
            int entries = media.audioEntries().size();
            if (languages && entries < 2) {
                audio.begin(media.audioChoices());
                return false;
            }
            if (!media.hasVideo() || !runs.pictureMayCome()) runs.abandonPicture();
            String key = audio.begin(media.audioChoices());
            throughFfmpeg = true;
            copyPlaying = true;
            LOGGER.info("Soundstage: {} plays through ffmpeg{}, read through a loopback relay.", describes,
                    !media.hasAudio() ? " on a silent soundtrack" : entries > 1
                            ? " in " + AudioChoice.labelOf(key, media.audioChoices())
                            + ", one of its " + entries + " audio tracks" : ", which reads its sound");
            runs.play(executor, new Copies(relay.url(), media, media.hasVideo() && toneMap(streaming, media), true));
            return true;
        }
    }

    private void playLanguages(LocalAudioTrackExecutor executor, Waits waits) throws Exception {
        Throwable failure;
        boolean played;
        try {
            played = playThroughFfmpeg(executor, true);
            failure = null;
        } catch (Exception | LinkageError e) {
            played = false;
            failure = e;
        }
        if (played || runs.isStopped()) return;
        if (failure == null) {
            playOwnPath(executor, waits);
            return;
        }
        throughFfmpeg = false;
        copyPlaying = false;
        audio.withdraw();
        long resumeAt = runs.resumePosition(executor);
        String why = reasons(failure);
        LOGGER.warn("Soundstage: {} plays on in the audio track LavaPlayer reads, from {} ms: {}", describes, resumeAt,
                why);
        SoundOnlyFallbacks.report(trackInfo.identifier, why);
        if (resumeAt > 0) executor.setPosition(resumeAt);
        playOwnPath(executor, waits);
    }

    private void playOwnPath(LocalAudioTrackExecutor executor, Waits waits) throws Exception {
        if (waits == Waits.SCREEN) {
            awaitingScreen = true;
            super.process(executor);
            return;
        }
        if (waits == Waits.PLACE) {
            LOGGER.info("Soundstage: {} plays with sound only for now: this server has no room for another picture "
                    + "yet; its picture comes when there is.", describes);
            awaitingPlace = true;
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

    static URI fetchable(String identifier) {
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
        private final boolean pictureOptional;

        Copies(URI relay, MediaProbe media, boolean toneMap) {
            this(relay, media, toneMap, false);
        }

        Copies(URI relay, MediaProbe media, boolean toneMap, boolean pictureOptional) {
            this.relay = relay;
            this.media = media;
            this.toneMap = toneMap;
            this.pictureOptional = pictureOptional;
        }

        @Override
        public boolean hasSurroundTrack() {
            return media.withAudio(audio.key()).surroundSound().isPresent();
        }

        @Override
        public boolean pictureOptional() {
            return pictureOptional;
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, FfmpegRuns.Sizing sizing, boolean surround, boolean picture)
                throws IOException {
            try {
                WebMediaSourceManager.Streaming streaming = sourceManager.streaming();
                if (!picture && !media.hasAudio()) {
                    return streaming.silence(describes, startMillis, media.durationMillis());
                }
                MediaProbe chosen = media.withAudio(audio.key());
                MediaProbe probe = picture ? chosen : chosen.withoutPicture();
                return streaming.start(relay, describes, startMillis,
                        picture ? sizing.copy(media.pictureWidth(), media.videoHeight()) : null,
                        surround ? probe.withSurround() : probe, picture && toneMap);
            } catch (IOException e) {
                Ffmpeg.forget();
                throw new IOException("ffmpeg could not be started", e);
            }
        }

        @Override
        public boolean hasVideo() {
            return media.hasVideo();
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
