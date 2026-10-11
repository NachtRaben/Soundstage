package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.CopyPace;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.PictureStepMemory;
import com.nachtraben.soundstage.core.video.SurroundBuffer;
import com.nachtraben.soundstage.core.video.SurroundDecoder;
import com.nachtraben.soundstage.core.video.TsDemuxer;
import com.nachtraben.soundstage.core.video.TsVideoTee;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import com.nachtraben.soundstage.core.video.VideoStamper;
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsStreamProvider;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.function.LongConsumer;

final class FfmpegRuns {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    interface Source {
        Ffmpeg.Transcode start(long startMillis, Sizing sizing, boolean surround, boolean picture) throws Exception;

        default boolean hasSurroundTrack() {
            return false;
        }

        default boolean pictureOptional() {
            return false;
        }

        boolean hasVideo();

        Next ended(RunEnd end) throws Exception;
    }

    enum Next {
        END,
        AGAIN,
        CONTINUE
    }

    record RunEnd(int exit, long startMillis, boolean producedSound, long soundEndMillis,
                  boolean certificateRefused) {}

    static final class Sizing {
        private final PictureSize.Target target;
        private final int capHeight;
        private PictureSize.Copy chosen;

        Sizing(PictureSize.Target target, int capHeight) {
            this.target = target;
            this.capHeight = capHeight;
        }

        int formatHeight() {
            return target.formatHeight(capHeight);
        }

        PictureSize.Copy copy(int width, int height) {
            chosen = target.copy(width, height, capHeight);
            return chosen;
        }

        PictureSize.Copy chosen() {
            return chosen;
        }
    }

    enum Step {
        NONE,
        SMALLER,
        NO_PICTURE
    }

    private final String describes;
    private final boolean still;

    private volatile VideoSink videoSink;
    private volatile PictureSize.Target target = PictureSize.Target.UNKNOWN;
    private volatile int stepHeight;
    private volatile PictureSize.Copy watchedPicture;
    private volatile long madeMillis = -1;
    private final CopyPace pace = new CopyPace();
    private volatile LocalAudioTrackExecutor executor;
    private volatile boolean waitingForPlace;
    private volatile boolean pictureGivenUp;
    private volatile boolean givingUp;
    private volatile Ffmpeg.Transcode current;
    private volatile boolean stopped;

    private boolean pictureGivenUpTold;
    private long runStart;
    private int generation;
    private long soundEndMillis = -1;
    private int copiesWithSound;

    FfmpegRuns(String describes) {
        this(describes, false);
    }

    FfmpegRuns(String describes, boolean still) {
        this.describes = describes;
        this.still = still;
    }

    void setVideoSink(VideoSink sink) {
        videoSink = sink;
        if (sink != null && !still) startAtRememberedStep(sink);
    }

    private void startAtRememberedStep(VideoSink sink) {
        int step = sink.rememberedStep();
        if (step == PictureStepMemory.NO_PICTURE) {
            if (!pictureGivenUp) {
                pictureGivenUp = true;
                LOGGER.info("Soundstage: {} plays with sound only: in the last {} minutes this server could not "
                        + "encode this receiver's video as fast as it played, even at the smallest picture size.",
                        describes, PictureStepMemory.KEEP_NANOS / 60_000_000_000L);
            }
        } else if (step > 0 && (stepHeight == 0 || step < stepHeight)) {
            stepHeight = step;
            LOGGER.info("Soundstage: {} is encoded at no more than {} lines: in the last {} minutes this server could "
                    + "not encode this receiver's video as fast as it played at a larger size.", describes, step,
                    PictureStepMemory.KEEP_NANOS / 60_000_000_000L);
        }
    }

    VideoSink videoSink() {
        return videoSink;
    }

    private volatile Source runningSource;
    private volatile boolean runCarriesSurround;
    private volatile boolean runCarriesPicture;

    boolean surroundPending() {
        Source source = runningSource;
        return source != null && !stopped && !runCarriesSurround && source.hasSurroundTrack() && surroundWanted();
    }

    boolean picturePending() {
        Source source = runningSource;
        return source != null && !stopped && !runCarriesPicture && source.hasVideo() && source.pictureOptional()
                && !waitingForPlace && !pictureGivenUp && pictureWanted();
    }

    boolean pictureWanted() {
        VideoSink sink = videoSink;
        if (sink == null || !SoundstageConfig.enableVideo() || !sink.wantsPicture()) return false;
        return sink.hasScreens();
    }

    boolean pictureMayCome() {
        VideoSink sink = videoSink;
        return sink != null && SoundstageConfig.enableVideo() && sink.wantsPicture();
    }

    boolean surroundWanted() {
        VideoSink sink = videoSink;
        SurroundBuffer surround = sink == null ? null : sink.surround();
        return surround != null && surround.wanted();
    }

    void setPictureTarget(PictureSize.Target target) {
        this.target = target == null ? PictureSize.Target.UNKNOWN : target;
    }

    private int capHeight() {
        int cap = SoundstageConfig.maxVideoHeight();
        int step = stepHeight;
        return step > 0 ? Math.min(cap, step) : cap;
    }

    int formatHeight() {
        return new Sizing(target, capHeight()).formatHeight();
    }

    boolean takePlace() {
        VideoSink sink = videoSink;
        return sink == null || sink.takePicturePlace();
    }

    void releasePlace() {
        VideoSink sink = videoSink;
        if (sink != null) sink.releasePicturePlace();
    }

    boolean placeFreed() {
        if (!waitingForPlace || stopped || pictureGivenUp || !pictureWanted()) return false;
        return takePlace();
    }

    Step judgePace() {
        PictureSize.Copy picture = watchedPicture;
        LocalAudioTrackExecutor playing = executor;
        long made = madeMillis;
        if (still || picture == null || playing == null || made < 0 || stopped) return Step.NONE;
        if (!pace.behind(System.nanoTime(), made, made - playing.getPosition())) return Step.NONE;
        watchedPicture = null;
        String speed = String.format(Locale.ROOT, "%.2f", pace.speed());
        int next = PictureSize.stepBelow(picture);
        VideoSink sink = videoSink;
        if (next > 0) {
            stepHeight = next;
            if (sink != null) sink.stepped(next);
            LOGGER.info("Soundstage: {} is encoded at {}x{} more slowly than it plays ({} seconds of video a "
                    + "second); it is encoded again at {} lines.", describes, picture.width(), picture.height(), speed,
                    next);
            return Step.SMALLER;
        }
        if (sink != null) sink.stepped(PictureStepMemory.NO_PICTURE);
        LOGGER.warn("Soundstage: {} is encoded at {}x{} more slowly than it plays ({} seconds of video a second), "
                + "even at the smallest picture size; it plays on with sound only.", describes, picture.width(),
                picture.height(), speed);
        return Step.NO_PICTURE;
    }

    boolean heldBack() {
        long made = madeMillis;
        LocalAudioTrackExecutor playing = executor;
        return made >= 0 && playing != null && made - playing.getPosition() >= CopyPace.FULL_AHEAD_MILLIS;
    }

    long copyProcess() {
        Ffmpeg.Transcode running = current;
        if (running == null) return -1;
        try {
            return running.pid();
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }

    void givePictureUp() {
        pictureGivenUp = true;
    }

    boolean pictureGivenUp() {
        return pictureGivenUp;
    }

    void giveCopyUp() {
        givingUp = true;
        Ffmpeg.Transcode running = current;
        if (running != null) running.abandon();
    }

    void abandonPicture() {
        VideoSink sink = videoSink;
        if (sink == null) return;
        try {
            sink.abandon();
        } catch (RuntimeException e) {
            LOGGER.error("Soundstage: the video sink failed while {} gave its picture up; its sound plays on.",
                    describes, e);
        }
    }

    boolean isStopped() {
        return stopped;
    }

    int copiesWithSound() {
        return copiesWithSound;
    }

    void play(LocalAudioTrackExecutor executor, Source source) {
        this.executor = executor;
        executor.executeProcessingLoop(() -> playFrom(runStart, executor, source), position -> {
            runStart = position;
            soundEndMillis = -1;
        });
    }

    long resumePosition(LocalAudioTrackExecutor executor) {
        long heard = executor.getPosition();
        return soundEndMillis >= 0 && heard >= runStart && heard <= soundEndMillis ? heard : runStart;
    }

    private void playFrom(long startMillis, LocalAudioTrackExecutor executor, Source source) throws Exception {
        long from = startMillis;
        while (true) {
            RunEnd end = runOnce(from, executor, source);
            switch (source.ended(end)) {
                case END -> {
                    return;
                }
                case AGAIN -> {
                }
                case CONTINUE -> from = end.soundEndMillis();
            }
            if (Thread.currentThread().isInterrupted()) throw interruption();
        }
    }

    private RunEnd runOnce(long startMillis, LocalAudioTrackExecutor executor, Source source)
            throws Exception {
        if (stopped) throw interruption();
        int run = ++generation;
        VideoSink sink = videoSink;
        SurroundBuffer surround = sink == null ? null : sink.surround();
        boolean wantSurround = surround != null && surround.wanted();
        boolean picture = pictureFor(source);
        Sizing sizing = new Sizing(target, capHeight());
        madeMillis = -1;
        pace.restart();
        Ffmpeg.Transcode copy = source.start(startMillis, sizing, wantSurround, picture);
        current = copy;
        VideoStamper stamper = null;
        SurroundDecoder sidecar = null;
        try {
            if (stopped) copy.abandon();

            VideoRun video = picture ? new VideoRun(run, startMillis) : null;
            stamper = new VideoStamper(startMillis, video == null ? (ptsMillis, unit) -> { } : video::accept);
            TsDemuxer.Listener timed = new Paced(stamper, startMillis);
            if (wantSurround && source.hasSurroundTrack()) {
                sidecar = new SurroundDecoder(timed, surround, startMillis);
            } else if (surround != null) {
                surround.clear();
            }
            runCarriesSurround = sidecar != null;
            runCarriesPicture = picture;
            runningSource = source;
            watchedPicture = picture ? sizing.chosen() : null;
            if (picture) PictureCopies.started(this);
            TsDemuxer.Listener listener = sidecar != null ? sidecar : timed;
            TsDemuxer demuxer = listener == null ? null : new TsDemuxer(listener);
            InputStream ts = demuxer == null ? copy.output() : new TsVideoTee(copy.output(), demuxer);
            AdtsStreamProvider provider = new AdtsStreamProvider(
                    new PesPacketInputStream(new MpegTsElementaryInputStream(ts,
                            MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM)),
                    executor.getProcessingContext());
            provider.setInitialSeek(startMillis, startMillis);
            if (video != null) video.begin();
            try (AutoCloseable closing = provider::close) {
                provider.provideFrames();
            } catch (RuntimeException e) {
                watchedPicture = null;
                if (!endedByItself(copy)) throw interruption(e);
                if (video != null) video.end();
                throw e;
            }
            watchedPicture = null;
            if (!endedByItself(copy)) throw interruption(null);

            if (demuxer != null) demuxer.flush();
            if (video != null) video.end();
            int exit = copy.awaitExit();
            boolean sound = stamper != null && stamper.heardAudio();
            return new RunEnd(exit, startMillis, sound,
                    sound ? startMillis + stamper.audioMillis() : startMillis, copy.certificateRefused());
        } finally {
            PictureCopies.ended(this);
            watchedPicture = null;
            madeMillis = -1;
            if (stamper != null && stamper.heardAudio()) {
                soundEndMillis = Math.max(soundEndMillis, startMillis + stamper.audioMillis());
                copiesWithSound++;
            }
            current = null;
            if (sidecar != null) sidecar.close();
            copy.close();
        }
    }

    private boolean pictureFor(Source source) throws FriendlyException {
        if (!source.pictureOptional()) {
            takePlace();
            return source.hasVideo();
        }
        if (!source.hasVideo() || !pictureWanted() || pictureGivenUp) {
            releasePlace();
            if (pictureGivenUp && !pictureGivenUpTold) {
                pictureGivenUpTold = true;
                abandonPicture();
            }
            return false;
        }
        if (takePlace()) {
            waitingForPlace = false;
            return true;
        }
        if (!waitingForPlace) {
            waitingForPlace = true;
            LOGGER.info("Soundstage: {} plays with sound only for now: this server has no room for another picture "
                    + "yet; its picture comes when there is.", describes);
            abandonPicture();
        }
        return false;
    }

    private static boolean endedByItself(Ffmpeg.Transcode copy) {
        return !copy.isAbandoned() && !Thread.currentThread().isInterrupted();
    }

    private Exception interruption() {
        return interruption(null);
    }

    private Exception interruption(Throwable cause) {
        if (givingUp && !stopped) return new IOException("its picture could not keep up with real time");
        InterruptedException interruption = new InterruptedException("a seek or a stop ended this copy");
        if (cause != null) interruption.initCause(cause);
        return interruption;
    }

    void seek(long position, LongConsumer seekTrack) {
        Ffmpeg.Transcode interrupted = current;
        seekTrack.accept(position);
        if (interrupted != null) interrupted.abandon();
    }

    void stop(Runnable stopTrack) {
        stopTrack.run();
        stopped = true;
        Ffmpeg.Transcode running = current;
        if (running != null) running.abandon();
    }

    private final class Paced implements TsDemuxer.Listener {
        private final VideoStamper stamper;
        private final long startMillis;

        Paced(VideoStamper stamper, long startMillis) {
            this.stamper = stamper;
            this.startMillis = startMillis;
        }

        @Override
        public void onVideo(VideoAccessUnit unit) {
            stamper.onVideo(unit);
        }

        @Override
        public void onAudio(long pts90k, int adtsFrameCount) {
            stamper.onAudio(pts90k, adtsFrameCount);
            madeMillis = startMillis + stamper.audioMillis();
        }

        @Override
        public void onAudio(long pts90k, int adtsFrameCount, int sampleRate, int configuration) {
            stamper.onAudio(pts90k, adtsFrameCount, sampleRate, configuration);
            madeMillis = startMillis + stamper.audioMillis();
        }

        @Override
        public void onSecondAudio(long pts90k, byte[] adts) {
            stamper.onSecondAudio(pts90k, adts);
        }
    }

    private final class VideoRun {
        private final int generation;
        private final long startMillis;
        private VideoSink told;
        private boolean complained;

        VideoRun(int generation, long startMillis) {
            this.generation = generation;
            this.startMillis = startMillis;
        }

        void begin() {
            VideoSink sink = videoSink;
            if (sink != null) tell(sink);
        }

        void accept(long ptsMillis, VideoAccessUnit unit) {
            VideoSink sink = videoSink;
            if (sink == null) return;
            if (sink != told) tell(sink);
            call(() -> sink.accept(generation, ptsMillis, unit));
        }

        void end() {
            VideoSink sink = videoSink;
            if (sink != null && sink == told) call(() -> sink.end(generation));
        }

        private void tell(VideoSink sink) {
            told = sink;
            call(() -> sink.begin(generation, startMillis));
        }

        private void call(Runnable delivery) {
            try {
                delivery.run();
            } catch (RuntimeException e) {
                if (!complained) {
                    complained = true;
                    LOGGER.error("Soundstage: the video sink failed while {} played; its sound plays on.",
                            describes, e);
                }
            }
        }
    }
}
