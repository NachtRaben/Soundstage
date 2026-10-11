package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.MediaListing;
import com.nachtraben.soundstage.core.video.MediaNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MediaFolder {

    public static final String DIRECTORY = "soundstage-media";

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    private static volatile Path root;

    private MediaFolder() {}

    public static void serverStarting(Path serverDirectory, boolean enabled) {
        Path folder = serverDirectory.resolve(DIRECTORY).toAbsolutePath().normalize();
        root = folder;
        if (!enabled || Files.isDirectory(folder)) return;
        try {
            Files.createDirectories(folder);
            LOGGER.info("Soundstage: created {} for media files; play one with media:<file name>.", folder);
        } catch (IOException e) {
            LOGGER.warn("Soundstage: could not create the media folder {}: {}", folder, e.toString());
        }
    }

    public static void serverStopped() {
        root = null;
    }

    public static Optional<Path> resolve(String name) {
        Path folder = root;
        return folder == null ? Optional.empty() : MediaNames.resolve(folder, name);
    }

    public static MediaListing.Result listNames() {
        Path folder = root;
        if (folder == null) return new MediaListing.Result(List.of(), false);
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (MediaNames.isAcceptable(name) && Files.isRegularFile(entry)) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Soundstage: could not list the media folder {}: {}", folder, e.toString());
            return new MediaListing.Result(List.of(), false);
        }
        return MediaListing.cap(names, MediaListing.MAX_ENTRIES);
    }
}
