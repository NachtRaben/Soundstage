package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.SoundChoices;
import com.nachtraben.soundstage.audio.video.SubtitleSource;
import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.StillImage;
import com.nachtraben.soundstage.core.video.StillImageProbe;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public final class FfmpegMediaTrack extends DelegatedAudioTrack implements VideoCapableTrack {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    static final ExecutorService SUBTITLE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Soundstage-Subtitles");
        thread.setDaemon(true);
        return thread;
    });

    private final String name;
    private final MediaFolderSourceManager sourceManager;
    private final FfmpegRuns runs;
    private final boolean still;
    private final TrackAudioChoice audio;

    FfmpegMediaTrack(AudioTrackInfo info, String name, MediaFolderSourceManager sourceManager) {
        super(info);
        this.name = name;
        this.sourceManager = sourceManager;
        this.still = StillImage.isImageName(name);
        this.runs = new FfmpegRuns((still ? "image " : "media file ") + name, still);
        this.audio = new TrackAudioChoice(info.identifier);
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
        return audio.reapply() ? PictureRetake.SEEK : PictureRetake.NONE;
    }

    @Override
    public boolean isStill() {
        return still;
    }

    String name() {
        return name;
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
        if (step == FfmpegRuns.Step.NO_PICTURE) runs.givePictureUp();
        boolean again = step != FfmpegRuns.Step.NONE || runs.picturePending() || runs.placeFreed();
        if (!again) return PictureRetake.NONE;
        return still ? PictureRetake.REPLAY : PictureRetake.SEEK;
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
        if (still) {
            showStill(file, executor);
            return;
        }
        MediaProbe media = sourceManager.probe(file, name);
        if (media == null) {
            SoundOnlyFallbacks.report(trackInfo.identifier, "ffmpeg or ffprobe is not available");
            runs.abandonPicture();
            processDelegate(sourceManager.readSoundOnly(file, name), executor);
            return;
        }
        if (!media.hasAudio() && !media.hasVideo()) {
            throw new FriendlyException("That media file has no sound or picture to play.", COMMON, null);
        }
        if (!media.hasAudio()) {
            LOGGER.info("Soundstage: media file {} has no sound; its picture plays on a silent soundtrack.", name);
        }
        String key = audio.begin(media.audioChoices());
        String playing = AudioChoice.labelOf(key, media.audioChoices());
        int entries = media.audioEntries().size();
        media.withAudio(key).sound().ifPresent(sound -> LOGGER.info("Soundstage: media file {} plays audio stream {}: "
                        + "{} {}, {}{}{}.", name, sound.index(), sound.channels(),
                sound.channels() == 1 ? "channel" : "channels",
                sound.language() == null ? "no language" : sound.language(),
                sound.channels() == 2 ? "" : ", mixed to stereo",
                entries > 1 ? ", " + playing + ", one of " + entries + " audio tracks" : ""));
        if (!media.hasVideo() || !runs.pictureMayCome()) runs.abandonPicture();
        if (media.hasOnlyImageSubtitles()) {
            LOGGER.info("Soundstage: media file {} has subtitles, but they are images, which this mod does not "
                    + "show.", name);
        }
        runs.play(executor, new Copies(file, media));
    }

    private void showStill(Path file, LocalAudioTrackExecutor executor) throws Exception {
        StillImage.Header header = sourceManager.readStill(file);
        if (header == null) {
            throw new FriendlyException("That file is not a PNG or JPEG image.", COMMON, null);
        }
        if (!header.fits()) {
            throw new FriendlyException(StillImageProbe.TOO_LARGE, COMMON, null);
        }
        if (!sourceManager.ffmpegAvailable()) {
            throw new FriendlyException(MediaFolderSourceManager.IMAGES_NEED_FFMPEG, COMMON, null);
        }
        if (!runs.pictureMayCome()) runs.abandonPicture();
        LOGGER.debug("Soundstage: image {} ({}, {}x{}) shows on a silent soundtrack.", name, header.type(),
                header.width(), header.height());
        runs.play(executor, new StillCopies(header,
                copy -> sourceManager.still(file, name, header.type(), copy),
                () -> sourceManager.silence(name, 0, MediaProbe.UNKNOWN_DURATION)));
    }

    private void startSubtitles(Path file, MediaProbe media) {
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
            public void start(String key, Consumer<CueTrack> deliver,
                              BooleanSupplier wanted) {
                BooleanSupplier stillWanted = () -> !runs.isStopped() && wanted.getAsBoolean();
                text.stream().filter(sub -> subtitleKey(sub).equals(key)).findFirst().ifPresent(subtitle ->
                        SUBTITLE_EXECUTOR.execute(() -> {
                            if (!stillWanted.getAsBoolean()) return;
                            LOGGER.info("Soundstage: media file {} shows subtitle stream {} ({}, {}).", name,
                                    subtitle.index(), subtitle.codec(), subtitle.language() == null
                                            || subtitle.language().isEmpty() ? "no language" : subtitle.language());
                            Ffmpeg.subtitles(file, name, subtitle, stillWanted, deliver);
                        }));
            }
        });
    }

    private static String subtitleKey(MediaProbe.Subtitle subtitle) {
        return "s:" + subtitle.index();
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
        private Boolean toneMapped;
        private boolean subtitlesStarted;

        Copies(Path file, MediaProbe media) {
            this.file = file;
            this.media = media;
        }

        @Override
        public boolean hasSurroundTrack() {
            return media.withAudio(audio.key()).surroundSound().isPresent();
        }

        @Override
        public boolean pictureOptional() {
            return true;
        }

        @Override
        public Ffmpeg.Transcode start(long startMillis, FfmpegRuns.Sizing sizing, boolean surround, boolean picture)
                throws InterruptedException {
            MediaProbe chosen = media.withAudio(audio.key());
            MediaProbe probe = picture ? chosen : chosen.withoutPicture();
            if (picture && toneMapped == null) toneMapped = toneMap(media);
            if (picture && !subtitlesStarted) {
                subtitlesStarted = true;
                startSubtitles(file, media);
            }
            PictureSize.Copy size = picture ? sizing.copy(media.pictureWidth(), media.videoHeight()) : null;
            try {
                if (!picture && !media.hasAudio()) {
                    return sourceManager.silence(name, startMillis, media.durationMillis());
                }
                return sourceManager.transcode(file, name, startMillis, size,
                        surround ? probe.withSurround() : probe, picture && toneMapped);
            } catch (IOException e) {
                Ffmpeg.forget();
                LOGGER.warn("Soundstage: ffmpeg could not be started to play media file {}", name, e);
                throw new FriendlyException("That media file could not be started.", SUSPICIOUS, e);
            }
        }

        @Override
        public boolean hasVideo() {
            return media.hasVideo();
        }

        @Override
        public FfmpegRuns.Next ended(FfmpegRuns.RunEnd end) {
            if (end.exit() == -1) {
                LOGGER.warn(
                        "Soundstage: ffmpeg closed its output for media file {} but did not exit; stopping it.", name);
            } else if (end.exit() != 0) {
                LOGGER.warn("Soundstage: ffmpeg stopped with exit {} playing media file {}.", end.exit(), name);
                throw new FriendlyException("That media file stopped with an error.", SUSPICIOUS, null);
            }
            return FfmpegRuns.Next.END;
        }
    }
}
