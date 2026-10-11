package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.FfmpegCommand;
import com.nachtraben.soundstage.core.video.HouseFormat;
import com.nachtraben.soundstage.core.video.LivePace;
import com.nachtraben.soundstage.core.video.LiveVideoStamper;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.TsDemuxer;
import com.nachtraben.soundstage.core.video.TsVideoTee;
import com.nachtraben.soundstage.core.video.TwitchVariants;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.ChainedInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;

public final class TwitchVideoTrack extends TwitchStreamAudioTrack implements VideoCapableTrack {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    private static final int TS_SYNC_BYTE = 0x47;

    static final int MAX_JUDGED_BYTES = 16 << 20;

    private static final long FINISH_MILLIS = 2_000;

    private final TwitchVideoSourceManager sourceManager;

    private volatile VideoSink videoSink;
    private volatile PictureSize.Target target = PictureSize.Target.UNKNOWN;
    private volatile boolean stopped;
    private volatile boolean awaitingScreen;
    private volatile Picture picture;
    private volatile LocalAudioTrackExecutor executor;

    TwitchVideoTrack(AudioTrackInfo trackInfo, TwitchVideoSourceManager sourceManager) {
        super(trackInfo, sourceManager);
        this.sourceManager = sourceManager;
    }

    @Override
    public void setVideoSink(VideoSink sink) {
        videoSink = sink;
    }

    @Override
    public void setPictureTarget(PictureSize.Target target) {
        this.target = target == null ? PictureSize.Target.UNKNOWN : target;
    }

    int pictureHeight() {
        return target.formatHeight(SoundstageConfig.maxVideoHeight());
    }

    @Override
    public PictureRetake pictureRetake() {
        VideoSink sink = videoSink;
        if (awaitingScreen && sink != null && sink.hasScreens() && sink.wantsPicture()) return PictureRetake.REPLAY;
        Picture playing = picture;
        if (playing == null) return PictureRetake.NONE;
        LocalAudioTrackExecutor running = executor;
        playing.judgePace(running == null ? 0 : running.getPosition());
        return playing.placeFreed() ? PictureRetake.REPLAY : PictureRetake.NONE;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        this.executor = executor;
        VideoSink sink = videoSink;
        boolean showing = sink != null && sink.hasScreens() && sink.wantsPicture();
        awaitingScreen = sink != null && !showing && sink.wantsPicture();
        if (!showing && !awaitingScreen && sink != null) sink.abandon();
        int height = showing ? pictureHeight() : TwitchVariantSegmentUrlProvider.SOUND_ONLY;
        boolean ffmpeg = showing && Ffmpeg.available();
        boolean placeHeld = ffmpeg && sink.takePicturePlace();
        Throwable failure;
        Picture playing = null;
        try (TwitchVideoSourceManager.Segments segments =
                     sourceManager.streaming().open(sourceManager, getChannelName(), height, placeHeld)) {
            playing = showing ? new Picture(segments, height, ffmpeg, placeHeld) : null;
            picture = playing;
            if (playing != null) playing.begin();
            Picture tapped = playing;
            boolean[] ranOut = {false};
            try (ChainedInputStream joined = new ChainedInputStream(() -> {
                InputStream next = segments.next();
                if (next == null) ranOut[0] = true;
                return tap(next, tapped);
            })) {
                processJoinedStream(executor, joined);
            }
            if (playing != null && (ranOut[0] || !stopped)) playing.end();
            return;
        } catch (Exception | LinkageError e) {
            failure = e;
        } finally {
            picture = null;
            if (playing != null) playing.close();
        }
        if (placeHeld && playing == null) sink.releasePicturePlace();
        if (stopped) return;
        String why = reasons(failure);
        if (playing != null) {
            LOGGER.warn("Soundstage: Twitch channel {} plays on with sound only{}: {}", getChannelName(),
                    playing.stamper.heardAudio() ? "" : " from the start", why);
            SoundOnlyFallbacks.report(trackInfo.identifier, why);
            playing.abandon();
        } else {
            LOGGER.warn("Soundstage: Twitch channel {} plays on through LavaPlayer's own path: {}",
                    getChannelName(), why);
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
        return picture == null ? peek : picture.tap(peek);
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
        return FfmpegCommand.withoutUrls(text.toString());
    }

    private final class Picture implements LiveVideoStamper.Output {

        private enum Mode {
            JUDGING,
            PASSING,
            REENCODING,
            WAITING,
            NONE
        }

        private record Placed(int generation, long millis, VideoAccessUnit unit) {}

        final LiveVideoStamper stamper = new LiveVideoStamper(this);
        private final TwitchVideoSourceManager.Segments segments;
        private final int maxHeight;
        private final boolean ffmpeg;
        private final HouseFormat.Judge judge = new HouseFormat.Judge();
        private final LivePace pace = new LivePace();
        private final TsDemuxer.Listener router = new TsDemuxer.Listener() {
            @Override
            public void onVideo(VideoAccessUnit unit) {
                video(unit);
            }

            @Override
            public void onAudio(long pts90k, int adtsFrameCount) {
                audio(pts90k, adtsFrameCount, 0, -1);
            }

            @Override
            public void onAudio(long pts90k, int adtsFrameCount, int sampleRate, int configuration) {
                audio(pts90k, adtsFrameCount, sampleRate, configuration);
            }
        };

        private Mode mode = Mode.JUDGING;
        private boolean placeHeld;
        private final List<Placed> held = new ArrayList<>();
        private ByteArrayOutputStream judged = new ByteArrayOutputStream();
        private LiveReencode reencode;
        private boolean broke;
        private long newestMillis = -1;
        private boolean outOfFormatSaid;
        private VideoAccessUnit placingLate;
        private int generation;
        private long startMillis;
        private VideoSink told;
        private boolean complained;
        private boolean ended;

        Picture(TwitchVideoSourceManager.Segments segments, int maxHeight, boolean ffmpeg, boolean placeHeld) {
            this.segments = segments;
            this.maxHeight = maxHeight;
            this.ffmpeg = ffmpeg;
            this.placeHeld = placeHeld;
        }

        InputStream tap(InputStream ts) {
            return new FilterInputStream(new TsVideoTee(ts, new TsDemuxer(router))) {
                private final byte[] single = new byte[1];

                @Override
                public int read() throws IOException {
                    int b = super.read();
                    if (b >= 0) {
                        single[0] = (byte) b;
                        feed(single, 0, 1);
                    }
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int n = super.read(b, off, len);
                    if (n > 0) feed(b, off, n);
                    return n;
                }
            };
        }

        synchronized void begin() {
            generation++;
            startMillis = 0;
            told = null;
            VideoSink sink = videoSink;
            if (sink != null) tell(sink);
        }

        private void audio(long pts90k, int frames, int sampleRate, int configuration) {
            stamper.onAudio(pts90k, frames, sampleRate, configuration);
            synchronized (this) {
                if (mode != Mode.JUDGING) return;
                int rate = sampleRate > 0 ? sampleRate : LiveVideoStamper.DEFAULT_SAMPLE_RATE;
                judge.heard(frames * 1024L * 90_000 / rate);
                decideIfKnown();
            }
        }

        private void video(VideoAccessUnit unit) {
            boolean place;
            synchronized (this) {
                if (mode == Mode.JUDGING) {
                    judge.conforms(unit);
                    place = true;
                } else if (mode == Mode.PASSING) {
                    place = judge.conforms(unit);
                    if (!place && !outOfFormatSaid) {
                        outOfFormatSaid = true;
                        LOGGER.warn("Soundstage: Twitch channel {} began sending pictures that cannot be passed "
                                + "through as they are (H.264 Main profile, no B-frames, at most 30 a second, a "
                                + "keyframe at least every 2 seconds); those pictures are left out.",
                                getChannelName());
                    }
                } else {
                    return;
                }
            }
            if (place) {
                stamper.onVideo(unit);
            } else {
                stamper.gap();
            }
            synchronized (this) {
                if (mode == Mode.JUDGING) decideIfKnown();
            }
        }

        private synchronized void feed(byte[] bytes, int offset, int length) {
            if (mode == Mode.JUDGING) {
                judged.write(bytes, offset, length);
                if (judged.size() > MAX_JUDGED_BYTES) decide(HouseFormat.Judge.Verdict.NOT_HOUSE, "no verdict in time");
            } else if (mode == Mode.REENCODING) {
                if (broke) {
                    broke = false;
                    try {
                        reencode.restart();
                    } catch (IOException e) {
                        giveUp("ffmpeg could not be started again after a break in the stream");
                        return;
                    }
                }
                if (!reencode.feed(bytes, offset, length)) giveUp("ffmpeg could not keep up with the stream");
            }
        }

        private void decideIfKnown() {
            HouseFormat.Judge.Verdict verdict = judge.verdict();
            if (verdict != HouseFormat.Judge.Verdict.PENDING) decide(verdict, judge.reason());
        }

        private void decide(HouseFormat.Judge.Verdict verdict, String reason) {
            TwitchVariants.Variant chosen = segments.chosen();
            boolean fits = chosen == null || chosen.height() <= maxHeight;
            VideoSink sink = videoSink;
            if (verdict == HouseFormat.Judge.Verdict.HOUSE && fits) {
                mode = Mode.PASSING;
                judged = null;
                if (placeHeld) {
                    placeHeld = false;
                    if (sink != null) call(sink::releasePicturePlace);
                }
                LOGGER.info("Soundstage: Twitch channel {}'s picture is passed through as it is.", getChannelName());
                for (Placed placed : held) {
                    if (placed.generation() == generation) deliver(placed.millis(), placed.unit());
                }
                held.clear();
                return;
            }
            held.clear();
            String why = verdict == HouseFormat.Judge.Verdict.HOUSE ? "it is taller than the screens ask for" : reason;
            if (!ffmpeg) {
                giveUp("its picture needs re-encoding (" + why + "), and ffmpeg is not available");
                return;
            }
            if (!placeHeld && (sink == null || !sink.takePicturePlace())) {
                mode = Mode.WAITING;
                judged = null;
                LOGGER.info("Soundstage: Twitch channel {} plays with sound only for now: its picture needs "
                        + "re-encoding ({}), and this server has no room for another picture yet; it comes when "
                        + "there is.", getChannelName(), why);
                if (sink != null) call(sink::abandon);
                return;
            }
            PictureSize.Copy size = target.copy(chosen == null ? 0 : chosen.width(),
                    chosen == null ? 0 : chosen.height(), SoundstageConfig.maxVideoHeight());
            LiveReencode starting = new LiveReencode("Twitch channel " + getChannelName(), size, this::reencoded);
            try {
                starting.start();
            } catch (IOException e) {
                Ffmpeg.forget();
                giveUp("ffmpeg could not be started to re-encode its picture");
                return;
            }
            reencode = starting;
            mode = Mode.REENCODING;
            placeHeld = true;
            pace.start(System.nanoTime());
            LOGGER.info("Soundstage: Twitch channel {}'s picture is re-encoded at {}x{}: {}.", getChannelName(),
                    size.width(), size.height(), why);
            byte[] read = judged.toByteArray();
            judged = null;
            boolean brokeWhileJudged = broke;
            broke = false;
            if (!brokeWhileJudged && !reencode.feed(read, 0, read.length)) {
                giveUp("ffmpeg could not keep up with the stream");
            }
        }

        private void reencoded(VideoAccessUnit unit) {
            synchronized (stamper) {
                placingLate = unit;
                try {
                    stamper.placeLate(unit);
                } finally {
                    placingLate = null;
                }
            }
        }

        @Override
        public synchronized void restart(long millis) {
            brokeHere();
            generation++;
            startMillis = millis;
            told = null;
            held.clear();
            newestMillis = -1;
            VideoSink sink = videoSink;
            if (sink != null && showsPicture()) tell(sink);
        }

        @Override
        public synchronized void discontinuity() {
            brokeHere();
        }

        private void brokeHere() {
            broke = true;
            if (mode == Mode.REENCODING) pace.start(System.nanoTime());
        }

        @Override
        public synchronized void stamped(long ptsMillis, VideoAccessUnit unit) {
            if (mode == Mode.JUDGING) {
                held.add(new Placed(generation, ptsMillis, unit));
            } else if (mode == Mode.PASSING || mode == Mode.REENCODING && unit == placingLate) {
                deliver(ptsMillis, unit);
            }
        }

        private void deliver(long ptsMillis, VideoAccessUnit unit) {
            VideoSink sink = videoSink;
            if (sink == null) return;
            if (sink != told) tell(sink);
            int run = generation;
            call(() -> sink.accept(run, ptsMillis, unit));
            if (mode == Mode.REENCODING) newestMillis = Math.max(newestMillis, ptsMillis);
        }

        synchronized void judgePace(long playedMillis) {
            if (mode != Mode.REENCODING) return;
            String failure = reencode.failure();
            if (failure != null) {
                giveUp(failure);
            } else if (pace.behind(System.nanoTime(), newestMillis, playedMillis)) {
                giveUp("its re-encoded picture cannot keep up with the sound");
            }
        }

        synchronized boolean placeFreed() {
            boolean wants = mode == Mode.WAITING || mode == Mode.PASSING && ffmpeg && segments.betterWithReencode();
            if (!wants) return false;
            VideoSink sink = videoSink;
            return sink != null && sink.hasScreens() && sink.wantsPicture() && sink.takePicturePlace();
        }

        private void giveUp(String why) {
            mode = Mode.NONE;
            if (reencode != null) reencode.close();
            reencode = null;
            judged = null;
            LOGGER.warn("Soundstage: Twitch channel {} plays on with sound only: {}.", getChannelName(), why);
            SoundOnlyFallbacks.report(trackInfo.identifier, why);
            VideoSink sink = videoSink;
            if (sink != null) call(sink::abandon);
        }

        void end() {
            LiveReencode finishing;
            synchronized (this) {
                finishing = mode == Mode.REENCODING ? reencode : null;
            }
            if (finishing != null) {
                finishing.finish(FINISH_MILLIS, this::endRun);
            } else {
                endRun();
            }
        }

        private synchronized void endRun() {
            if (ended) return;
            ended = true;
            VideoSink sink = videoSink;
            int run = generation;
            if (sink != null && sink == told) call(() -> sink.end(run));
        }

        synchronized void abandon() {
            ended = true;
            mode = Mode.NONE;
            VideoSink sink = videoSink;
            if (sink != null) call(sink::abandon);
        }

        void close() {
            LiveReencode running;
            synchronized (this) {
                running = reencode;
                reencode = null;
                judged = null;
            }
            if (running != null) running.close();
        }

        private boolean showsPicture() {
            return mode == Mode.JUDGING || mode == Mode.PASSING || mode == Mode.REENCODING;
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
