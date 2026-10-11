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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class StillImageProbe implements MediaContainerProbe {

    public static final String NAME = "soundstage-image";

    public static final String AUTHOR = "Image";

    public static final String TOO_LARGE = "That image is larger than " + StillImage.MAX_SIDE + " by "
            + StillImage.MAX_SIDE + " pixels, or its size cannot be read.";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean matchesHints(MediaContainerHints hints) {
        return hints != null && StillImage.ofContentType(hints.mimeType) != null;
    }

    @Override
    public MediaContainerDetectionResult probe(AudioReference reference, SeekableInputStream inputStream)
            throws IOException {
        StillImage.Header header = StillImage.read(inputStream);
        if (header == null) return null;
        if (!header.fits()) return MediaContainerDetectionResult.unsupportedFormat(this, TOO_LARGE);
        AudioTrackInfo info = new AudioTrackInfo(titleOf(reference.identifier, AUTHOR), AUTHOR,
                Units.DURATION_MS_UNKNOWN, reference.identifier, true, reference.identifier, null, null);
        return MediaContainerDetectionResult.supportedFormat(this, header.type().name(), info);
    }

    @Override
    public AudioTrack createTrack(String parameters, AudioTrackInfo trackInfo, SeekableInputStream inputStream) {
        LoggerFactory.getLogger("Soundstage").warn("Soundstage: an image is shown through ffmpeg, which this server "
                + "cannot run.");
        throw new FriendlyException("This server can't show images.", FriendlyException.Severity.COMMON, null);
    }

    public static StillImage.Type type(MediaContainerDescriptor descriptor) {
        if (descriptor == null || !(descriptor.probe instanceof StillImageProbe)) return null;
        try {
            return StillImage.Type.valueOf(descriptor.parameters);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    static String titleOf(String identifier, String fallback) {
        try {
            String path = new URI(identifier).getRawPath();
            if (path == null) return fallback;
            String name = path.substring(path.lastIndexOf('/') + 1);
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            return name.isBlank() ? fallback : name;
        } catch (Exception e) {
            return fallback;
        }
    }
}
