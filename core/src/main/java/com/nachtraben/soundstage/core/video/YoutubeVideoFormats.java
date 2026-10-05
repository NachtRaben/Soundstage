package com.nachtraben.soundstage.core.video;

import com.sedmelluq.discord.lavaplayer.tools.Units;
import dev.lavalink.youtube.track.format.StreamFormat;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

public final class YoutubeVideoFormats {

    public record Choice(StreamFormat video, int videoHeight, StreamFormat audio) {}

    static final Map<Integer, Integer> HEIGHTS = Map.ofEntries(
            entry(160, 144), entry(133, 240), entry(134, 360), entry(135, 480), entry(212, 480),
            entry(136, 720), entry(298, 720), entry(137, 1080), entry(299, 1080), entry(264, 1440),
            entry(266, 2160),
            entry(278, 144), entry(242, 240), entry(243, 360), entry(244, 480), entry(245, 480),
            entry(246, 480), entry(247, 720), entry(302, 720), entry(248, 1080), entry(303, 1080),
            entry(271, 1440), entry(308, 1440), entry(313, 2160), entry(315, 2160), entry(272, 2160),
            entry(394, 144), entry(395, 240), entry(396, 360), entry(397, 480), entry(398, 720),
            entry(399, 1080), entry(400, 1440), entry(401, 2160));

    private static final int NOT_A_PICTURE = -1;

    private YoutubeVideoFormats() {}

    public static Optional<Choice> choose(List<StreamFormat> formats, int targetHeight) {
        return choose(formats, targetHeight, false);
    }

    public static Optional<Choice> choose(List<StreamFormat> formats, int targetHeight, boolean surround) {
        List<StreamFormat> sounds = formats.stream().filter(YoutubeVideoFormats::isSound).toList();
        boolean anyDefault = sounds.stream().anyMatch(StreamFormat::isDefaultAudioTrack);
        Comparator<StreamFormat> soundOrder = Comparator.comparing(StreamFormat::isDrc)
                .thenComparing(f -> surround ? f.getAudioChannels() < 6 : f.getAudioChannels() > 2)
                .thenComparing(Comparator.comparingLong(StreamFormat::getBitrate).reversed());
        Optional<StreamFormat> audio = sounds.stream()
                .filter(f -> !anyDefault || f.isDefaultAudioTrack())
                .min(soundOrder);
        if (audio.isEmpty()) return Optional.empty();

        List<StreamFormat> pictures = formats.stream().filter(f -> codecRank(f) != NOT_A_PICTURE).toList();
        if (pictures.isEmpty()) return Optional.empty();
        boolean anyFits = pictures.stream().anyMatch(f -> heightOf(f) <= targetHeight);
        Comparator<StreamFormat> byHeight = anyFits
                ? Comparator.comparingInt(YoutubeVideoFormats::heightOf).reversed()
                : Comparator.comparingInt(YoutubeVideoFormats::heightOf);
        StreamFormat video = pictures.stream()
                .filter(f -> !anyFits || heightOf(f) <= targetHeight)
                .min(byHeight.thenComparingInt(YoutubeVideoFormats::codecRank)
                        .thenComparingLong(YoutubeVideoFormats::bitrateOrMost))
                .orElseThrow();
        return Optional.of(new Choice(video, heightOf(video), audio.get()));
    }

    static int heightOf(StreamFormat format) {
        return HEIGHTS.getOrDefault(format.getItag(), 0);
    }

    static int codecRank(StreamFormat format) {
        if (!hasLength(format) || heightOf(format) == 0) return NOT_A_PICTURE;
        String mime = mimeOf(format);
        String codec = codecsOf(format);
        if (codec.contains(",")) return NOT_A_PICTURE;
        if (mime.equals("video/mp4") && codec.startsWith("avc1.")) return 0;
        if (mime.equals("video/webm") && (codec.equals("vp9") || codec.startsWith("vp09.00."))) return 1;
        if (mime.equals("video/mp4") && codec.startsWith("av01.")) {
            String[] parts = codec.split("\\.");
            return parts.length >= 4 && parts[3].equals("08") ? 2 : NOT_A_PICTURE;
        }
        return NOT_A_PICTURE;
    }

    static boolean isSound(StreamFormat format) {
        if (!hasLength(format)) return false;
        String mime = mimeOf(format);
        String codec = codecsOf(format);
        return mime.equals("audio/mp4") && codec.startsWith("mp4a.")
                || mime.equals("audio/webm") && codec.equals("opus");
    }

    private static boolean hasLength(StreamFormat format) {
        long length = format.getContentLength();
        return length > 0 && length != Units.CONTENT_LENGTH_UNKNOWN;
    }

    private static long bitrateOrMost(StreamFormat format) {
        long bitrate = format.getBitrate();
        return bitrate < 0 ? Long.MAX_VALUE : bitrate;
    }

    private static String mimeOf(StreamFormat format) {
        return format.getType().getMimeType().toLowerCase(Locale.ROOT);
    }

    private static String codecsOf(StreamFormat format) {
        String codecs = format.getType().getParameter("codecs");
        return codecs == null ? "" : codecs.strip().toLowerCase(Locale.ROOT);
    }
}
