package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public final class SilentVideoProbe implements MediaContainerProbe {

    public static final String THROUGH_FFMPEG = "ffmpeg";

    public static final Set<String> CONTAINERS = Set.of("mp4", "matroska/webm");

    private final MediaContainerProbe inner;

    public SilentVideoProbe(MediaContainerProbe inner) {
        this.inner = inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public boolean matchesHints(MediaContainerHints hints) {
        return inner.matchesHints(hints);
    }

    @Override
    public MediaContainerDetectionResult probe(AudioReference reference, SeekableInputStream inputStream)
            throws IOException {
        MediaContainerDetectionResult result = inner.probe(reference, inputStream);
        if (result == null || !result.isContainerDetected() || result.isSupportedFile() || result.isReference()) {
            return result;
        }
        AudioTrackInfo info = new AudioTrackInfo(StillImageProbe.titleOf(reference.identifier, "Unknown title"),
                "Unknown artist", Units.DURATION_MS_UNKNOWN, reference.identifier, false, reference.identifier,
                null, null);
        return MediaContainerDetectionResult.supportedFormat(this, THROUGH_FFMPEG, info);
    }

    @Override
    public AudioTrack createTrack(String parameters, AudioTrackInfo trackInfo, SeekableInputStream inputStream) {
        if (THROUGH_FFMPEG.equals(parameters)) {
            LoggerFactory.getLogger("Soundstage").warn("Soundstage: a video's sound can only be read through ffmpeg, "
                    + "which this server cannot run.");
            throw new FriendlyException("This server can't play that video's sound.",
                    FriendlyException.Severity.COMMON, null);
        }
        return inner.createTrack(parameters, trackInfo, inputStream);
    }

    public static boolean throughFfmpeg(MediaContainerDescriptor descriptor) {
        return descriptor != null && descriptor.probe instanceof SilentVideoProbe
                && THROUGH_FFMPEG.equals(descriptor.parameters);
    }
}
