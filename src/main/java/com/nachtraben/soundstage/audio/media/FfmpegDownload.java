package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.ffmpeg.FfmpegBuild;
import com.nachtraben.soundstage.core.ffmpeg.FfmpegInstall;

import java.nio.file.Path;

public final class FfmpegDownload {

    public static final String DIRECTORY = "soundstage-ffmpeg";

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    public enum State { IDLE, CHECKING, DOWNLOADING, READY, FAILED, NOT_NEEDED }

    private static boolean rigForce() {
        return com.nachtraben.soundstage.RigStaging.fault("ffmpeg-force-download");
    }

    private static final Object LOCK = new Object();
    private static volatile Path serverDirectory;
    private static Path checked;
    private static volatile State state = State.IDLE;
    private static volatile Path managed;
    private static volatile Path managedFor;

    private FfmpegDownload() {}

    public static void serverStarting(Path directory) {
        serverDirectory = directory.toAbsolutePath().normalize();
        synchronized (LOCK) {
            if (state == State.FAILED || state == State.NOT_NEEDED) checked = null;
        }
        requestIfWanted();
    }

    static void requestIfWanted() {
        Path directory = serverDirectory;
        if (directory == null || !(rigForce() || SoundstageConfig.downloadFfmpeg())) return;
        synchronized (LOCK) {
            if (directory.equals(checked)) return;
            checked = directory;
            state = State.CHECKING;
        }
        Thread thread = new Thread(() -> check(directory), "Soundstage-ffmpeg-download");
        thread.setDaemon(true);
        thread.start();
    }

    static String ffmpeg() {
        Path ffmpeg = managed;
        return ffmpeg != null && serverDirectory != null && serverDirectory.equals(managedFor) ? ffmpeg.toString() : null;
    }

    public static State state() {
        return state;
    }

    public static String downloaded() {
        return ffmpeg();
    }

    static String hint() {
        return switch (state) {
            case CHECKING, DOWNLOADING -> "Soundstage is downloading one (downloadFfmpeg); pictures start once it is ready.";
            case FAILED -> "downloadFfmpeg is on, but the download did not work; the reason was logged above.";
            default -> "Install ffmpeg, set ffmpegPath in the Soundstage config, or turn on downloadFfmpeg to "
                    + "have Soundstage download a copy.";
        };
    }

    private static void check(Path directory) {
        try {
            String configured = SoundstageConfig.ffmpegPath();
            if (configured != null && !configured.isBlank() && !configured.strip().equals(Ffmpeg.DEFAULT_PATH)) {
                LOGGER.info("Soundstage: downloadFfmpeg is on, but ffmpegPath names {}; that one is used and nothing "
                        + "is downloaded.", configured.strip());
                state = State.NOT_NEEDED;
                return;
            }
            if (!rigForce() && Ffmpeg.runs(Ffmpeg.DEFAULT_PATH)) {
                LOGGER.info("Soundstage: downloadFfmpeg is on, but ffmpeg already runs from this server's PATH; "
                        + "nothing is downloaded.");
                state = State.NOT_NEEDED;
                return;
            }
            FfmpegBuild build = FfmpegBuild.forThisPlatform().orElse(null);
            if (build == null) {
                LOGGER.warn("Soundstage: downloadFfmpeg is on, but there is no ffmpeg build to download for {} {}. "
                        + "Install ffmpeg and set ffmpegPath to give media files and YouTube videos pictures.",
                        System.getProperty("os.name"), System.getProperty("os.arch"));
                state = State.FAILED;
                return;
            }
            if (com.nachtraben.soundstage.RigStaging.fault("ffmpeg-bad-pin")) {
                LOGGER.warn("Soundstage: red control — the ffmpeg build is pinned to a hash no archive has "
                        + "(ffmpeg-bad-pin)");
                build = new FfmpegBuild(build.id(), build.version(), build.url(), "0".repeat(64), build.size(),
                        build.archive(), build.root());
            }
            Path home = directory.resolve(DIRECTORY);
            FfmpegBuild pinned = build;
            long[] logged = {-1};
            FfmpegInstall.Outcome outcome = FfmpegInstall.ensure(home, build, FfmpegInstall.GITHUB,
                    FfmpegInstall.RUNS, bytes -> {
                        long quarter = bytes * 4 / Math.max(1, pinned.size());
                        if (logged[0] < 0) {
                            state = State.DOWNLOADING;
                            LOGGER.info("Soundstage: this server has no ffmpeg, and downloadFfmpeg is on: downloading "
                                    + "ffmpeg {} ({} MB) from github.com/BtbN/FFmpeg-Builds into {}.",
                                    pinned.version(), pinned.megabytes(), home);
                            logged[0] = 0;
                        } else if (quarter > logged[0] && quarter < 4) {
                            logged[0] = quarter;
                            LOGGER.info("Soundstage: ffmpeg download {}% done.", quarter * 25);
                        }
                    });
            FfmpegInstall.Installed installed = outcome.installed();
            if (installed == null) {
                state = State.FAILED;
                LOGGER.warn("Soundstage: could not get ffmpeg: {}. Media files and YouTube videos play with sound "
                        + "only.", outcome.failure());
                return;
            }
            managedFor = directory;
            managed = installed.ffmpeg();
            state = State.READY;
            Ffmpeg.forget();
            if (outcome.failure() != null) {
                LOGGER.warn("Soundstage: could not get ffmpeg {}: {}. Using the one downloaded before, {}, in {}.",
                        build.version(), outcome.failure(), installed.version(), installed.home());
            } else if (outcome.downloaded()) {
                LOGGER.info("Soundstage: ffmpeg {} is downloaded, matches its pinned SHA-256, ran its test copy, and "
                        + "is ready in {}. Media files and YouTube videos have pictures from the next track on.",
                        installed.version(), installed.home());
            } else {
                LOGGER.info("Soundstage: using the ffmpeg downloaded before, {}, in {}.", installed.version(),
                        installed.home());
            }
        } catch (InterruptedException e) {
            state = State.FAILED;
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            state = State.FAILED;
            LOGGER.warn("Soundstage: getting ffmpeg failed unexpectedly", e);
        }
    }
}
