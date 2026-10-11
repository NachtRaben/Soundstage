package com.nachtraben.soundstage.core.video;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class FfmpegCommand {

    static final double MAX_FRAME_RATE = 30.5;

    static final String KEYFRAMES_BY_TIME = "expr:isnan(prev_forced_t)+gte(t-prev_forced_t,1.9)";

    static final String LIVE_ANALYZE_MICROS = "1000000";

    static final String URL_PROTOCOLS = "https,tls,tcp";

    public static final String FILE_PROTOCOLS = "file";

    static final String URL_READ_TIMEOUT_MICROS = "10000000";

    private static final Pattern CERTIFICATE_REFUSED = Pattern.compile(
            "(?i).*certificate.*(verif|not trusted|untrusted|does not match|self.signed|unknown ca|expired).*");

    static final String AT_MOST_30_FPS = "select='isnan(prev_selected_t)+gte(t-prev_selected_t,0.03)'";

    static final String SILENCE = "anullsrc=r=48000:cl=stereo";

    static final String SILENCE_LABEL = "[silence]";

    static final String STILL_COLOUR = ":out_color_matrix=bt709:out_range=tv";

    static final String STILL_LOOP = "format=yuv420p,loop=loop=-1:size=1:start=0,setpts=N/TB";

    static final String STILL_QUALITY = "20";

    static final String TONE_MAP = "t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,"
            + "tonemap=tonemap=mobius:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p";

    public static final List<String> TONE_MAP_FILTERS = List.of("zscale", "tonemap");

    private static final Pattern URL_IN_TEXT = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://\\S+");

    private FfmpegCommand() {}

    public static List<String> version(String ffmpeg) {
        return List.of(ffmpeg, "-version");
    }

    public static List<String> filters(String ffmpeg) {
        return List.of(ffmpeg, "-hide_banner", "-filters");
    }

    public static List<String> missingToneMapFilters(String listing) {
        Set<String> present = new LinkedHashSet<>();
        for (String line : listing.split("\\R")) {
            String[] words = line.strip().split("\\s+");
            if (words.length >= 3 && words[2].contains("->")) present.add(words[1]);
        }
        List<String> missing = new ArrayList<>();
        for (String filter : TONE_MAP_FILTERS) {
            if (!present.contains(filter)) missing.add(filter);
        }
        return List.copyOf(missing);
    }

    public static String input(Path file) {
        return "file:" + file.toAbsolutePath().normalize();
    }

    public static List<String> ffprobeCandidates(String ffmpeg) {
        Set<String> candidates = new LinkedHashSet<>();
        int cut = Math.max(ffmpeg.lastIndexOf('/'), ffmpeg.lastIndexOf('\\')) + 1;
        String name = ffmpeg.substring(cut);
        int at = name.toLowerCase(Locale.ROOT).lastIndexOf("ffmpeg");
        if (at >= 0) {
            candidates.add(ffmpeg.substring(0, cut) + name.substring(0, at) + "ffprobe"
                    + name.substring(at + "ffmpeg".length()));
        }
        candidates.add("ffprobe");
        return List.copyOf(candidates);
    }

    public static List<String> transcode(String ffmpeg, String input, long startMillis, PictureSize.Copy picture,
                                         MediaProbe probe) {
        return transcode(ffmpeg, input, startMillis, picture, probe, true);
    }

    public static List<String> transcode(String ffmpeg, String input, long startMillis, PictureSize.Copy picture,
                                         MediaProbe probe, boolean toneMap) {
        return transcodeOf(ffmpeg, input, startMillis, picture, probe, toneMap);
    }

    private static List<String> transcodeOf(String ffmpeg, String input, long startMillis, PictureSize.Copy picture,
                                            MediaProbe probe, boolean toneMap) {
        Optional<MediaProbe.Sound> sidecar = probe.surround() ? probe.surroundSound() : Optional.empty();
        boolean silent = !probe.hasAudio();
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin",
                "-ss", seconds(startMillis), "-protocol_whitelist", FILE_PROTOCOLS, "-i", input);
        if (probe.hasVideo()) {
            Collections.addAll(args, "-map", "0:v:0?");
        }
        if (silent) {
            Collections.addAll(args, "-filter_complex", SILENCE + SILENCE_LABEL, "-map", SILENCE_LABEL);
            if (probe.durationMillis() != MediaProbe.UNKNOWN_DURATION) {
                Collections.addAll(args, "-t", seconds(probe.durationMillis() - startMillis));
            }
            if (probe.hasVideo()) args.add("-shortest");
        } else if (sidecar.isPresent()) {
            String map = "0:" + sidecar.get().index();
            Collections.addAll(args, "-map", map, "-map", map);
        } else {
            Collections.addAll(args, "-map", probe.sound().map(sound -> "0:" + sound.index()).orElse("0:a:0?"));
        }
        if (probe.hasVideo()) {
            Objects.requireNonNull(picture, "a copy with a picture needs its size");
            String filter = scale(picture);
            if (probe.videoFrameRate() > MAX_FRAME_RATE) {
                filter += ",fps=30";
            }
            if (toneMap && probe.colour().isHdr()) {
                filter += ",zscale=" + probe.colour().zscaleInput() + ":" + TONE_MAP;
            }
            Collections.addAll(args, "-vf", filter);
            addPictureEncoding(args, picture);
        }
        if (sidecar.isPresent()) {
            addSurroundEncodingAndOutput(args);
        } else {
            addSoundEncodingAndOutput(args);
        }
        return args;
    }

    public static List<String> silence(String ffmpeg, long startMillis, long durationMillis) {
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin",
                "-filter_complex", SILENCE + SILENCE_LABEL, "-map", SILENCE_LABEL);
        if (durationMillis != MediaProbe.UNKNOWN_DURATION) {
            Collections.addAll(args, "-t", seconds(durationMillis - startMillis));
        }
        addSoundEncodingAndOutput(args);
        return args;
    }

    public static List<String> still(String ffmpeg, String input, StillImage.Type type, PictureSize.Copy picture) {
        Objects.requireNonNull(picture, "an image's copy needs its size");
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin",
                "-max_pixels", Long.toString(StillImage.MAX_PIXELS),
                "-protocol_whitelist", FILE_PROTOCOLS, "-f", type.demuxer, "-i", input,
                "-filter_complex", "[0:v]" + scale(picture) + STILL_COLOUR + "," + STILL_LOOP + "[picture];"
                        + SILENCE + SILENCE_LABEL,
                "-map", "[picture]", "-map", SILENCE_LABEL, "-r", "1",
                "-c:v", "libx264", "-preset", "veryfast", "-tune", "zerolatency",
                "-profile:v", "main", "-pix_fmt", "yuv420p", "-g", "1", "-bf", "0",
                "-colorspace", "bt709", "-color_range", "tv",
                "-x264-params", X264_PARAMS,
                "-crf", STILL_QUALITY, "-maxrate", picture.kbps() + "k", "-bufsize", picture.kbps() + "k");
        addSoundEncodingAndOutput(args);
        return args;
    }

    public static boolean hasSurroundSidecar(List<String> command) {
        return command.contains("-ac:a:1");
    }

    public static List<String> subtitles(String ffmpeg, String input, int streamIndex) {
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin",
                "-protocol_whitelist", FILE_PROTOCOLS, "-i", input,
                "-map", "0:" + streamIndex, "-f", "webvtt", "pipe:1");
        return args;
    }

    public static List<String> transcodeUrls(String ffmpeg, YoutubeStreams.Streams streams, long startMillis,
                                              PictureSize.Copy picture) {
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin");
        addUrlInput(args, streams.video(), streams.videoContainer(), startMillis);
        addUrlInput(args, streams.audio(), streams.audioContainer(), startMillis);
        Collections.addAll(args, "-map", "0:v:0", "-map", "1:a:0");
        if (streams.surround()) Collections.addAll(args, "-map", "1:a:0");
        Collections.addAll(args, "-shortest",
                "-vf", AT_MOST_30_FPS + "," + scale(picture));
        addPictureEncoding(args, picture);
        if (streams.surround()) {
            addSurroundEncodingAndOutput(args);
        } else {
            addSoundEncodingAndOutput(args);
        }
        return args;
    }

    public static List<String> transcodeSoundUrl(String ffmpeg, YoutubeStreams.Streams streams, long startMillis) {
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin");
        addUrlInput(args, streams.audio(), streams.audioContainer(), startMillis);
        Collections.addAll(args, "-map", "0:a:0");
        if (streams.surround()) {
            Collections.addAll(args, "-map", "0:a:0");
            addSurroundEncodingAndOutput(args);
        } else {
            addSoundEncodingAndOutput(args);
        }
        return args;
    }

    public static String withoutUrls(String line) {
        return URL_IN_TEXT.matcher(line).replaceAll("<url>");
    }

    public static boolean saysCertificateRefused(String line) {
        return CERTIFICATE_REFUSED.matcher(line).matches();
    }

    private static void addUrlInput(List<String> args, URI url, StreamContainer container, long startMillis) {
        if (!YoutubeStreamUrls.isAcceptable(url)) {
            throw new IllegalArgumentException("not a YouTube stream URL ffmpeg may fetch");
        }
        Collections.addAll(args, "-protocol_whitelist", URL_PROTOCOLS, "-tls_verify", "1",
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-rw_timeout", URL_READ_TIMEOUT_MICROS,
                "-f", container.demuxer(), "-ss", seconds(startMillis), "-i", url.toASCIIString());
    }

    private static String scale(PictureSize.Copy picture) {
        String fit = "min(1,min(" + picture.width() + "/iw," + picture.height() + "/ih))";
        return "scale='" + side("iw", fit) + "':'" + side("ih", fit) + "'";
    }

    private static String side(String size, String fit) {
        String rounded = "2*round(" + size + "*" + fit + "/2)";
        return "trunc(min(" + size + "," + rounded + ")/2)*2";
    }

    static final int SLICES = 4;
    static final String X264_PARAMS = "repeat-headers=1:threads=" + SLICES + ":slices=" + SLICES;

    private static void addPictureEncoding(List<String> args, PictureSize.Copy picture) {
        int kbps = picture.kbps();
        Collections.addAll(args,
                "-c:v", "libx264", "-preset", "veryfast", "-tune", "zerolatency",
                "-profile:v", "main", "-pix_fmt", "yuv420p", "-g", "60", "-bf", "0",
                "-force_key_frames", KEYFRAMES_BY_TIME,
                "-x264-params", X264_PARAMS,
                "-b:v", kbps + "k", "-maxrate", kbps * 5 / 4 + "k", "-bufsize", kbps * 2 + "k");
    }

    public static List<String> liveReencode(String ffmpeg, PictureSize.Copy picture) {
        List<String> args = new ArrayList<>();
        args.add(ffmpeg);
        Collections.addAll(args, "-hide_banner", "-loglevel", "error", "-nostdin", "-copyts",
                "-analyzeduration", LIVE_ANALYZE_MICROS, "-protocol_whitelist", "pipe", "-f", "mpegts", "-i", "pipe:0",
                "-map", "0:v:0", "-vf", AT_MOST_30_FPS + "," + scale(picture));
        addPictureEncoding(args, picture);
        Collections.addAll(args, "-f", "mpegts", "-muxdelay", "0", "-muxpreload", "0", "pipe:1");
        return args;
    }

    public static int pictureBitrateKbps(int height) {
        if (height <= 480) return Math.max(400, height * 2000 / 480);
        if (height <= 720) return 2000 + (height - 480) * 2000 / 240;
        return Math.min(6000, 4000 + (height - 720) * 2000 / 360);
    }

    private static void addSurroundEncodingAndOutput(List<String> args) {
        Collections.addAll(args, "-c:a", "aac", "-ar", "48000",
                "-b:a:0", "160k", "-ac:a:0", "2",
                "-b:a:1", "384k", "-ac:a:1", "6",
                "-f", "mpegts", "-muxdelay", "0", "-muxpreload", "0", "pipe:1");
    }

    private static void addSoundEncodingAndOutput(List<String> args) {
        Collections.addAll(args, "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "2",
                "-f", "mpegts", "-muxdelay", "0", "-muxpreload", "0", "pipe:1");
    }

    private static String seconds(long millis) {
        long ms = Math.max(0, millis);
        return (ms / 1000) + "." + String.format(Locale.ROOT, "%03d", ms % 1000);
    }
}
