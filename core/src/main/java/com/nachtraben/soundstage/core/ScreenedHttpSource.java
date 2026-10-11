package com.nachtraben.soundstage.core;

import com.nachtraben.soundstage.core.video.SilentVideoProbe;
import com.nachtraben.soundstage.core.video.StillImageProbe;
import com.sedmelluq.discord.lavaplayer.container.MediaContainer;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.container.playlists.HlsStreamTrack;
import com.sedmelluq.discord.lavaplayer.container.playlists.M3uPlaylistContainerProbe;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.conn.DnsResolver;

import java.util.ArrayList;
import java.util.List;

public class ScreenedHttpSource extends HttpAudioSourceManager {

    public static final String ADMITTED_SOURCE_NAME = "soundstage-http-admitted";

    private final boolean admitted;
    private final HttpInterfaceManager hls;

    public ScreenedHttpSource(boolean admitted, DnsResolver screen) {
        this(admitted, screen, hlsClient(screen));
    }

    private ScreenedHttpSource(boolean admitted, DnsResolver screen, HttpInterfaceManager hls) {
        super(registry(hls));
        this.admitted = admitted;
        this.hls = hls;
        configureBuilder(builder -> ScreenedConnections.install(builder, screen));
    }

    public boolean isAdmitted() {
        return admitted;
    }

    @Override
    public String getSourceName() {
        return admitted ? ADMITTED_SOURCE_NAME : super.getSourceName();
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (admitted && !(reference instanceof AdmittedReference)) return null;
        AudioItem item = super.loadItem(manager, reference);
        if (admitted && item instanceof AudioReference referral) return AdmittedReference.of(referral);
        return item;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        try {
            hls.close();
        } catch (Exception ignored) {
        }
    }

    private static HttpInterfaceManager hlsClient(DnsResolver screen) {
        HttpInterfaceManager client = new ThreadLocalHttpInterfaceManager(
                HttpClientTools.createSharedCookiesHttpBuilder()
                        .setRedirectStrategy(new HttpClientTools.NoRedirectsStrategy()),
                HttpClientTools.DEFAULT_REQUEST_CONFIG);
        client.configureBuilder(builder -> ScreenedConnections.install(builder, screen));
        return client;
    }

    private static MediaContainerRegistry registry(HttpInterfaceManager hls) {
        List<MediaContainerProbe> probes = new ArrayList<>();
        probes.add(new StillImageProbe());
        for (MediaContainerProbe probe : MediaContainer.asList()) {
            if (probe instanceof M3uPlaylistContainerProbe) {
                probes.add(new ScreenedM3uProbe(hls));
            } else if (SilentVideoProbe.CONTAINERS.contains(probe.getName())) {
                probes.add(new SilentVideoProbe(probe));
            } else {
                probes.add(probe);
            }
        }
        return new MediaContainerRegistry(probes);
    }

    static final class ScreenedM3uProbe extends M3uPlaylistContainerProbe {
        static final String HLS_OUTER = "hls-outer";
        static final String HLS_INNER = "hls-inner";

        private final HttpInterfaceManager hls;

        ScreenedM3uProbe(HttpInterfaceManager hls) {
            this.hls = hls;
        }

        @Override
        public AudioTrack createTrack(String parameters, AudioTrackInfo trackInfo, SeekableInputStream inputStream) {
            if (HLS_INNER.equals(parameters)) return new HlsStreamTrack(trackInfo, trackInfo.identifier, hls, true);
            if (HLS_OUTER.equals(parameters)) return new HlsStreamTrack(trackInfo, trackInfo.identifier, hls, false);
            throw new IllegalArgumentException("Unsupported parameters: " + parameters);
        }
    }
}
