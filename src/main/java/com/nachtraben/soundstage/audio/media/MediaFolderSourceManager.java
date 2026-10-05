package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.MediaNames;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public final class MediaFolderSourceManager implements AudioSourceManager {

    public static final String PREFIX = "media:";

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final String SOURCE_NAME = "soundstage-media";
    private static final int ENCODING_VERSION = 1;
    private static final String AUTHOR = "Media folder";
    private static final String SOUND_ONLY_REFUSED = "Without ffmpeg on the server, that media file cannot be played.";

    @FunctionalInterface
    interface Transcoder {
        Ffmpeg.Transcode start(Path file, String name, long startMillis, int maxHeight, MediaProbe probe,
                               boolean toneMap) throws IOException;
    }

    @FunctionalInterface
    interface Prober {
        MediaProbe probe(Path file, String name) throws InterruptedException;
    }

    @FunctionalInterface
    interface FilterCheck {
        List<String> missingToneMapFilters() throws InterruptedException;
    }

    private final LocalAudioSourceManager soundOnly = new LocalAudioSourceManager();
    private final Transcoder transcoder;
    private final Prober prober;
    private final FilterCheck filters;

    public MediaFolderSourceManager() {
        this(Ffmpeg::transcode, MediaFolderSourceManager::probeWithFfmpeg);
    }

    MediaFolderSourceManager(Transcoder transcoder, Prober prober) {
        this(transcoder, prober, Ffmpeg::missingToneMapFilters);
    }

    MediaFolderSourceManager(Transcoder transcoder, Prober prober, FilterCheck filters) {
        this.transcoder = transcoder;
        this.prober = prober;
        this.filters = filters;
    }

    List<String> missingToneMapFilters() throws InterruptedException {
        return filters.missingToneMapFilters();
    }

    private static MediaProbe probeWithFfmpeg(Path file, String name) throws InterruptedException {
        return Ffmpeg.available() ? Ffmpeg.probe(file, name) : null;
    }

    MediaProbe probe(Path file, String name) throws InterruptedException {
        return prober.probe(file, name);
    }

    Ffmpeg.Transcode transcode(Path file, String name, long startMillis, int maxHeight, MediaProbe probe,
                               boolean toneMap) throws IOException {
        return transcoder.start(file, name, startMillis, maxHeight, probe, toneMap);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null || !identifier.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        if (!SoundstageConfig.enableMediaFolder()) {
            throw new FriendlyException("Media files are turned off on this server.", COMMON, null);
        }
        String name = identifier.substring(PREFIX.length()).strip();
        if (!MediaNames.isAcceptable(name)) {
            throw new FriendlyException("Name a media file by its file name alone, like media:clip.mp4.",
                    COMMON, null);
        }
        Path file = MediaFolder.resolve(name).orElseThrow(() ->
                new FriendlyException("There is no file by that name in the media folder.", COMMON, null));

        MediaProbe probe;
        try {
            probe = probe(file, name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FriendlyException("Loading that media file was interrupted.", COMMON, e);
        }
        if (probe != null) {
            if (!probe.hasAudio()) {
                throw new FriendlyException("That media file has no sound to play.", COMMON, null);
            }
            return new FfmpegMediaTrack(info(name, probe.durationMillis()), name, this);
        }
        AudioTrack readable = readSoundOnly(file, name);
        return new FfmpegMediaTrack(info(name, readable.getDuration()), name, this);
    }

    InternalAudioTrack readSoundOnly(Path file, String name) {
        AudioItem item;
        try {
            item = soundOnly.loadItem(null, new AudioReference(file.toString(), null));
        } catch (FriendlyException e) {
            LOGGER.warn("Soundstage: without ffmpeg, LavaPlayer cannot read media file {}: {}", name, e.getMessage());
            throw new FriendlyException(SOUND_ONLY_REFUSED, COMMON, e);
        }
        if (item instanceof InternalAudioTrack track) {
            return track;
        }
        throw new FriendlyException(SOUND_ONLY_REFUSED, COMMON, null);
    }

    private static AudioTrackInfo info(String name, long lengthMillis) {
        String identifier = PREFIX + name;
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        return new AudioTrackInfo(title, AUTHOR, lengthMillis, identifier, false, identifier, null, null);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return track instanceof FfmpegMediaTrack;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        output.writeByte(ENCODING_VERSION);
        output.writeUTF(((FfmpegMediaTrack) track).name());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        if (input.readUnsignedByte() != ENCODING_VERSION) return null;
        String name = input.readUTF();
        return MediaNames.isAcceptable(name) ? new FfmpegMediaTrack(trackInfo, name, this) : null;
    }

    @Override
    public void shutdown() {
        soundOnly.shutdown();
    }
}
