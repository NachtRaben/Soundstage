package com.nachtraben.soundstage.config;

import com.nachtraben.soundstage.core.UrlPolicy;
import dev.armadeus.armalib.config.Configs;
import dev.armadeus.armalib.core.config.BooleanSetting;
import dev.armadeus.armalib.core.config.ConfigSpec;
import dev.armadeus.armalib.core.config.IntSetting;
import dev.armadeus.armalib.core.config.Scope;
import dev.armadeus.armalib.core.config.StringListSetting;
import dev.armadeus.armalib.core.config.StringSetting;

import java.util.List;

public final class SoundstageConfigSpec implements SoundstageSettings {

    private static final String REGISTRATION_ID = "soundstage_config_internal";

    public static final ConfigSpec SPEC;
    private static final Values VALUES;

    private static final SoundstageSettings D = new SoundstageSettings() {};

    static {
        ConfigSpec.Builder b = ConfigSpec.builder("soundstage", Scope.COMMON);
        VALUES = new Values(b);
        SPEC = Configs.register(REGISTRATION_ID, b.build());
    }

    private static final class Values {
        final StringSetting oauthRefreshToken;
        final StringSetting poToken;
        final StringSetting visitorData;
        final BooleanSetting allowYoutubeSearch;
        final StringListSetting youtubeClients;

        final BooleanSetting enableYoutube;
        final BooleanSetting enableSoundCloud;
        final BooleanSetting enableBandcamp;
        final BooleanSetting enableVimeo;
        final BooleanSetting enableTwitch;
        final BooleanSetting enableHttp;
        final BooleanSetting allowPrivateNetworkHosts;
        final BooleanSetting enableLiveInput;
        final BooleanSetting enableMediaFolder;

        final BooleanSetting enableVideo;
        final StringSetting ffmpegPath;
        final BooleanSetting downloadFfmpeg;
        final IntSetting maxVideoHeight;

        final StringListSetting urlAllowPatterns;
        final StringListSetting urlDenyPatterns;

        final IntSetting maxPlaylistTracks;
        final IntSetting maxQueueSize;
        final IntSetting maxPlayingReceivers;

        Values(ConfigSpec.Builder b) {
            b.push("general");
            oauthRefreshToken = b.define("oauthRefreshToken", D.oauthRefreshToken(), "YouTube OAuth Refresh Token");
            oauthRefreshToken.secret();
            poToken = b.define("poToken", D.poToken(), "YouTube PO Token");
            poToken.secret();
            visitorData = b.define("visitorData", D.visitorData(), "YouTube Visitor Data");
            visitorData.secret();
            allowYoutubeSearch = b.define("allowYoutubeSearch", D.allowYoutubeSearch(), "Allow YouTube Search");
            youtubeClients = b.defineList("youtubeClients", D.youtubeClients(),
                    "Which YouTube clients to try, in order, by youtube-source class name.",
                    "Exists as config because it expires: YouTube periodically breaks one client or",
                    "another, and the fix is a different order rather than a code change. An unknown",
                    "name is skipped with a warning, so a typo cannot take YouTube down entirely.",
                    "The Tv client is appended automatically when an OAuth token is configured.");
            b.pop();

            b.push("sources", "Which audio sources players are allowed to load from.");
            enableYoutube = b.define("enableYoutube", D.enableYoutube(), "Allow YouTube links");
            enableSoundCloud = b.define("enableSoundCloud", D.enableSoundCloud(), "Allow SoundCloud links");
            enableBandcamp = b.define("enableBandcamp", D.enableBandcamp(), "Allow Bandcamp links");
            enableVimeo = b.define("enableVimeo", D.enableVimeo(), "Allow Vimeo links");
            enableTwitch = b.define("enableTwitch", D.enableTwitch(), "Allow Twitch stream links");
            enableHttp = b.define("enableHttp", D.enableHttp(),
                    "Allow arbitrary direct HTTP(S) media URLs.",
                    "OFF by default, and the only source that is. Every other source resolves through a",
                    "known platform's API; this one makes the SERVER fetch whatever URL a player types,",
                    "which can be used to probe hosts only the server can reach (other containers, the",
                    "local network, cloud metadata endpoints). Enable only on a trusted player base, and",
                    "preferably alongside urlAllowPatterns.");
            allowPrivateNetworkHosts = b.define("allowPrivateNetworkHosts", D.allowPrivateNetworkHosts(),
                    "Permit direct HTTP URLs that resolve to loopback, private, or link-local addresses,",
                    "for operators only: other players are always refused, including through redirects",
                    "and playlists. Only consulted when enableHttp is true. Leave false unless you are",
                    "deliberately streaming from another machine on the same LAN.");
            enableLiveInput = b.define("enableLiveInput", D.enableLiveInput(),
                    "Allow players to share audio from their own computer through a receiver: an input",
                    "device on their machine, such as a virtual audio cable an app plays into. A share",
                    "is refused by a receiver's lock and counts toward maxPlayingReceivers like any track.");
            enableMediaFolder = b.define("enableMediaFolder", D.enableMediaFolder(),
                    "Allow media:<name> to play a file from the soundstage-media folder in the server's",
                    "directory (in singleplayer, the game directory). A player names a file directly in",
                    "that folder and nothing else: no paths, no subfolders, no hidden files.");
            b.pop();

            b.push("video", "How the server makes video for screens.");
            enableVideo = b.define("enableVideo", D.enableVideo(),
                    "Make video for screens at all. On by default. Off, screens stay black, a media",
                    "file with a picture plays its sound only, and YouTube plays the same sound-only",
                    "way it always does without a linked screen. Sound is never affected.");
            ffmpegPath = b.define("ffmpegPath", D.ffmpegPath(),
                    "The ffmpeg program: a name on the PATH, or a full path. ffprobe is looked for beside it,",
                    "then on the PATH. Optional: without it, media files play with sound only.");
            downloadFfmpeg = b.define("downloadFfmpeg", D.downloadFfmpeg(),
                    "Let Soundstage download ffmpeg when this server has none: a GPL build from",
                    "github.com/BtbN/FFmpeg-Builds (about 60 MB for Linux, 76 MB for Windows, 180 MB",
                    "unpacked), checked against the SHA-256 this version of Soundstage carries, test-run,",
                    "and kept in the soundstage-ffmpeg folder in the server's directory. It is only used",
                    "while ffmpegPath is \"ffmpeg\" and no ffmpeg runs from the PATH. Linux (x64, arm64,",
                    "glibc) and Windows (x64, arm64); not macOS. OFF by default: turning it on means this",
                    "server downloads and runs a program from the internet.");
            maxVideoHeight = b.defineInRange("maxVideoHeight", D.maxVideoHeight(), 144, 1080,
                    "Tallest picture sent to screens, in lines. Every client decodes it on its own",
                    "processor, so taller costs every viewer more.");
            b.pop();

            b.push("filters",
                    "Regex filters applied to the requested URL before it is resolved.",
                    "If allow is non-empty a URL must match at least one entry to be accepted, and deny",
                    "is not consulted. If allow is empty, any URL is accepted unless it matches deny.",
                    "Patterns are matched against the whole URL, case-insensitively.");
            urlAllowPatterns = b.defineList("urlAllowPatterns", D.urlAllowPatterns(),
                    "Example: [\"^https://(www\\\\.)?youtube\\\\.com/.*\"]");
            urlDenyPatterns = b.defineList("urlDenyPatterns", D.urlDenyPatterns(), "Example: [\".*\\\\.exe$\"]");
            b.pop();

            b.push("limits", "Bounds on queues, and on how many receivers may play at once.");
            maxPlaylistTracks = b.defineInRange("maxPlaylistTracks", D.maxPlaylistTracks(), 1, 1000,
                    "Most tracks one playlist link may enqueue. A watch URL carrying '&list=' or a search",
                    "result queues only the track the player actually selected, so this applies to genuine",
                    "playlist links.");
            maxQueueSize = b.defineInRange("maxQueueSize", D.maxQueueSize(), 1, 10000,
                    "Most tracks a single receiver may hold queued at once.");
            maxPlayingReceivers = b.defineInRange("maxPlayingReceivers", D.maxPlayingReceivers(), 0, 1024,
                    "Most receivers and party boxes that may play at once on this server; 0 means no limit.",
                    "Each one playing decodes, encodes and streams audio to everyone nearby, and fetches from",
                    "its source. A player asked to start one beyond this is told to stop another first.");
            b.pop();
        }
    }

    @Override public String oauthRefreshToken() { return VALUES.oauthRefreshToken.get(); }
    @Override public String poToken() { return VALUES.poToken.get(); }
    @Override public String visitorData() { return VALUES.visitorData.get(); }
    @Override public boolean allowYoutubeSearch() { return VALUES.allowYoutubeSearch.get(); }

    @Override public boolean enableYoutube() { return VALUES.enableYoutube.get(); }
    @Override public boolean enableSoundCloud() { return VALUES.enableSoundCloud.get(); }
    @Override public boolean enableBandcamp() { return VALUES.enableBandcamp.get(); }
    @Override public boolean enableVimeo() { return VALUES.enableVimeo.get(); }
    @Override public boolean enableTwitch() { return VALUES.enableTwitch.get(); }
    @Override public boolean enableHttp() { return VALUES.enableHttp.get(); }
    @Override public boolean allowPrivateNetworkHosts() { return VALUES.allowPrivateNetworkHosts.get(); }
    @Override public boolean enableLiveInput() { return VALUES.enableLiveInput.get(); }
    @Override public boolean enableMediaFolder() { return VALUES.enableMediaFolder.get(); }
    @Override public boolean enableVideo() { return VALUES.enableVideo.get(); }
    @Override public String ffmpegPath() { return VALUES.ffmpegPath.get(); }
    @Override public boolean downloadFfmpeg() { return VALUES.downloadFfmpeg.get(); }
    @Override public int maxVideoHeight() { return VALUES.maxVideoHeight.get(); }

    @Override public int maxPlaylistTracks() { return VALUES.maxPlaylistTracks.get(); }
    @Override public int maxQueueSize() { return VALUES.maxQueueSize.get(); }
    @Override public int maxPlayingReceivers() { return VALUES.maxPlayingReceivers.get(); }

    @Override
    public void setOauthRefreshToken(String token) {
        VALUES.oauthRefreshToken.set(token);
        save();
    }

    private static void save() {
        if (com.nachtraben.soundstage.RigStaging.fault("settings-unsaved")) return;
        try {
            Configs.save(SPEC);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("soundstage.aon could not be saved", e);
        }
    }

    @Override
    public List<String> youtubeClients() {
        return VALUES.youtubeClients.get();
    }

    @Override
    public List<String> urlAllowPatterns() {
        return VALUES.urlAllowPatterns.get();
    }

    @Override
    public List<String> urlDenyPatterns() {
        return VALUES.urlDenyPatterns.get();
    }

    static IntSetting maxPlayingReceiversSetting() {
        return VALUES.maxPlayingReceivers;
    }

    static BooleanSetting enableHttpSetting() {
        return VALUES.enableHttp;
    }

    public static void enableHttpForRigSession() {
        if (!com.nachtraben.soundstage.RigStaging.ACTIVE) throw new IllegalStateException("rig sessions only");
        VALUES.enableHttp.set(true);
    }

    static BooleanSetting allowPrivateNetworkHostsSetting() {
        return VALUES.allowPrivateNetworkHosts;
    }
}
