package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.core.video.Fmp4Init;
import com.nachtraben.soundstage.core.video.Fmp4ToTs;
import com.nachtraben.soundstage.core.video.TwitchVariants;
import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.methods.CloseableHttpResponse;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TwitchVariantSegmentUrlProvider extends TwitchStreamSegmentUrlProvider {

    static final int SOUND_ONLY = 0;
    private static final int MAX_INIT_BYTES = 1 << 20;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    static final class NoFittingPicture extends IllegalStateException {
        NoFittingPicture(String message) {
            super(message);
        }
    }

    private final String channel;
    private final int maxHeight;
    private String chosen;
    private Map<String, String> initFor = Map.of();
    private String initUrl;
    private Fmp4Init init;
    private final Fmp4ToTs repackager = new Fmp4ToTs();

    TwitchVariantSegmentUrlProvider(String channel, TwitchStreamAudioSourceManager manager, int maxHeight) {
        super(channel, manager);
        this.channel = channel;
        this.maxHeight = maxHeight;
    }

    @Override
    protected List<ChannelStreamInfo> loadChannelStreamsList(String[] lines) {
        List<ChannelStreamInfo> listed = super.loadChannelStreamsList(lines);
        if (maxHeight <= SOUND_ONLY) return listed;
        List<TwitchVariants.Variant> variants = TwitchVariants.parse(lines);
        Optional<TwitchVariants.Variant> choice = TwitchVariants.choose(variants, maxHeight);
        if (choice.isEmpty()) {
            throw new NoFittingPicture("Twitch channel " + channel + " offers no H.264 picture at most " + maxHeight
                    + " lines tall (" + describe(variants) + ")");
        }
        TwitchVariants.Variant variant = choice.get();
        for (ChannelStreamInfo stream : listed) {
            if (variant.name().equals(stream.quality)) {
                if (!variant.name().equals(chosen)) {
                    chosen = variant.name();
                    LOGGER.info("Soundstage: Twitch channel {} shows its picture from {} ({}x{} at {} fps) of {}.",
                            channel, variant.name(), variant.width(), variant.height(), variant.frameRate(),
                            describe(variants));
                }
                return List.of(stream);
            }
        }
        throw new NoFittingPicture("Twitch channel " + channel + ": LavaPlayer did not list rendition "
                + variant.name());
    }

    @Override
    protected List<SegmentInfo> loadStreamSegmentsList(HttpInterface http, String playlistUrl) throws IOException {
        List<SegmentInfo> segments = new ArrayList<>();
        Map<String, String> inits = new HashMap<>();
        ExtendedM3uParser.Line segmentInfo = null;
        String map = null;
        for (String text : HttpClientTools.fetchResponseLines(http, new HttpGet(playlistUrl), "stream segments list")) {
            ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(text);
            if (line.isDirective() && "EXTINF".equals(line.directiveName)) segmentInfo = line;
            if (line.isDirective() && "EXT-X-MAP".equals(line.directiveName)) {
                String uri = line.directiveArguments.get("URI");
                map = uri == null ? null : createSegmentUrl(playlistUrl, uri);
            }
            if (line.isData()) {
                if (segmentInfo != null && segmentInfo.extraData.contains(",")) {
                    String[] fields = segmentInfo.extraData.split(",", 2);
                    segments.add(new SegmentInfo(line.lineData, seconds(fields[0]), fields[1]));
                } else {
                    segments.add(new SegmentInfo(line.lineData, null, null));
                }
                if (map != null) inits.put(line.lineData, map);
            }
        }
        initFor = inits;
        return segments;
    }

    private static Long seconds(String value) {
        try {
            return (long) (Double.parseDouble(value) * 1000.0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public InputStream getNextSegmentStream(HttpInterface http) {
        InputStream segment = super.getNextSegmentStream(http);
        if (segment == null || lastSegment == null) return segment;
        String map = initFor.get(lastSegment.url);
        if (map == null) return segment;
        try {
            return repackager.convert(init(http, map), segment);
        } catch (IOException e) {
            try {
                segment.close();
            } catch (IOException ignored) {
            }
            throw new UncheckedIOException("Twitch channel " + channel + "'s init segment could not be read", e);
        }
    }

    private Fmp4Init init(HttpInterface http, String url) throws IOException {
        if (url.equals(initUrl) && init != null) return init;
        try (CloseableHttpResponse response = http.execute(createSegmentGetRequest(url))) {
            int status = response.getStatusLine().getStatusCode();
            if (!HttpClientTools.isSuccessWithContent(status)) throw new IOException("init segment status " + status);
            byte[] bytes;
            try (InputStream in = response.getEntity().getContent()) {
                bytes = in.readNBytes(MAX_INIT_BYTES + 1);
            }
            if (bytes.length > MAX_INIT_BYTES) throw new IOException("an init segment of over a megabyte");
            Fmp4Init parsed = Fmp4Init.parse(bytes);
            if (init == null) {
                LOGGER.info("Soundstage: Twitch channel {} is an enhanced broadcast, served as fragmented MP4; its "
                        + "segments are repackaged as MPEG-TS ({}).", channel,
                        parsed.video() == null ? "sound only" : "sound and picture");
            }
            init = parsed;
            initUrl = url;
            return parsed;
        }
    }

    private static String describe(List<TwitchVariants.Variant> variants) {
        List<String> names = new ArrayList<>();
        for (TwitchVariants.Variant variant : variants) names.add(variant.name());
        return String.join(", ", names);
    }
}
