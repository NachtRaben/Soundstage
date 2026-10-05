package com.nachtraben.soundstage.core.ffmpeg;

import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FfmpegArchive {

    static final long MAX_FILE_BYTES = 256L << 20;
    static final long MAX_TOTAL_BYTES = 768L << 20;

    private static final Pattern LIBRARY = Pattern.compile("lib[A-Za-z0-9_+-]+\\.so(\\.[0-9]+)*");
    private static final Pattern DLL = Pattern.compile("[A-Za-z0-9_+-][A-Za-z0-9_.+-]*\\.dll");
    static final String LICENCE = "LICENSE.txt";

    private FfmpegArchive() {}

    public static void extract(Path archive, FfmpegBuild build, Path into) throws IOException {
        Files.createDirectories(into);
        long[] total = {0};
        if (build.archive() == FfmpegBuild.Archive.ZIP) {
            extractZip(archive, build, into, total);
        } else {
            extractTarXz(archive, build, into, total);
        }
        Path ffmpeg = into.resolve("bin").resolve(build.ffmpegName());
        Path ffprobe = into.resolve("bin").resolve(build.ffprobeName());
        if (!Files.isRegularFile(ffmpeg, LinkOption.NOFOLLOW_LINKS)) throw new IOException("the archive holds no " + build.ffmpegName());
        if (!Files.isRegularFile(ffprobe, LinkOption.NOFOLLOW_LINKS)) throw new IOException("the archive holds no " + build.ffprobeName());
        if (!build.windows()) {
            executable(ffmpeg);
            executable(ffprobe);
        }
    }

    static String destination(String name, FfmpegBuild build, boolean symlink) {
        String prefix = build.root() + "/";
        if (!name.startsWith(prefix) || name.indexOf('\\') >= 0) return null;
        String relative = name.substring(prefix.length());
        if (relative.equals(LICENCE)) return symlink ? null : relative;
        int slash = relative.indexOf('/');
        if (slash < 0) return null;
        String directory = relative.substring(0, slash);
        String file = relative.substring(slash + 1);
        if (file.isEmpty() || file.indexOf('/') >= 0) return null;
        if (directory.equals("bin")) {
            if (symlink) return null;
            if (file.equals(build.ffmpegName()) || file.equals(build.ffprobeName())) return relative;
            if (build.windows() && DLL.matcher(file).matches()) return relative;
            return null;
        }
        if (directory.equals("lib") && !build.windows() && LIBRARY.matcher(file).matches()) return relative;
        return null;
    }

    private static void extractTarXz(Path archive, FfmpegBuild build, Path into, long[] total) throws IOException {
        List<String[]> links = new ArrayList<>();
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive), 1 << 16);
             InputStream xz = new XZInputStream(raw)) {
            TarReader tar = new TarReader(xz);
            for (TarReader.Entry entry; (entry = tar.next()) != null; ) {
                if (entry.type() == TarReader.Type.FILE) {
                    String relative = destination(entry.name(), build, false);
                    if (relative != null) write(tar.content(), into.resolve(relative), total);
                } else if (entry.type() == TarReader.Type.SYMLINK) {
                    String relative = destination(entry.name(), build, true);
                    if (relative == null) continue;
                    String target = entry.linkName();
                    if (target == null || !LIBRARY.matcher(target).matches()) {
                        throw new IOException("the archive links " + entry.name() + " somewhere unexpected");
                    }
                    links.add(new String[] {relative, target});
                }
            }
        }
        Map<String, String> linkTargets = new HashMap<>();
        for (String[] link : links) linkTargets.put(link[0], link[1]);
        for (String[] link : links) {
            Path at = into.resolve(link[0]);
            Path file = resolveLink(into, link[0], linkTargets);
            Files.deleteIfExists(at);
            try {
                Files.createSymbolicLink(at, Path.of(link[1]));
            } catch (UnsupportedOperationException | IOException e) {
                Files.copy(file, at, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path resolveLink(Path into, String relative, Map<String, String> linkTargets)
            throws IOException {
        String at = relative;
        for (int hops = 0; hops < 8; hops++) {
            String target = linkTargets.get(at);
            if (target == null) {
                Path file = into.resolve(at);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return file;
                break;
            }
            at = at.substring(0, at.lastIndexOf('/') + 1) + target;
        }
        throw new IOException("the archive links " + relative + " to a file it does not hold");
    }

    private static void extractZip(Path archive, FfmpegBuild build, Path into, long[] total) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String relative = destination(entry.getName(), build, false);
                if (relative == null) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    write(in, into.resolve(relative), total);
                }
            }
        }
    }

    private static void write(InputStream in, Path target, long[] total) throws IOException {
        Files.createDirectories(target.getParent());
        long written = 0;
        byte[] buffer = new byte[1 << 16];
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            for (int n; (n = in.read(buffer)) >= 0; ) {
                written += n;
                total[0] += n;
                if (written > MAX_FILE_BYTES) throw new IOException(target.getFileName() + " is larger than any ffmpeg file");
                if (total[0] > MAX_TOTAL_BYTES) throw new IOException("the archive unpacks larger than any ffmpeg build");
                out.write(buffer, 0, n);
            }
        }
        if (target.getFileName().toString().contains(".so")) executable(target);
    }

    private static void executable(Path file) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view != null) view.setPermissions(PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
