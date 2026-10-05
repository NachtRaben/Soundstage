package com.nachtraben.soundstage.core;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class BandcampHostSource extends BandcampAudioSourceManager {

    private static final String SEARCH_PREFIX = "bcsearch:";

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier != null && !identifier.startsWith(SEARCH_PREFIX) && !isBandcampUrl(identifier)) {
            return null;
        }
        return super.loadItem(manager, reference);
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return isBandcampUrl(trackInfo.identifier) ? super.decodeTrack(trackInfo, input) : null;
    }

    public static boolean isBandcampUrl(String url) {
        if (url == null || PrivateNetworkAccess.unreadableHost(url)) return false;
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null) return false;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            return host.equals("bandcamp.com") || host.endsWith(".bandcamp.com");
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
