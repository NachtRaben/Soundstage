package com.nachtraben.soundstage.core.video;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class WebMediaCommand {

    static final String PROTOCOLS = "http,tcp";

    static final String DEMUXERS = "mov,matroska,mpegts";

    static final String READ_TIMEOUT_MICROS = "10000000";

    private WebMediaCommand() {}

    public static List<String> probe(String ffprobe, URI relay) {
        return forRelay(MediaProbe.command(ffprobe, input(relay)), relay);
    }

    public static List<String> transcode(String ffmpeg, URI relay, long startMillis, PictureSize.Copy picture,
                                         MediaProbe probe, boolean toneMap) {
        return forRelay(FfmpegCommand.transcode(ffmpeg, input(relay), startMillis, picture, probe, toneMap), relay,
                DEMUXERS);
    }

    public static List<String> still(String ffmpeg, URI relay, StillImage.Type type, PictureSize.Copy picture) {
        return forRelay(FfmpegCommand.still(ffmpeg, input(relay), type, picture), relay, type.demuxer);
    }

    private static List<String> forRelay(List<String> shipped, URI relay) {
        return forRelay(shipped, relay, DEMUXERS);
    }

    private static List<String> forRelay(List<String> shipped, URI relay, String demuxers) {
        String url = input(relay);
        int at = shipped.indexOf(url);
        if (at < 0 || shipped.lastIndexOf(url) != at || shipped.indexOf("-i") != shipped.lastIndexOf("-i")) {
            throw new IllegalStateException("the media file command no longer has the one input");
        }
        int input = at > 0 && shipped.get(at - 1).equals("-i") ? at - 1 : at;
        List<String> args = new ArrayList<>();
        boolean listed = false;
        for (int i = 0; i < shipped.size(); i++) {
            String arg = shipped.get(i);
            if (i == input) {
                if (!listed) {
                    args.add("-protocol_whitelist");
                    args.add(PROTOCOLS);
                }
                args.add("-format_whitelist");
                args.add(demuxers);
                args.add("-rw_timeout");
                args.add(READ_TIMEOUT_MICROS);
            }
            args.add(arg);
            if (arg.equals("-protocol_whitelist") && i < input) {
                args.add(PROTOCOLS);
                i++;
                listed = true;
            }
        }
        return args;
    }

    static String input(URI relay) {
        if (!LoopbackRelay.isRelayUrl(relay)) {
            throw new IllegalArgumentException("not a loopback relay URL ffmpeg may read");
        }
        return relay.toASCIIString();
    }
}
