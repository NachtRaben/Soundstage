package com.nachtraben.soundstage.audio.media;

import java.util.List;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.audio.video.SubtitleSource;
import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.YoutubeCaptions;
import com.nachtraben.soundstage.core.video.YoutubeStreams;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import dev.lavalink.youtube.track.YoutubeAudioTrack;

import java.io.IOException;

public final class YoutubeVideoTrack extends YoutubeAudioTrack implements VideoCapableTrack {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    static final int MAX_RESOLUTIONS = 8;

    static final long SHORT_END_MARGIN_MILLIS = 2_000;

    static final String CERTIFICATE_REFUSED = "ffmpeg could not verify the certificate of YouTube's media "
            + "server; most likely its TLS library has no trusted certificates to check against, as some "
            + "older and minimal ffmpeg builds have none";

    private final YoutubeVideoSourceManager sourceManager;
    private final FfmpegRuns runs;

    YoutubeVideoTrack(AudioTrackInfo trackInfo, YoutubeVideoSourceManager sourceManager) {
        super(trackInfo, sourceManager);
        this.sourceManager = sourceManager;
        this.runs = new FfmpegRuns("YouTube video " + trackInfo.identifier);
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
        if (sink == null || !sink.hasScreens() || !sink.wantsPicture() || trackInfo.isStream) {
            playSoundOnly(executor);
            return;
        }
        YoutubeVideoSourceManager.Streaming streaming = sourceManager.streaming();
        boolean ffmpeg = streaming.ffmpegAvailable();
        if (runs.isStopped()) return;
        if (!ffmpeg) {
            LOGGER.info("Soundstage: YouTube video {} plays with sound only: ffmpeg is not available.",
                    trackInfo.identifier);
            SoundOnlyFallbacks.report(trackInfo.identifier, "ffmpeg is not available");
            playSoundOnly(executor);
            return;
        }
        YoutubeStreams.Streams streams;
        try {
            streams = streaming.resolve(sourceManager, trackInfo.identifier, runs.pictureHeight(), getUserData(),
                    runs.surroundWanted());
        } catch (IOException e) {
            if (runs.isStopped()) return;
            LOGGER.warn("Soundstage: YouTube video {} plays with sound only: no client gave its picture and its "
                    + "sound as separate streams ({}).", trackInfo.identifier, e.getMessage());
            SoundOnlyFallbacks.report(trackInfo.identifier,
                    "no client gave its picture and its sound as separate streams (" + e.getMessage() + ")");
            playSoundOnly(executor);
            return;
        }
        if (runs.isStopped()) return;
        LOGGER.info("Soundstage: YouTube video {} plays its picture through ffmpeg: {} lines from {}.",
                trackInfo.identifier, streams.videoHeight(), streams.client());
        startCaptions(streaming, streams);

        Throwable failure;
        try {
            runs.play(executor, new Streamed(streams));
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
        List<SubtitleChoice.Option> options = new java.util.ArrayList<>();
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
            public void start(String key, java.util.function.Consumer<CueTrack> deliver,
                              java.util.function.BooleanSupplier wanted) {
                tracks.stream().filter(track -> captionKey(track).equals(key)).findFirst().ifPresent(track ->
                        FfmpegMediaTrack.SUBTITLE_EXECUTOR.execute(() -> {
                            if (runs.isStopped() || !wanted.getAsBoolean()) return;
                            CueTrack cues = streaming.captions(sourceManager, track, trackInfo.identifier);
                            if (!cues.cues().isEmpty() && !runs.isStopped() && wanted.getAsBoolean()) deliver.accept(cues);
                        }));
            }
        });
    }

    private static String captionKey(YoutubeCaptions.Track track) {
        return "yt:" + track.languageCode() + (track.autoGenerated() ? ":asr" : "");
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
        private YoutubeStreams.Streams streams;
        private int soundAtResolution;
        private int resolutions = 1;

        Streamed(YoutubeStreams.Streams streams) {
            this.streams = streams;
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, int maxHeight) throws IOException {
            if (maxHeight != streams.chosenFor() && resolutions < MAX_RESOLUTIONS) {
                resolutions++;
                try {
                    streams = streaming.resolve(sourceManager, trackInfo.identifier, maxHeight, getUserData(),
                            runs.surroundWanted());
                    soundAtResolution = runs.copiesWithSound();
                } catch (IOException e) {
                    LOGGER.debug("Soundstage: YouTube video {} keeps its {}-line stream: {}", trackInfo.identifier,
                            streams.videoHeight(), e.getMessage());
                }
            }
            try {
                return streaming.start(streams, name, startMillis, maxHeight);
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
            return true;
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
            streams = streaming.resolve(sourceManager, trackInfo.identifier, runs.pictureHeight(), getUserData(),
                    runs.surroundWanted());
            soundAtResolution = runs.copiesWithSound();
            LOGGER.info("Soundstage: YouTube video {}: {}; its streams were resolved again, from {}.",
                    trackInfo.identifier, why, streams.client());
        }
    }
}
