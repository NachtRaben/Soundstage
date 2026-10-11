package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.SoundChoices;
import com.nachtraben.soundstage.audio.video.SubtitleSource;
import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.core.video.YoutubeAudioTracks;
import com.nachtraben.soundstage.core.video.YoutubeCaptions;
import com.nachtraben.soundstage.core.video.YoutubeStreams;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import dev.lavalink.youtube.track.YoutubeAudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class YoutubeVideoTrack extends YoutubeAudioTrack implements VideoCapableTrack {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    static final int MAX_RESOLUTIONS = 8;

    static final long SHORT_END_MARGIN_MILLIS = 2_000;

    static final String CERTIFICATE_REFUSED = "ffmpeg could not verify the certificate of YouTube's media "
            + "server; most likely its TLS library has no trusted certificates to check against, as some "
            + "older and minimal ffmpeg builds have none";

    private final YoutubeVideoSourceManager sourceManager;
    private final FfmpegRuns runs;
    private volatile boolean awaitingScreen;
    private volatile boolean awaitingPlace;
    private final TrackAudioChoice audio;

    YoutubeVideoTrack(AudioTrackInfo trackInfo, YoutubeVideoSourceManager sourceManager) {
        super(trackInfo, sourceManager);
        this.sourceManager = sourceManager;
        this.runs = new FfmpegRuns("YouTube video " + trackInfo.identifier);
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
        return audio.reapply() ? PictureRetake.REPLAY : PictureRetake.NONE;
    }

    private YoutubeAudioTracks.Track offer(List<YoutubeAudioTracks.Track> tracks) {
        if (tracks.size() < 2) return null;
        String key = audio.begin(YoutubeAudioTracks.choices(tracks));
        YoutubeAudioTracks.Track chosen = YoutubeAudioTracks.byKey(tracks, key).orElse(null);
        if (chosen == null || chosen.original()) return null;
        LOGGER.info("Soundstage: YouTube video {} plays its {} audio, one of {} tracks.", trackInfo.identifier,
                chosen.label(), tracks.size());
        return chosen;
    }

    private void playDub(LocalAudioTrackExecutor executor, YoutubeVideoSourceManager.Streaming streaming,
                         YoutubeAudioTracks.Track dub) throws Exception {
        YoutubeStreams.Streams streams;
        try {
            streams = streaming.resolveSound(sourceManager, trackInfo.identifier, getUserData(),
                    runs.surroundWanted(), dub);
        } catch (IOException e) {
            if (runs.isStopped()) return;
            LOGGER.warn("Soundstage: YouTube video {} plays its original audio: no client gave its {} audio as a "
                    + "stream of its own ({}).", trackInfo.identifier, dub.label(), e.getMessage());
            playSoundOnly(executor);
            return;
        }
        if (runs.isStopped()) return;
        LOGGER.info("Soundstage: YouTube video {} plays its {} audio through ffmpeg, from {}.", trackInfo.identifier,
                dub.label(), streams.client());
        Throwable failure;
        try {
            runs.play(executor, new Streamed(streams, dub, true));
            return;
        } catch (Exception | LinkageError e) {
            failure = e;
        }
        if (runs.isStopped()) return;
        long resumeAt = runs.resumePosition(executor);
        String why = reasons(failure);
        LOGGER.warn("Soundstage: YouTube video {} plays on in its original audio, from {} ms: {}",
                trackInfo.identifier, resumeAt, why);
        SoundOnlyFallbacks.report(trackInfo.identifier, why);
        if (resumeAt > 0) executor.setPosition(resumeAt);
        playSoundOnly(executor);
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

    @Override
    public PictureRetake pictureRetake() {
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
        YoutubeVideoSourceManager.Streaming streaming = sourceManager.streaming();
        if (trackInfo.isStream || !streaming.ffmpegAvailable()) {
            play(executor, streaming, null);
            return;
        }
        List<YoutubeAudioTracks.Track> known = streaming.audioTracksKept(sourceManager, trackInfo.identifier);
        if (known == null && audio.wanted()) {
            known = streaming.audioTracks(sourceManager, trackInfo.identifier, getUserData());
        }
        if (known != null) {
            play(executor, streaming, offer(known));
            return;
        }
        try (AutoCloseable watching = streaming.whenAudioTracksSeen(sourceManager, trackInfo.identifier, this::offer)) {
            play(executor, streaming, null);
        }
    }

    private void play(LocalAudioTrackExecutor executor, YoutubeVideoSourceManager.Streaming streaming,
                      YoutubeAudioTracks.Track dub) throws Exception {
        VideoSink sink = runs.videoSink();
        if (runs.isStopped()) return;
        if (sink == null || !sink.wantsPicture() || trackInfo.isStream) {
            playSoundOnly(executor, streaming, dub);
            return;
        }
        boolean ffmpeg = streaming.ffmpegAvailable();
        if (runs.isStopped()) return;
        if (!sink.hasScreens()) {
            if (!ffmpeg) {
                playSoundOnly(executor);
                return;
            }
            awaitingScreen = true;
            if (dub != null) {
                playDub(executor, streaming, dub);
                return;
            }
            super.process(executor);
            return;
        }
        if (!ffmpeg) {
            LOGGER.info("Soundstage: YouTube video {} plays with sound only: ffmpeg is not available.",
                    trackInfo.identifier);
            SoundOnlyFallbacks.report(trackInfo.identifier, "ffmpeg is not available");
            playSoundOnly(executor);
            return;
        }
        if (runs.pictureGivenUp()) {
            playSoundOnly(executor, streaming, dub);
            return;
        }
        if (!runs.takePlace()) {
            LOGGER.info("Soundstage: YouTube video {} plays with sound only for now: this server has no room for "
                    + "another picture yet; its picture comes when there is.", trackInfo.identifier);
            awaitingPlace = true;
            playSoundOnly(executor, streaming, dub);
            return;
        }
        YoutubeStreams.Streams streams;
        try {
            streams = streaming.resolve(sourceManager, trackInfo.identifier, runs.formatHeight(), getUserData(),
                    runs.surroundWanted(), dub);
        } catch (IOException e) {
            if (runs.isStopped()) return;
            LOGGER.warn("Soundstage: YouTube video {} plays with sound only: no client gave its picture and its "
                    + "sound as separate streams ({}).", trackInfo.identifier, e.getMessage());
            SoundOnlyFallbacks.report(trackInfo.identifier,
                    "no client gave its picture and its sound as separate streams (" + e.getMessage() + ")");
            playSoundOnly(executor, streaming, dub);
            return;
        }
        if (runs.isStopped()) return;
        LOGGER.info("Soundstage: YouTube video {} plays its picture through ffmpeg: {} lines from {}.",
                trackInfo.identifier, streams.videoHeight(), streams.client());
        startCaptions(streaming, streams);

        Throwable failure;
        try {
            runs.play(executor, new Streamed(streams, dub, false));
            return;
        } catch (Exception | LinkageError e) {
            failure = e;
        }
        if (runs.isStopped()) return;
        long resumeAt = runs.resumePosition(executor);
        String why = reasons(failure);
        LOGGER.warn("Soundstage: YouTube video {} plays on with sound only, from {} ms: {}", trackInfo.identifier,
                resumeAt, why);
        SoundOnlyFallbacks.report(trackInfo.identifier, why);
        runs.abandonPicture();
        if (resumeAt > 0) executor.setPosition(resumeAt);
        super.process(executor);
    }

    private void startCaptions(YoutubeVideoSourceManager.Streaming streaming, YoutubeStreams.Streams streams) {
        VideoSink sink = runs.videoSink();
        if (sink == null) return;
        List<YoutubeCaptions.Track> tracks = streams.captions();
        if (tracks.isEmpty()) return;
        List<SubtitleChoice.Option> options = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            options.add(new SubtitleChoice.Option(captionKey(tracks.get(i)), tracks.get(i).name()));
        }
        sink.subtitleSource(new SubtitleSource() {
            @Override
            public List<SubtitleChoice.Option> options() {
                return options;
            }

            @Override
            public String autoKey(String playerLanguage) {
                return YoutubeCaptions.choose(tracks, playerLanguage).map(YoutubeVideoTrack::captionKey).orElse(null);
            }

            @Override
            public void start(String key, Consumer<CueTrack> deliver,
                              BooleanSupplier wanted) {
                tracks.stream().filter(track -> captionKey(track).equals(key)).findFirst().ifPresent(track ->
                        FfmpegMediaTrack.SUBTITLE_EXECUTOR.execute(() -> {
                            if (runs.isStopped() || !wanted.getAsBoolean()) return;
                            CueTrack cues = streaming.captions(sourceManager, track, trackInfo.identifier);
                            if (!cues.cues().isEmpty() && !runs.isStopped() && wanted.getAsBoolean()) {
                                deliver.accept(cues);
                            }
                        }));
            }
        });
    }

    private static String captionKey(YoutubeCaptions.Track track) {
        return "yt:" + track.languageCode() + (track.autoGenerated() ? ":asr" : "");
    }

    private void playSoundOnly(LocalAudioTrackExecutor executor, YoutubeVideoSourceManager.Streaming streaming,
                               YoutubeAudioTracks.Track dub) throws Exception {
        if (dub == null) {
            playSoundOnly(executor);
            return;
        }
        runs.abandonPicture();
        playDub(executor, streaming, dub);
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
        return new YoutubeVideoTrack(trackInfo, sourceManager);
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

    private final class Streamed implements FfmpegRuns.Source {
        private final String name = "YouTube video " + trackInfo.identifier;
        private final YoutubeVideoSourceManager.Streaming streaming = sourceManager.streaming();
        private final YoutubeAudioTracks.Track dub;
        private final boolean soundOnly;
        private YoutubeStreams.Streams streams;
        private int soundAtResolution;
        private int resolutions = 1;

        Streamed(YoutubeStreams.Streams streams, YoutubeAudioTracks.Track dub, boolean soundOnly) {
            this.streams = streams;
            this.dub = dub;
            this.soundOnly = soundOnly;
        }

        @Override
        public boolean pictureOptional() {
            return soundOnly;
        }

        private YoutubeStreams.Streams resolveStreams(int formatHeight) throws IOException {
            return soundOnly
                    ? streaming.resolveSound(sourceManager, trackInfo.identifier, getUserData(),
                            runs.surroundWanted(), dub)
                    : streaming.resolve(sourceManager, trackInfo.identifier, formatHeight, getUserData(),
                            runs.surroundWanted(), dub);
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, FfmpegRuns.Sizing sizing, boolean surround, boolean picture)
                throws IOException {
            if (soundOnly) {
                try {
                    return streaming.startSound(streams, name, startMillis);
                } catch (IOException e) {
                    Ffmpeg.forget();
                    throw new IOException("ffmpeg could not be started", e);
                }
            }
            int formatHeight = sizing.formatHeight();
            if (formatHeight != streams.chosenFor() && resolutions < MAX_RESOLUTIONS) {
                resolutions++;
                try {
                    streams = resolveStreams(formatHeight);
                    soundAtResolution = runs.copiesWithSound();
                } catch (IOException e) {
                    LOGGER.debug("Soundstage: YouTube video {} keeps its {}-line stream: {}", trackInfo.identifier,
                            streams.videoHeight(), e.getMessage());
                }
            }
            PictureSize.Copy size = sizing.copy(PictureSize.sixteenByNineWidth(streams.videoHeight()),
                    streams.videoHeight());
            try {
                return streaming.start(streams, name, startMillis, size);
            } catch (IOException e) {
                Ffmpeg.forget();
                throw new IOException("ffmpeg could not be started", e);
            }
        }

        @Override
        public boolean hasSurroundTrack() {
            return streams.surround();
        }

        @Override
        public boolean hasVideo() {
            return !soundOnly;
        }

        @Override
        public FfmpegRuns.Next ended(FfmpegRuns.RunEnd end) throws IOException {
            boolean failed = end.exit() != 0 && end.exit() != -1;
            if (end.certificateRefused() && !end.producedSound()) {
                throw new IOException(CERTIFICATE_REFUSED);
            }
            if (end.producedSound()) {
                if (!stopsShort(end.soundEndMillis())) {
                    if (failed) {
                        LOGGER.warn("Soundstage: ffmpeg exited {} at the end of YouTube video {}.", end.exit(),
                                trackInfo.identifier);
                    }
                    return FfmpegRuns.Next.END;
                }
                resolveAgain("its sound stopped at " + end.soundEndMillis() + " ms of " + trackInfo.length
                        + " (ffmpeg exit " + end.exit() + ")");
                return FfmpegRuns.Next.CONTINUE;
            }
            if (!failed && !stopsShort(end.startMillis())) {
                return FfmpegRuns.Next.END;
            }
            String why = "a copy from " + end.startMillis() + " ms played nothing (ffmpeg exit " + end.exit() + ")";
            if (runs.copiesWithSound() == soundAtResolution) {
                throw new IOException(why + ", from streams that had not played");
            }
            resolveAgain(why);
            return FfmpegRuns.Next.AGAIN;
        }

        private boolean stopsShort(long atMillis) {
            long length = trackInfo.length;
            return length != Units.DURATION_MS_UNKNOWN && atMillis < length - SHORT_END_MARGIN_MILLIS;
        }

        private void resolveAgain(String why) throws IOException {
            if (resolutions >= MAX_RESOLUTIONS) {
                throw new IOException(why + ", and its streams were already resolved " + resolutions + " times");
            }
            resolutions++;
            streams = resolveStreams(runs.formatHeight());
            soundAtResolution = runs.copiesWithSound();
            LOGGER.info("Soundstage: YouTube video {}: {}; its streams were resolved again, from {}.",
                    trackInfo.identifier, why, streams.client());
        }
    }
}
