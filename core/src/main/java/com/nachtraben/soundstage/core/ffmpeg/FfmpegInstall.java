package com.nachtraben.soundstage.core.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

public final class FfmpegInstall {

    public interface Fetcher {
        void fetch(URI url, OutputStream out, LongConsumer progress) throws IOException, InterruptedException;
    }

    public interface SelfTest {
        String failure(Path ffmpeg, Path ffprobe, String version) throws InterruptedException;
    }

    public record Installed(Path home, Path ffmpeg, Path ffprobe, String version) {
    }

    public record Outcome(Installed installed, String failure, boolean downloaded) {
    }

    static final String RECORD = "install.properties";
    static final String DOES_NOT_RUN = ".does-not-run";
    static final String PART = ".part";
    static final String STAGING = ".staging";
    static final long MIN_FREE_BYTES = 512L << 20;

    private FfmpegInstall() {}

    public static Outcome ensure(Path dir, FfmpegBuild build, Fetcher fetcher, SelfTest test, LongConsumer progress)
            throws InterruptedException {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return new Outcome(null, "could not create " + dir + ": " + e.getMessage(), false);
        }
        try (FileChannel channel = FileChannel.open(dir.resolve(".lock"), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return ensureLocked(dir, build, fetcher, test, progress);
        } catch (IOException | OverlappingFileLockException e) {
            return new Outcome(null, "could not lock " + dir + ": " + e, false);
        }
    }

    private static Outcome ensureLocked(Path dir, FfmpegBuild build, Fetcher fetcher, SelfTest test,
                                        LongConsumer progress) throws InterruptedException {
        Path home = dir.resolve(build.id());
        Optional<Installed> current = read(home);
        if (current.isPresent() && build.sha256().equals(recordedSha256(home))) {
            Installed installed = current.get();
            String failure = test.failure(installed.ffmpeg(), installed.ffprobe(), installed.version());
            if (failure == null) {
                removeOthers(dir, home);
                return new Outcome(installed, null, false);
            }
            deleteQuietly(home);
        }
        Path doesNotRun = dir.resolve(build.id() + DOES_NOT_RUN);
        String failure;
        if (Files.isRegularFile(doesNotRun)) {
            failure = "it did not run here before (" + readQuietly(doesNotRun) + "); delete " + doesNotRun
                    + " to try again";
        } else {
            try {
                Installed installed = install(dir, build, fetcher, test, progress, doesNotRun);
                removeOthers(dir, installed.home());
                return new Outcome(installed, null, true);
            } catch (IOException e) {
                failure = e.getMessage();
            }
        }
        for (Installed previous : previous(dir, home)) {
            if (test.failure(previous.ffmpeg(), previous.ffprobe(), previous.version()) == null) {
                return new Outcome(previous, failure, false);
            }
        }
        return new Outcome(null, failure, false);
    }

    private static Installed install(Path dir, FfmpegBuild build, Fetcher fetcher, SelfTest test,
                                     LongConsumer progress, Path doesNotRun)
            throws IOException, InterruptedException {
        long free = Files.getFileStore(dir).getUsableSpace();
        if (free < MIN_FREE_BYTES) {
            throw new IOException("only " + (free >> 20) + " MB is free in " + dir + ", and ffmpeg needs "
                    + (MIN_FREE_BYTES >> 20) + " MB");
        }
        Path part = dir.resolve(build.id() + PART);
        Path staging = dir.resolve(build.id() + STAGING);
        Path home = dir.resolve(build.id());
        try {
            download(build, part, fetcher, progress);
            deleteQuietly(staging);
            FfmpegArchive.extract(part, build, staging);
            Path ffmpeg = staging.resolve("bin").resolve(build.ffmpegName());
            Path ffprobe = staging.resolve("bin").resolve(build.ffprobeName());
            String failure = test.failure(ffmpeg, ffprobe, build.version());
            if (failure != null) {
                Files.writeString(doesNotRun, failure);
                throw new IOException("the downloaded ffmpeg does not run here: " + failure);
            }
            writeRecord(staging, build);
            deleteQuietly(home);
            try {
                Files.move(staging, home, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(staging, home);
            }
            Optional<Installed> installed = read(home);
            if (installed.isEmpty()) throw new IOException("the install at " + home + " could not be read back");
            return installed.get();
        } finally {
            deleteQuietly(part);
            deleteQuietly(staging);
        }
    }

    private static void download(FfmpegBuild build, Path part, Fetcher fetcher, LongConsumer progress)
            throws IOException, InterruptedException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("this Java has no SHA-256", e);
        }
        long[] count = {0};
        try (OutputStream file = Files.newOutputStream(part, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             OutputStream out = new FilterOutputStream(file) {
                 @Override
                 public void write(int b) throws IOException {
                     write(new byte[] {(byte) b}, 0, 1);
                 }

                 @Override
                 public void write(byte[] b, int off, int len) throws IOException {
                     count[0] += len;
                     if (count[0] > build.size()) {
                         throw new IOException("the download ran past the " + build.size() + " bytes pinned for it");
                     }
                     digest.update(b, off, len);
                     file.write(b, off, len);
                 }
             }) {
            try {
                fetcher.fetch(build.url(), out, progress);
            } catch (IOException e) {
                throw e.getMessage() != null && e.getMessage().contains("pinned") ? e
                        : new IOException("the download from " + build.url().getHost() + " failed (" + e + ")", e);
            }
        }
        if (count[0] != build.size()) {
            throw new IOException("the download ended at " + count[0] + " of the " + build.size() + " bytes pinned for it");
        }
        String sha256 = HexFormat.of().formatHex(digest.digest());
        if (!sha256.equals(build.sha256())) {
            throw new IOException("the download's SHA-256 is " + sha256 + ", not the " + build.sha256() + " pinned for it");
        }
    }

    private static void writeRecord(Path home, FfmpegBuild build) throws IOException {
        Properties record = new Properties();
        record.setProperty("sha256", build.sha256());
        record.setProperty("version", build.version());
        record.setProperty("ffmpeg", "bin/" + build.ffmpegName());
        record.setProperty("ffprobe", "bin/" + build.ffprobeName());
        try (Writer out = Files.newBufferedWriter(home.resolve(RECORD))) {
            record.store(out, "An ffmpeg Soundstage downloaded and ran. Delete this directory to have it fetched again.");
        }
    }

    static Optional<Installed> read(Path home) {
        Properties record = properties(home);
        if (record == null) return Optional.empty();
        String version = record.getProperty("version");
        Path ffmpeg = inside(home, record.getProperty("ffmpeg"));
        Path ffprobe = inside(home, record.getProperty("ffprobe"));
        if (version == null || ffmpeg == null || ffprobe == null) return Optional.empty();
        if (!Files.isRegularFile(ffmpeg, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(ffprobe, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(new Installed(home, ffmpeg, ffprobe, version));
    }

    private static String recordedSha256(Path home) {
        Properties record = properties(home);
        return record == null ? null : record.getProperty("sha256");
    }

    private static Properties properties(Path home) {
        Path file = home.resolve(RECORD);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        Properties record = new Properties();
        try (Reader in = Files.newBufferedReader(file)) {
            record.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return record;
    }

    private static Path inside(Path home, String relative) {
        if (relative == null || !relative.startsWith("bin/")) return null;
        String name = relative.substring(4);
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) return null;
        return home.resolve("bin").resolve(name);
    }

    static List<Installed> previous(Path dir, Path except) {
        List<Installed> found = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (entry.equals(except) || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                read(entry).ifPresent(found::add);
            }
        } catch (IOException e) {
            return found;
        }
        found.sort((a, b) -> Long.compare(modified(b.home().resolve(RECORD)), modified(a.home().resolve(RECORD))));
        return found;
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void removeOthers(Path dir, Path keep) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (entry.equals(keep)) continue;
                String name = entry.getFileName().toString();
                boolean ours = name.endsWith(PART) || name.endsWith(STAGING) || name.endsWith(DOES_NOT_RUN)
                        || Files.isRegularFile(entry.resolve(RECORD), LinkOption.NOFOLLOW_LINKS);
                if (ours) deleteQuietly(entry);
            }
        } catch (IOException ignored) {
        }
    }

    private static final int READ_TIMEOUT_MILLIS = 60_000;
    private static final int CONNECT_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_REDIRECTS = 5;

    public static final Fetcher GITHUB = FfmpegInstall::fetchFromGithub;

    static boolean allowedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("github.com") || h.endsWith(".githubusercontent.com");
    }

    private static void fetchFromGithub(URI url, OutputStream out, LongConsumer progress) throws IOException {
        URI at = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!"https".equalsIgnoreCase(at.getScheme()) || !allowedHost(at.getHost())) {
                throw new IOException("the download was sent to " + at.getScheme() + "://" + at.getHost()
                        + ", which is not GitHub");
            }
            HttpURLConnection connection = (HttpURLConnection) at.toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setRequestProperty("User-Agent", "Soundstage");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("GitHub redirected without saying where");
                    at = at.resolve(location);
                    continue;
                }
                if (status != 200) throw new IOException("GitHub answered " + status + " for " + url);
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[1 << 16];
                    long total = 0;
                    for (int n; (n = in.read(buffer)) >= 0; ) {
                        out.write(buffer, 0, n);
                        total += n;
                        progress.accept(total);
                    }
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("GitHub redirected more than " + MAX_REDIRECTS + " times");
    }

    private static final long SELF_TEST_TIMEOUT_SECONDS = 60;
    private static final int SELF_TEST_OUTPUT_LIMIT = 64 * 1024;

    public static final SelfTest RUNS = FfmpegInstall::selfTest;

    private static String selfTest(Path ffmpeg, Path ffprobe, String version) throws InterruptedException {
        String failure = versionFailure(ffmpeg, "ffmpeg", version);
        if (failure != null) return failure;
        failure = versionFailure(ffprobe, "ffprobe", version);
        if (failure != null) return failure;
        Run run = run(List.of(ffmpeg.toString(), "-hide_banner", "-nostdin", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=size=128x72:rate=10:duration=0.5",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.5",
                "-c:v", "libx264", "-c:a", "aac", "-f", "mpegts", "pipe:1"), true);
        if (run.exit != 0) return "ffmpeg could not make a test copy (" + run.describe() + ")";
        return null;
    }

    private static String versionFailure(Path program, String name, String version) throws InterruptedException {
        Run run = run(List.of(program.toString(), "-hide_banner", "-version"), false);
        if (run.exit != 0) return name + " -version failed (" + run.describe() + ")";
        String expected = name + " version " + version;
        if (!run.output.startsWith(expected)) return name + " is not " + version + " (" + run.firstLine() + ")";
        return null;
    }

    private record Run(int exit, String output) {
        String firstLine() {
            int end = output.indexOf('\n');
            return (end < 0 ? output : output.substring(0, end)).strip();
        }

        String describe() {
            String line = firstLine();
            return exit == Integer.MIN_VALUE ? "it did not finish" : exit == Integer.MAX_VALUE ? line
                    : "exit " + exit + (line.isEmpty() ? "" : ": " + line);
        }
    }

    private static Run run(List<String> command, boolean discardOutput) throws InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (discardOutput) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        } else {
            builder.redirectErrorStream(true);
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return new Run(Integer.MAX_VALUE, "could not start: " + e.getMessage());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InputStream stream = discardOutput ? process.getErrorStream() : process.getInputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = stream) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) >= 0; ) {
                    synchronized (output) {
                        if (output.size() < SELF_TEST_OUTPUT_LIMIT) output.write(buffer, 0, n);
                    }
                }
            } catch (IOException ignored) {
            }
        }, "Soundstage-ffmpeg-selftest");
        reader.setDaemon(true);
        reader.start();
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
        try {
            if (!process.waitFor(SELF_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return new Run(Integer.MIN_VALUE, "");
            reader.join(2_000);
            synchronized (output) {
                return new Run(process.exitValue(), output.toString(StandardCharsets.UTF_8));
            }
        } finally {
            process.destroyForcibly();
        }
    }

    private static String readQuietly(Path file) {
        try {
            String text = Files.readString(file).strip();
            return text.length() > 300 ? text.substring(0, 300) + "…" : text;
        } catch (IOException e) {
            return "unknown";
        }
    }

    static void deleteQuietly(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(path);
                return;
            }
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException e) throws IOException {
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }
}
