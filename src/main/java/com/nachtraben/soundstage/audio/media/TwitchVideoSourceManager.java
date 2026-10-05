package com.nachtraben.soundstage.audio.media;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;

public class TwitchVideoSourceManager extends TwitchStreamAudioSourceManager {

    interface Segments extends AutoCloseable {
        InputStream next() throws IOException;

        @Override
        void close();
    }

    interface Streaming {
        Segments open(TwitchVideoSourceManager source, String channel, int maxHeight);
    }

    private static final Streaming REAL = (source, channel, maxHeight) -> {
        TwitchVariantSegmentUrlProvider provider = new TwitchVariantSegmentUrlProvider(channel, source, maxHeight);
        HttpInterface http = source.getHttpInterface();
        return new Segments() {
            @Override
            public InputStream next() {
                return provider.getNextSegmentStream(http);
            }

            @Override
            public void close() {
                try {
                    http.close();
                } catch (IOException ignored) {
                }
            }
        };
    };

    private final Streaming streaming;

    public TwitchVideoSourceManager() {
        this(REAL);
    }

    TwitchVideoSourceManager(Streaming streaming) {
        this.streaming = streaming;
    }

    Streaming streaming() {
        return streaming;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        AudioItem item = super.loadItem(manager, reference);
        return item instanceof TwitchStreamAudioTrack track ? new TwitchVideoTrack(track.getInfo(), this) : item;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new TwitchVideoTrack(trackInfo, this);
    }
}
