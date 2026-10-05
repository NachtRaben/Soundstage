package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record MediaProbe(long durationMillis, boolean hasAudio, boolean hasVideo, int videoHeight,
                         double videoFrameRate, List<Sound> sounds, VideoColour colour, boolean surround,
                         List<Subtitle> subtitles) {

    public static final long UNKNOWN_DURATION = Long.MAX_VALUE;

    public record Sound(int index, int channels, String language, boolean isDefault) {
        String languageKey() {
            return MediaProbe.languageKey(language);
        }
    }

    public MediaProbe {
        sounds = List.copyOf(sounds);
        subtitles = subtitles == null ? List.of() : List.copyOf(subtitles);
        colour = colour == null ? VideoColour.UNSTATED : colour;
    }

    public MediaProbe(long durationMillis, boolean hasAudio, boolean hasVideo, int videoHeight,
                      double videoFrameRate, List<Sound> sounds, VideoColour colour) {
        this(durationMillis, hasAudio, hasVideo, videoHeight, videoFrameRate, sounds, colour, false, List.of());
    }

    public MediaProbe(long durationMillis, boolean hasAudio, boolean hasVideo, int videoHeight,
                      double videoFrameRate, List<Sound> sounds, VideoColour colour, List<Subtitle> subtitles) {
        this(durationMillis, hasAudio, hasVideo, videoHeight, videoFrameRate, sounds, colour, false, subtitles);
    }

    public MediaProbe withSurround() {
        return new MediaProbe(durationMillis, hasAudio, hasVideo, videoHeight, videoFrameRate, sounds, colour, true,
                subtitles);
    }

    public MediaProbe(long durationMillis, boolean hasAudio, boolean hasVideo, int videoHeight,
                      double videoFrameRate, List<Sound> sounds) {
        this(durationMillis, hasAudio, hasVideo, videoHeight, videoFrameRate, sounds, VideoColour.UNSTATED);
    }

    public MediaProbe(long durationMillis, boolean hasAudio, boolean hasVideo, int videoHeight,
                      double videoFrameRate) {
        this(durationMillis, hasAudio, hasVideo, videoHeight, videoFrameRate, List.of());
    }

    public Optional<Sound> sound() {
        return choose(sounds);
    }

    public record Subtitle(int index, String codec, String language, boolean isDefault, boolean forced,
                           String title) {
        public Subtitle(int index, String codec, String language, boolean isDefault, boolean forced) {
            this(index, codec, language, isDefault, forced, null);
        }

        String languageKey() {
            return MediaProbe.languageKey(language);
        }

        public boolean isText() {
            return TEXT_CODECS.contains(codec == null ? "" : codec.toLowerCase(Locale.ROOT));
        }
    }

    private static final Set<String> TEXT_CODECS =
            Set.of("subrip", "ass", "ssa", "mov_text", "webvtt", "text");

    private static String languageKey(String language) {
        if (language == null || language.isBlank()) return null;
        String key = language.strip().toLowerCase(Locale.ROOT);
        return key.equals("und") ? null : key;
    }

    public Optional<Subtitle> subtitle(String playerLanguage) {
        List<Subtitle> text = subtitles.stream().filter(Subtitle::isText).toList();
        if (text.isEmpty()) return Optional.empty();
        List<Subtitle> notForced = text.stream().filter(s -> !s.forced()).toList();
        List<Subtitle> candidates = notForced.isEmpty() ? text : notForced;
        Optional<Subtitle> flaggedDefault = candidates.stream().filter(Subtitle::isDefault).findFirst();
        if (flaggedDefault.isPresent()) return flaggedDefault;
        if (com.nachtraben.soundstage.core.RigFaults.on("subtitle-first-track")) return Optional.of(candidates.get(0));
        return LanguageMatch.best(playerLanguage, notForced, Subtitle::language);
    }

    public Optional<Subtitle> subtitle() {
        return subtitle(null);
    }

    public List<Subtitle> textSubtitles() {
        return subtitles.stream().filter(Subtitle::isText).toList();
    }

    public boolean hasOnlyImageSubtitles() {
        return !subtitles.isEmpty() && subtitles.stream().noneMatch(Subtitle::isText);
    }

    public Optional<Sound> surroundSound() {
        if (sounds.isEmpty()) return Optional.empty();
        Sound main = sounds.stream().filter(Sound::isDefault).findFirst().orElse(sounds.get(0));
        String language = main.languageKey();
        return sounds.stream()
                .filter(sound -> sound.channels() >= 6 && Objects.equals(sound.languageKey(), language))
                .findFirst();
    }

    static Optional<Sound> choose(List<Sound> sounds) {
        if (sounds.isEmpty()) return Optional.empty();
        Sound main = sounds.stream().filter(Sound::isDefault).findFirst().orElse(sounds.get(0));
        String language = main.languageKey();
        return Optional.of(sounds.stream()
                .filter(sound -> sound.channels() == 2 && Objects.equals(sound.languageKey(), language))
                .findFirst()
                .orElse(main));
    }

    public static List<String> command(String ffprobe, String input) {
        return List.of(ffprobe, "-v", "error", "-protocol_whitelist", FfmpegCommand.FILE_PROTOCOLS,
                "-print_format", "json",
                "-show_entries", "format=duration"
                        + ":stream=index,codec_type,codec_name,height,avg_frame_rate,r_frame_rate,channels"
                        + ",color_transfer,color_primaries,color_space,color_range"
                        + ":stream_tags=language"
                        + ":stream_disposition=attached_pic,default,forced"
                        + ":stream_side_data=side_data_type,dv_profile,dv_bl_signal_compatibility_id",
                input);
    }

    public static MediaProbe parse(String json) throws IOException {
        JsonBrowser root = JsonBrowser.parse(json);
        if (root == null || !root.isMap()) {
            throw new IOException("ffprobe's answer is not a JSON object");
        }
        boolean audio = false;
        JsonBrowser video = null;
        List<Sound> sounds = new ArrayList<>();
        List<Subtitle> subtitles = new ArrayList<>();
        boolean described = true;
        for (JsonBrowser stream : root.get("streams").values()) {
            String type = stream.get("codec_type").safeText();
            if ("audio".equals(type)) {
                audio = true;
                int index = stream.get("index").asInt(-1);
                if (index < 0) described = false;
                sounds.add(new Sound(index, stream.get("channels").asInt(0), stream.get("tags").get("language").text(),
                        stream.get("disposition").get("default").asInt(0) == 1));
            } else if ("video".equals(type) && video == null) {
                video = stream;
            } else if ("subtitle".equals(type)) {
                subtitles.add(new Subtitle(stream.get("index").asInt(-1), stream.get("codec_name").text(),
                        stream.get("tags").get("language").text(),
                        stream.get("disposition").get("default").asInt(0) == 1,
                        stream.get("disposition").get("forced").asInt(0) == 1,
                        stream.get("tags").get("title").text()));
            }
        }
        if (!described) sounds.clear();
        boolean hasVideo = video != null && video.get("disposition").get("attached_pic").asInt(0) == 0;
        if (!hasVideo) {
            return new MediaProbe(durationOf(root), audio, false, 0, 0, sounds, VideoColour.UNSTATED, subtitles);
        }
        double rate = rate(video.get("avg_frame_rate").safeText());
        if (rate <= 0) rate = rate(video.get("r_frame_rate").safeText());
        return new MediaProbe(durationOf(root), audio, true, Math.max(0, video.get("height").asInt(0)), rate, sounds,
                colourOf(video), subtitles);
    }

    private static VideoColour colourOf(JsonBrowser video) {
        int profile = VideoColour.NO_DOLBY_VISION;
        int compatibility = -1;
        for (JsonBrowser side : video.get("side_data_list").values()) {
            if ("DOVI configuration record".equals(side.get("side_data_type").safeText())) {
                profile = side.get("dv_profile").asInt(VideoColour.NO_DOLBY_VISION);
                compatibility = side.get("dv_bl_signal_compatibility_id").asInt(-1);
                break;
            }
        }
        return new VideoColour(video.get("color_transfer").text(), video.get("color_primaries").text(),
                video.get("color_space").text(), video.get("color_range").text(), profile, compatibility);
    }

    private static long durationOf(JsonBrowser root) {
        String seconds = root.get("format").get("duration").safeText();
        try {
            double value = Double.parseDouble(seconds);
            if (!(value > 0) || Double.isInfinite(value)) return UNKNOWN_DURATION;
            return Math.round(value * 1000);
        } catch (NumberFormatException e) {
            return UNKNOWN_DURATION;
        }
    }

    private static double rate(String fraction) {
        int slash = fraction.indexOf('/');
        if (slash < 0) return 0;
        try {
            double numerator = Double.parseDouble(fraction.substring(0, slash));
            double denominator = Double.parseDouble(fraction.substring(slash + 1));
            double value = numerator / denominator;
            return value > 0 && !Double.isInfinite(value) ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
