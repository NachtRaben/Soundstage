package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.audio.video.SubtitleSource;
import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public final class FfmpegMediaTrack extends DelegatedAudioTrack implements VideoCapableTrack {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    static final ExecutorService SUBTITLE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Soundstage-Subtitles");
        thread.setDaemon(true);
        return thread;
    });

    private final String name;
    private final MediaFolderSourceManager sourceManager;
    private final FfmpegRuns runs;

    FfmpegMediaTrack(AudioTrackInfo info, String name, MediaFolderSourceManager sourceManager) {
        super(info);
        this.name = name;
        this.sourceManager = sourceManager;
        this.runs = new FfmpegRuns("media file " + name);
    }

    String name() {
        return name;
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
        if (!SoundstageConfig.enableMediaFolder()) {
            throw new FriendlyException("Media files are turned off on this server.", COMMON, null);
        }
        Path file = MediaFolder.resolve(name).orElse(null);
        if (file == null) {
            LOGGER.warn("Soundstage: media file {} is no longer in the media folder.", name);
            throw new FriendlyException("That media file is no longer in the media folder.", COMMON, null);
        }
        MediaProbe media = sourceManager.probe(file, name);
        if (media == null) {
            SoundOnlyFallbacks.report(trackInfo.identifier, "ffmpeg or ffprobe is not available");
            runs.abandonPicture();
            processDelegate(sourceManager.readSoundOnly(file, name), executor);
            return;
        }
        if (!media.hasAudio()) {
            throw new FriendlyException("That media file has no sound to play.", COMMON, null);
        }
        media.sound().ifPresent(sound -> LOGGER.info("Soundstage: media file {} plays audio stream {}: {} {}, {}{}.",
                name, sound.index(), sound.channels(), sound.channels() == 1 ? "channel" : "channels",
                sound.language() == null ? "no language" : sound.language(),
                sound.channels() == 2 ? "" : ", mixed to stereo"));
        MediaProbe transcodeProbe = soundOnlyWithoutSink(media);
        if (!transcodeProbe.hasVideo()) runs.abandonPicture();
        startSubtitles(file, media);
        runs.play(executor, new Copies(file, transcodeProbe, toneMap(transcodeProbe)));
    }

    private void startSubtitles(Path file, MediaProbe media) {
        if (media.hasOnlyImageSubtitles()) {
            LOGGER.info("Soundstage: media file {} has subtitles, but they are images, which this mod does not "
                    + "show.", name);
        }
        if (!SoundstageConfig.enableVideo()) return;
        VideoSink sink = runs.videoSink();
        if (sink == null) return;
        List<MediaProbe.Subtitle> text = media.textSubtitles();
        if (text.isEmpty()) {
            if (media.hasVideo() && !media.hasOnlyImageSubtitles()) {
                LOGGER.info("Soundstage: media file {} has no text subtitles.", name);
            }
            return;
        }
        List<SubtitleChoice.Option> options = text.stream()
                .map(sub -> new SubtitleChoice.Option(subtitleKey(sub),
                        SubtitleChoice.mediaLabel(sub.language(), sub.title(), sub.forced())))
                .toList();
        sink.subtitleSource(new SubtitleSource() {
            @Override
            public List<SubtitleChoice.Option> options() {
                return options;
            }

            @Override
            public String autoKey(String playerLanguage) {
                return media.subtitle(playerLanguage).map(FfmpegMediaTrack::subtitleKey).orElse(null);
            }

            @Override
            public void start(String key, java.util.function.Consumer<CueTrack> deliver,
                              java.util.function.BooleanSupplier wanted) {
                text.stream().filter(sub -> subtitleKey(sub).equals(key)).findFirst().ifPresent(subtitle -> {
                    LOGGER.info("Soundstage: media file {} shows subtitle stream {} ({}, {}).", name,
                            subtitle.index(), subtitle.codec(), subtitle.language() == null
                                    || subtitle.language().isEmpty() ? "no language" : subtitle.language());
                    SUBTITLE_EXECUTOR.execute(() -> Ffmpeg.subtitles(file, name, subtitle,
                            () -> !runs.isStopped() && wanted.getAsBoolean(), deliver));
                });
            }
        });
    }

    private static String subtitleKey(MediaProbe.Subtitle subtitle) {
        return "s:" + subtitle.index();
    }

    private MediaProbe soundOnlyWithoutSink(MediaProbe probe) {
        VideoSink sink = runs.videoSink();
        if (!probe.hasVideo() || (SoundstageConfig.enableVideo() && sink != null && sink.wantsPicture())) return probe;
        return new MediaProbe(probe.durationMillis(), probe.hasAudio(), false, 0, 0, probe.sounds());
    }

    private boolean toneMap(MediaProbe media) throws InterruptedException {
        if (!media.hasVideo() || !media.colour().isHdr()) return false;
        String colour = media.colour().describe();
        List<String> missing = sourceManager.missingToneMapFilters();
        if (missing == null) {
            LOGGER.warn("Soundstage: media file {} is HDR ({}), but ffmpeg's list of filters could not be read, so "
                    + "its picture will look washed out this time.", name, colour);
            return false;
        }
        if (!missing.isEmpty()) {
            LOGGER.warn("Soundstage: media file {} is HDR ({}), but this ffmpeg has no {} filter, so its picture "
                    + "will look washed out. An ffmpeg built with libzimg has it.", name, colour,
                    String.join(" or ", missing));
            return false;
        }
        if (media.colour().colourWillBeOff()) {
            LOGGER.warn("Soundstage: media file {} is Dolby Vision profile {} with no HDR10 base layer; it plays "
                    + "tone-mapped as HDR10, so its brightness is right but its colours will be off.", name,
                    media.colour().dolbyVisionProfile());
        } else {
            LOGGER.info("Soundstage: media file {} is HDR ({}); its picture is tone-mapped to SDR.", name, colour);
        }
        return true;
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
        return new FfmpegMediaTrack(trackInfo, name, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    private final class Copies implements FfmpegRuns.Source {
        private final Path file;
        private final MediaProbe media;
        private final boolean toneMap;

        Copies(Path file, MediaProbe media, boolean toneMap) {
            this.file = file;
            this.media = media;
            this.toneMap = toneMap;
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, int maxHeight) {
            return start(startMillis, maxHeight, false);
        }

        @Override
        public boolean hasSurroundTrack() {
            return media.surroundSound().isPresent();
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, int maxHeight, boolean surround) {
            try {
                return sourceManager.transcode(file, name, startMillis, maxHeight,
                        surround ? media.withSurround() : media, toneMap);
            } catch (IOException e) {
                Ffmpeg.forget();
                LOGGER.warn("Soundstage: ffmpeg could not be started to play media file {}", name, e);
                throw new FriendlyException("ffmpeg could not be started to play that media file.", SUSPICIOUS, e);
            }
        }

        @Override
        public boolean hasVideo() {
            return media.hasVideo();
        }

        @Override
        public FfmpegRuns.Next ended(FfmpegRuns.RunEnd end) {
            if (end.exit() == -1) {
                LOGGER.warn("Soundstage: ffmpeg closed its output for media file {} but did not exit; stopping it.", name);
            } else if (end.exit() != 0) {
                throw new FriendlyException("ffmpeg stopped with an error playing that media file.", SUSPICIOUS, null);
            }
            return FfmpegRuns.Next.END;
        }
    }
}
