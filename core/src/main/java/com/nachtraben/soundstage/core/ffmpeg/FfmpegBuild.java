package com.nachtraben.soundstage.core.ffmpeg;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record FfmpegBuild(
        String id,
        String version,
        URI url,
        String sha256,
        long size,
        Archive archive,
        String root) {

    public enum Archive {
        TAR_XZ,
        ZIP
    }

    private static final String RELEASE = "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-08-31-13-27/";
    private static final String VERSION = "n9.0.1-11-ge47273f4d9";

    public static final FfmpegBuild LINUX_X64 = pinned("linux64", Archive.TAR_XZ, 60_154_956L,
            "30801e00d25f823c6da339378ea63c38e5265f237741909e1d6164b6b5bd3d33");
    public static final FfmpegBuild LINUX_ARM64 = pinned("linuxarm64", Archive.TAR_XZ, 51_638_564L,
            "d4689b273822c77b985d441618aa433da4b9bda370484c63b7ac963686dc7da6");
    public static final FfmpegBuild WINDOWS_X64 = pinned("win64", Archive.ZIP, 76_434_028L,
            "00d78694632f17a1de325c639d0acf04a6b3ab8f20ce0a2e5bedd2d5e21e3adb");
    public static final FfmpegBuild WINDOWS_ARM64 = pinned("winarm64", Archive.ZIP, 51_649_580L,
            "4419887ac2c5585d909d532944843756f67e430d4f3822e9062bff1455498abc");

    public static final List<FfmpegBuild> PINNED = List.of(LINUX_X64, LINUX_ARM64, WINDOWS_X64, WINDOWS_ARM64);

    private static FfmpegBuild pinned(String target, Archive archive, long size, String sha256) {
        String root = "ffmpeg-" + VERSION + "-" + target + "-gpl-shared-9.0";
        String file = root + (archive == Archive.ZIP ? ".zip" : ".tar.xz");
        return new FfmpegBuild(VERSION + "-" + target + "-gpl-shared", VERSION, URI.create(RELEASE + file), sha256,
                size, archive, root);
    }

    public static Optional<FfmpegBuild> forPlatform(String osName, String osArch) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        if (os.startsWith("linux")) {
            if (x64) return Optional.of(LINUX_X64);
            if (arm64) return Optional.of(LINUX_ARM64);
        } else if (os.startsWith("windows")) {
            if (x64) return Optional.of(WINDOWS_X64);
            if (arm64) return Optional.of(WINDOWS_ARM64);
        }
        return Optional.empty();
    }

    public static Optional<FfmpegBuild> forThisPlatform() {
        return forPlatform(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    public boolean windows() {
        return archive == Archive.ZIP;
    }

    public String ffmpegName() {
        return windows() ? "ffmpeg.exe" : "ffmpeg";
    }

    public String ffprobeName() {
        return windows() ? "ffprobe.exe" : "ffprobe";
    }

    public long megabytes() {
        return (size + 999_999) / 1_000_000;
    }
}
