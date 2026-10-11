package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.CueParser;
import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.MediaProbe;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.StillImage;
import com.nachtraben.soundstage.core.video.SubtitleCue;
import com.nachtraben.soundstage.core.video.YoutubeStreams;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

final class Ffmpeg {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    private static final long VERSION_TIMEOUT_SECONDS = 5;
    private static final long PROBE_TIMEOUT_SECONDS = 10;
    private static final long EXIT_WAIT_MILLIS = 500;
    private static final long FINISH_WAIT_MILLIS = 2_000;
    private static final int PROBE_OUTPUT_LIMIT = 1 << 20;
    private static final int LOGGED_ERROR_LINES = 20;

    private record Availability(String ffmpeg, boolean runs) {
    }

    private static volatile Availability availability;
    private static volatile String ffprobeReportedMissing;

    private Ffmpeg() {}

    static final String DEFAULT_PATH = "ffmpeg";

    static String path() {
        String configured = SoundstageConfig.ffmpegPath();
        String chosen = configured == null || configured.isBlank() ? DEFAULT_PATH : configured.strip();
        if (chosen.equals(DEFAULT_PATH)) {
            String downloaded = FfmpegDownload.ffmpeg();
            if (downloaded != null) return downloaded;
        }
        return chosen;
    }

    static boolean available() throws InterruptedException {
        FfmpegDownload.requestIfWanted();
        String ffmpeg = path();
        Availability known = availability;
        if (known != null && known.ffmpeg().equals(ffmpeg)) return known.runs();
        synchronized (Ffmpeg.class) {
            known = availability;
            if (known != null && known.ffmpeg().equals(ffmpeg)) return known.runs();
            boolean runs = runs(ffmpeg);
            availability = new Availability(ffmpeg, runs);
            if (runs) {
                LOGGER.info("Soundstage: media files and YouTube videos play through ffmpeg ({}), with pictures "
                        + "for screens.", ffmpeg);
            } else {
                LOGGER.info("Soundstage: ffmpeg ({}) could not be run, so media files and YouTube videos will play "
                        + "with sound only. {}", ffmpeg, FfmpegDownload.hint());
            }
            return runs;
        }
    }

    static void forget() {
        availability = null;
        toneMapping = null;
    }

    private record ToneMapping(String ffmpeg, List<String> missing) {
    }

    private static volatile ToneMapping toneMapping;

    private static final Object TONE_MAPPING_LOCK = new Object();

    static List<String> missingToneMapFilters() throws InterruptedException {
        String ffmpeg = path();
        ToneMapping known = toneMapping;
        if (known != null && known.ffmpeg().equals(ffmpeg)) return known.missing();
        synchronized (TONE_MAPPING_LOCK) {
            known = toneMapping;
            if (known != null && known.ffmpeg().equals(ffmpeg)) return known.missing();
            String listing = listFilters(ffmpeg);
            if (listing == null) return null;
            List<String> missing = FfmpegCommand.missingToneMapFilters(listing);
            toneMapping = new ToneMapping(ffmpeg, missing);
            return missing;
        }
    }

    private static String listFilters(String ffmpeg) throws InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(FfmpegCommand.filters(ffmpeg))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            return null;
        }
        try {
            closeQuietly(process.getOutputStream());
            ByteArrayOutputStream listing = new ByteArrayOutputStream();
            Thread out = daemon("Soundstage-ffmpeg-filters", () -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    for (int n; (n = in.read(buffer)) >= 0; ) {
                        if (listing.size() < PROBE_OUTPUT_LIMIT) listing.write(buffer, 0, n);
                    }
                } catch (IOException ignored) {
                }
            });
            out.start();
            if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
            out.join(EXIT_WAIT_MILLIS);
            return listing.toString(StandardCharsets.UTF_8);
        } finally {
            stop(process);
        }
    }

    static boolean runs(String ffmpeg) throws InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(FfmpegCommand.version(ffmpeg))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            return false;
        }
        try {
            closeQuietly(process.getOutputStream());
            return process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;
        } finally {
            stop(process);
        }
    }

    static MediaProbe probe(Path file, String name) throws InterruptedException {
        String input = FfmpegCommand.input(file);
        return probe(ffprobe -> MediaProbe.command(ffprobe, input), name);
    }

    static MediaProbe probe(Function<String, List<String>> command, String name)
            throws InterruptedException {
        String ffmpeg = path();
        for (String ffprobe : FfmpegCommand.ffprobeCandidates(ffmpeg)) {
            Process process;
            try {
                process = new ProcessBuilder(command.apply(ffprobe)).start();
            } catch (IOException e) {
                continue;
            }
            try {
                return readProbe(process, ffprobe, name);
            } finally {
                stop(process);
            }
        }
        if (!ffmpeg.equals(ffprobeReportedMissing)) {
            ffprobeReportedMissing = ffmpeg;
            LOGGER.warn("Soundstage: ffprobe was not found beside {} or on the PATH, so media files play with "
                    + "sound only.", ffmpeg);
        }
        return null;
    }

    private static MediaProbe readProbe(Process process, String ffprobe, String name) throws InterruptedException {
        closeQuietly(process.getOutputStream());
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        List<String> errors = new ArrayList<>();
        Thread out = daemon("Soundstage-ffprobe-stdout", () -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) >= 0; ) {
                    if (json.size() < PROBE_OUTPUT_LIMIT) json.write(buffer, 0, n);
                }
            } catch (IOException ignored) {
            }
        });
        Thread err = daemon("Soundstage-ffprobe-stderr", () -> readLines(process.getErrorStream(), line -> {
            synchronized (errors) {
                if (errors.size() < LOGGED_ERROR_LINES) errors.add(line);
            }
        }));
        out.start();
        err.start();
        if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            LOGGER.warn("Soundstage: {} took longer than {}s to read media file {}; giving up on it.",
                    ffprobe, PROBE_TIMEOUT_SECONDS, name);
            throw new FriendlyException("Reading that media file took too long.", COMMON, null);
        }
        out.join(EXIT_WAIT_MILLIS);
        err.join(EXIT_WAIT_MILLIS);
        String said;
        synchronized (errors) {
            said = String.join(" | ", errors);
        }
        if (process.exitValue() != 0) {
            LOGGER.warn("Soundstage: {} could not read media file {} (exit {}): {}", ffprobe, name,
                    process.exitValue(), said);
            throw new FriendlyException("That media file can't be read.", COMMON, null);
        }
        try {
            return MediaProbe.parse(json.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Soundstage: {} gave an answer about media file {} that could not be read: {}",
                    ffprobe, name, e.toString());
            throw new FriendlyException("That media file can't be read.", COMMON, e);
        }
    }

    static Transcode transcode(Path file, String name, long startMillis, PictureSize.Copy picture, MediaProbe probe,
                               boolean toneMap) throws IOException {
        List<String> command = FfmpegCommand.transcode(path(), FfmpegCommand.input(file), startMillis,
                picture, probe, toneMap);
        return adopt(new ProcessBuilder(command).start(), name);
    }

    static Transcode still(Path file, String name, StillImage.Type type,
                           PictureSize.Copy picture) throws IOException {
        List<String> command = FfmpegCommand.still(path(), FfmpegCommand.input(file), type, picture);
        return adopt(new ProcessBuilder(command).start(), name);
    }

    static Transcode silence(String name, long startMillis, long durationMillis) throws IOException {
        List<String> command = FfmpegCommand.silence(path(), startMillis, durationMillis);
        return adopt(new ProcessBuilder(command).start(), name);
    }

    static Transcode transcode(YoutubeStreams.Streams streams, String name, long startMillis, PictureSize.Copy picture)
            throws IOException {
        List<String> command = FfmpegCommand.transcodeUrls(path(), streams, startMillis, picture);
        return adopt(new ProcessBuilder(command).start(), name);
    }

    static Transcode transcodeSound(YoutubeStreams.Streams streams, String name, long startMillis) throws IOException {
        List<String> command = FfmpegCommand.transcodeSoundUrl(path(), streams, startMillis);
        return adopt(new ProcessBuilder(command).start(), name);
    }

    static Process liveReencode(PictureSize.Copy picture) throws IOException {
        return new ProcessBuilder(FfmpegCommand.liveReencode(path(), picture)).start();
    }

    private static final int SUBTITLE_OUTPUT_LIMIT = 4 * (1 << 20);
    private static final long SUBTITLE_TIMEOUT_SECONDS = 600;
    private static final long SUBTITLE_PUBLISH_NANOS = 1_000_000_000L;

    static void subtitles(Path file, String name, MediaProbe.Subtitle track, BooleanSupplier wanted,
                          Consumer<CueTrack> publish) {
        if (!wanted.getAsBoolean()) return;
        String ffmpeg = path();
        List<String> command = FfmpegCommand.subtitles(ffmpeg, FfmpegCommand.input(file), track.index());
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            LOGGER.info("Soundstage: could not start ffmpeg to extract subtitles for {}: {}", name, e.toString());
            return;
        }
        try {
            closeQuietly(process.getOutputStream());
            ByteArrayOutputStream vtt = new ByteArrayOutputStream();
            boolean[] overLimit = {false};
            List<String> errors = new ArrayList<>();
            Thread out = daemon("Soundstage-Subtitles-stdout", () -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    for (int n; (n = in.read(buffer)) >= 0; ) {
                        synchronized (vtt) {
                            if (vtt.size() + n > SUBTITLE_OUTPUT_LIMIT) {
                                overLimit[0] = true;
                                break;
                            }
                            vtt.write(buffer, 0, n);
                        }
                    }
                } catch (IOException ignored) {
                }
            });
            Thread err = daemon("Soundstage-Subtitles-stderr", () -> readLines(process.getErrorStream(), line -> {
                synchronized (errors) {
                    if (errors.size() < LOGGED_ERROR_LINES) errors.add(line);
                }
            }));
            out.start();
            err.start();
            long started = System.nanoTime();
            long published = System.nanoTime();
            int handedOn = 0;
            boolean finished;
            while (true) {
                finished = process.waitFor(250, TimeUnit.MILLISECONDS);
                if (!wanted.getAsBoolean()) return;
                long now = System.nanoTime();
                if (finished || overLimit[0] || now - started > TimeUnit.SECONDS.toNanos(SUBTITLE_TIMEOUT_SECONDS)) {
                    break;
                }
                if (now - published >= SUBTITLE_PUBLISH_NANOS) {
                    published = now;
                    handedOn = publishCues(vtt, false, handedOn, publish);
                }
            }
            if (finished) {
                out.join(EXIT_WAIT_MILLIS);
                err.join(EXIT_WAIT_MILLIS);
            }
            boolean complete = finished && !overLimit[0] && process.exitValue() == 0;
            handedOn = publishCues(vtt, complete, handedOn, publish);
            if (complete) {
                LOGGER.info("Soundstage: read {} subtitle cues from {} in {} s.", handedOn, name,
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));
            }
            if (!finished || overLimit[0]) {
                LOGGER.info("Soundstage: stopped extracting subtitles for {} after {} cues ({}).", name, handedOn,
                        overLimit[0] ? "output too large" : "took too long");
            } else if (process.exitValue() != 0) {
                String said;
                synchronized (errors) {
                    said = String.join(" | ", errors);
                }
                LOGGER.info("Soundstage: ffmpeg could not extract all the subtitles for {} (exit {}, {} cues kept): {}",
                        name, process.exitValue(), handedOn, said);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop(process);
        }
    }

    static int publishCues(ByteArrayOutputStream vtt, boolean complete, int handedOn, Consumer<CueTrack> publish) {
        String text;
        synchronized (vtt) {
            text = vtt.toString(StandardCharsets.UTF_8);
        }
        if (!complete) {
            int end = text.lastIndexOf("\n\n");
            if (end < 0) return handedOn;
            text = text.substring(0, end + 2);
        }
        List<SubtitleCue> cues = CueParser.parseWebVtt(text);
        if (cues.size() <= handedOn) return handedOn;
        publish.accept(new CueTrack(cues));
        return cues.size();
    }

    static Transcode adopt(Process process, String name) {
        try {
            Transcode transcode = new Transcode(process, name);
            closeQuietly(process.getOutputStream());
            transcode.errors.start();
            return transcode;
        } catch (RuntimeException | Error e) {
            stop(process);
            throw e;
        }
    }

    static final class Transcode {
        private final Process process;
        private final String name;
        private final Thread errors;
        private volatile boolean abandoned;
        private volatile boolean certificateRefused;

        private Transcode(Process process, String name) {
            this.process = process;
            this.name = name;
            this.errors = daemon("Soundstage-ffmpeg-stderr", this::logErrors);
        }

        InputStream output() {
            return process.getInputStream();
        }

        long pid() {
            return process.pid();
        }

        void abandon() {
            abandoned = true;
            process.destroy();
        }

        boolean isAbandoned() {
            return abandoned;
        }

        boolean certificateRefused() {
            return certificateRefused;
        }

        int awaitExit() throws InterruptedException {
            if (!process.waitFor(FINISH_WAIT_MILLIS, TimeUnit.MILLISECONDS)) return -1;
            errors.join(EXIT_WAIT_MILLIS);
            return process.exitValue();
        }

        void close() {
            abandoned = true;
            stop(process);
        }

        private void logErrors() {
            int[] seen = {0};
            readLines(process.getErrorStream(), raw -> {
                if (FfmpegCommand.saysCertificateRefused(raw)) certificateRefused = true;
                String line = FfmpegCommand.withoutUrls(raw);
                if (abandoned) {
                    LOGGER.debug("Soundstage: ffmpeg, stopped, playing {}: {}", name, line);
                } else if (seen[0]++ < LOGGED_ERROR_LINES) {
                    LOGGER.warn("Soundstage: ffmpeg playing {}: {}", name, line);
                }
            });
            if (seen[0] > LOGGED_ERROR_LINES) {
                LOGGER.warn("Soundstage: ffmpeg playing {}: {} more error lines not shown.", name,
                        seen[0] - LOGGED_ERROR_LINES);
            }
        }
    }

    static void stop(Process process) {
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        process.destroy();
        boolean interrupted = Thread.interrupted();
        try {
            if (!process.waitFor(EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            interrupted = true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static Thread daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        return thread;
    }

    static void readLines(InputStream stream, Consumer<String> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.isBlank()) sink.accept(line.strip());
            }
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
