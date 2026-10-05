package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.IosWithThumbnail;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.skeleton.Client;

import java.util.ArrayList;
import java.util.List;

public final class AudioSourceRegistry {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final String CLIENT_PACKAGE = Tv.class.getPackageName() + ".";
    private static final String CONFIG_CLIENT_PACKAGE =
            String.join(".", "dev", "lavalink", "youtube", "clients") + ".";

    private AudioSourceRegistry() {}

    public record Result(AudioPlayerManager playerManager, YoutubeAudioSourceManager youtubeSource) {}

    @FunctionalInterface
    public interface YoutubeSourceFactory {
        YoutubeAudioSourceManager create(boolean allowSearch, Client[] clients);
    }

    public static Result create(AudioSourceSettings settings) {
        return create(settings, List.of());
    }

    public static Result create(AudioSourceSettings settings, List<? extends AudioSourceManager> first) {
        return create(settings, first, YoutubeAudioSourceManager::new);
    }

    public static Result create(AudioSourceSettings settings, List<? extends AudioSourceManager> first,
                                YoutubeSourceFactory youtubeFactory) {
        return create(settings, first, youtubeFactory, TwitchStreamAudioSourceManager::new,
                ScreenedHttpSource::new);
    }

    @FunctionalInterface
    public interface HttpSourceFactory {
        ScreenedHttpSource create(boolean admitted, dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.DnsResolver screen);
    }

    public static Result create(AudioSourceSettings settings, List<? extends AudioSourceManager> first,
                                YoutubeSourceFactory youtubeFactory,
                                java.util.function.Supplier<? extends TwitchStreamAudioSourceManager> twitchFactory,
                                HttpSourceFactory httpFactory) {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        for (AudioSourceManager source : first) {
            manager.registerSourceManager(source);
        }

        manager.getConfiguration().setOutputFormat(
                new Pcm16AudioDataFormat(
                        2,
                        48000,
                        960,
                        true
                )
        );

        YoutubeAudioSourceManager youtube = settings.enableYoutube()
                ? registerYoutube(manager, settings, youtubeFactory) : null;

        if (settings.enableSoundCloud()) {
            manager.registerSourceManager(publicOnly(SoundCloudAudioSourceManager.createDefault()));
        }
        if (settings.enableBandcamp()) {
            manager.registerSourceManager(publicOnly(new BandcampHostSource()));
        }
        if (settings.enableVimeo()) {
            manager.registerSourceManager(publicOnly(new VimeoAudioSourceManager()));
        }
        if (settings.enableTwitch()) {
            manager.registerSourceManager(publicOnly(twitchFactory.get()));
        }

        if (settings.enableHttp()) {
            manager.registerSourceManager(httpFactory.create(true, new ScreenedDnsResolver(
                    dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.impl.conn.SystemDefaultDnsResolver.INSTANCE,
                    address -> settings.allowPrivateNetworkHosts() || UrlPolicy.isPubliclyRoutable(address))));
            manager.registerSourceManager(httpFactory.create(false, ScreenedDnsResolver.publicOnly()));
        }

        return new Result(manager, youtube);
    }

    private static YoutubeAudioSourceManager registerYoutube(AudioPlayerManager manager, AudioSourceSettings settings,
                                                             YoutubeSourceFactory factory) {
        String refreshToken = java.util.Objects.requireNonNullElse(settings.oauthRefreshToken(), "");

        List<String> requested = settings.youtubeClients();
        if (requested == null || requested.isEmpty()) requested = AudioSourceSettings.DEFAULT_YOUTUBE_CLIENTS;
        List<Client> clients = resolveClients(requested);

        if (!refreshToken.isEmpty()) {
            clients.add(new Tv());
        }

        if (clients.isEmpty()) {
            LOGGER.error("Soundstage: YouTube is enabled but not one client in {} could be resolved "
                    + "against this youtube-source — YouTube playback will not work. Check the "
                    + "youtubeClients config value for typos.", requested);
        }

        YoutubeAudioSourceManager youtube =
                factory.create(settings.allowYoutubeSearch(), clients.toArray(new Client[0]));

        if (!refreshToken.isEmpty()) {
            youtube.useOauth2(refreshToken, true);
        }

        String poToken = java.util.Objects.requireNonNullElse(settings.poToken(), "");
        String visitorData = java.util.Objects.requireNonNullElse(settings.visitorData(), "");
        if (!poToken.isEmpty() && !visitorData.isEmpty()) {
            Web.setPoTokenAndVisitorData(poToken, visitorData);
        }

        youtube.getHttpInterfaceManager().configureBuilder(
                builder -> ScreenedConnections.install(builder, ScreenedDnsResolver.platform(youtube.getSourceName())));
        manager.registerSourceManager(youtube);
        return youtube;
    }

    static <T extends com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
            & com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable> T publicOnly(T source) {
        source.configureBuilder(builder -> ScreenedConnections.install(builder, ScreenedDnsResolver.platform(source.getSourceName())));
        return source;
    }

    static List<Client> resolveClients(List<String> names) {
        List<Client> clients = new ArrayList<>();
        for (String name : names) {
            String className = name.startsWith(CONFIG_CLIENT_PACKAGE)
                    ? CLIENT_PACKAGE + name.substring(CONFIG_CLIENT_PACKAGE.length())
                    : name.contains(".") ? name : CLIENT_PACKAGE + name;
            try {
                Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
                if (instance.getClass() == IosWithThumbnail.class) {
                    instance = new CompatibleIosWithThumbnail();
                }
                if (instance instanceof Client client) {
                    clients.add(client);
                } else {
                    LOGGER.warn("Soundstage: '{}' is not a youtube-source client — skipping.", name);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.warn("Soundstage: YouTube client '{}' is not available in this youtube-source "
                        + "({}) — skipping.", name, e.toString());
            }
        }
        return clients;
    }
}
