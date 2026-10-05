package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.LiveVideoStamper;
import com.nachtraben.soundstage.core.video.TsDemuxer;
import com.nachtraben.soundstage.core.video.TsVideoTee;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.ChainedInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

public final class TwitchVideoTrack extends TwitchStreamAudioTrack implements VideoCapableTrack {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final int TS_SYNC_BYTE = 0x47;

    private final TwitchVideoSourceManager sourceManager;

    private volatile VideoSink videoSink;
    private volatile int targetHeight;
    private volatile boolean stopped;

    TwitchVideoTrack(AudioTrackInfo trackInfo, TwitchVideoSourceManager sourceManager) {
        super(trackInfo, sourceManager);
        this.sourceManager = sourceManager;
    }

    @Override
    public void setVideoSink(VideoSink sink) {
        videoSink = sink;
    }

    @Override
    public void setTargetHeight(int height) {
        targetHeight = height;
    }

    int pictureHeight() {
        int cap = SoundstageConfig.maxVideoHeight();
        int asked = targetHeight;
        return asked > 0 ? Math.min(asked, cap) : cap;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        VideoSink sink = videoSink;
        boolean showing = sink != null && sink.hasScreens() && sink.wantsPicture();
        if (!showing && sink != null) sink.abandon();
        Picture picture = showing ? new Picture() : null;
        int height = showing ? pictureHeight() : TwitchVariantSegmentUrlProvider.SOUND_ONLY;
        Throwable failure;
        try (TwitchVideoSourceManager.Segments segments =
                     sourceManager.streaming().open(sourceManager, getChannelName(), height)) {
            if (picture != null) picture.begin();
            boolean[] ranOut = {false};
            try (ChainedInputStream joined = new ChainedInputStream(() -> {
                InputStream next = segments.next();
                if (next == null) ranOut[0] = true;
                return tap(next, picture);
            })) {
                processJoinedStream(executor, joined);
            }
            if (picture != null && (ranOut[0] || !stopped)) picture.end();
            return;
        } catch (Exception | LinkageError e) {
            failure = e;
        }
        if (stopped) return;
        String why = reasons(failure);
        if (picture != null) {
            LOGGER.warn("Soundstage: Twitch channel {} plays on with sound only{}: {}", getChannelName(),
                    picture.stamper.heardAudio() ? "" : " from the start", why);
            SoundOnlyFallbacks.report(trackInfo.identifier, why);
            picture.abandon();
        } else {
            LOGGER.warn("Soundstage: Twitch channel {} plays on through LavaPlayer's own path: {}", getChannelName(), why);
        }
        super.process(executor);
    }

    private static InputStream tap(InputStream segment, Picture picture) throws IOException {
        if (segment == null) return null;
        PushbackInputStream peek = new PushbackInputStream(segment, 1);
        int first = peek.read();
        if (first >= 0) {
            peek.unread(first);
            if (first != TS_SYNC_BYTE) {
                peek.close();
                throw new IOException("its segments are not MPEG-TS (first byte 0x" + Integer.toHexString(first) + ")");
            }
        }
        return picture == null ? peek : new TsVideoTee(peek, new TsDemuxer(picture.stamper));
    }

    @Override
    public void stop() {
        stopped = true;
        super.stop();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new TwitchVideoTrack(trackInfo, sourceManager);
    }

    private static String reasons(Throwable failure) {
        Throwable first = failure instanceof FriendlyException && failure.getCause() != null
                ? failure.getCause() : failure;
        StringBuilder text = new StringBuilder();
        for (Throwable at = first; at != null && text.length() < 600; at = at.getCause()) {
            String message = at.getMessage() == null ? at.getClass().getSimpleName() : at.getMessage();
            if (at.getCause() != null && message.equals(at.getCause().toString())) continue;
            if (!text.isEmpty()) text.append(" <- ");
            text.append(message);
            if (at.getCause() == at) break;
        }
        return com.nachtraben.soundstage.core.video.FfmpegCommand.withoutUrls(text.toString());
    }

    private final class Picture implements LiveVideoStamper.Output {
        final LiveVideoStamper stamper = new LiveVideoStamper(this);
        private int generation;
        private long startMillis;
        private VideoSink told;
        private boolean complained;
        private boolean ended;

        void begin() {
            generation++;
            startMillis = 0;
            told = null;
            VideoSink sink = videoSink;
            if (sink != null) tell(sink);
        }

        @Override
        public void restart(long millis) {
            generation++;
            startMillis = millis;
            told = null;
            VideoSink sink = videoSink;
            if (sink != null) tell(sink);
        }

        @Override
        public void stamped(long ptsMillis, VideoAccessUnit unit) {
            VideoSink sink = videoSink;
            if (sink == null) return;
            if (sink != told) tell(sink);
            int run = generation;
            call(() -> sink.accept(run, ptsMillis, unit));
        }

        void end() {
            if (ended) return;
            ended = true;
            VideoSink sink = videoSink;
            int run = generation;
            if (sink != null && sink == told) call(() -> sink.end(run));
        }

        void abandon() {
            ended = true;
            VideoSink sink = videoSink;
            if (sink != null) call(sink::abandon);
        }

        private void tell(VideoSink sink) {
            told = sink;
            int run = generation;
            long start = startMillis;
            call(() -> sink.begin(run, start));
        }

        private void call(Runnable delivery) {
            try {
                delivery.run();
            } catch (RuntimeException e) {
                if (!complained) {
                    complained = true;
                    LOGGER.error("Soundstage: the video sink failed while Twitch channel {} played; its sound "
                            + "plays on.", getChannelName(), e);
                }
            }
        }
    }
}
