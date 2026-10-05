package com.nachtraben.soundstage.audio.media;

import com.nachtraben.soundstage.audio.video.VideoSink;
import com.nachtraben.soundstage.core.video.SurroundBuffer;
import com.nachtraben.soundstage.core.video.SurroundDecoder;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.TsDemuxer;
import com.nachtraben.soundstage.core.video.TsVideoTee;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import com.nachtraben.soundstage.core.video.VideoStamper;
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsStreamProvider;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.InputStream;
import java.util.function.LongConsumer;

final class FfmpegRuns {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    interface Source {
        Ffmpeg.Transcode start(long startMillis, int maxHeight) throws Exception;

        default Ffmpeg.Transcode start(long startMillis, int maxHeight, boolean surround) throws Exception {
            return start(startMillis, maxHeight);
        }

        default boolean hasSurroundTrack() {
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

    private final String describes;

    private volatile VideoSink videoSink;
    private volatile int targetHeight;
    private volatile Ffmpeg.Transcode current;
    private volatile boolean stopped;

    private long runStart;
    private int generation;
    private long soundEndMillis = -1;
    private int copiesWithSound;

    FfmpegRuns(String describes) {
        this.describes = describes;
    }

    void setVideoSink(VideoSink sink) {
        videoSink = sink;
    }

    VideoSink videoSink() {
        return videoSink;
    }

    private volatile Source runningSource;
    private volatile boolean runCarriesSurround;

    boolean surroundPending() {
        Source source = runningSource;
        return source != null && !stopped && !runCarriesSurround && source.hasSurroundTrack() && surroundWanted();
    }

    boolean surroundWanted() {
        VideoSink sink = videoSink;
        SurroundBuffer surround = sink == null ? null : sink.surround();
        return surround != null && surround.wanted();
    }

    void setTargetHeight(int height) {
        targetHeight = height;
    }

    int pictureHeight() {
        int cap = SoundstageConfig.maxVideoHeight();
        int asked = targetHeight;
        return asked > 0 ? Math.min(asked, cap) : cap;
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
            if (Thread.currentThread().isInterrupted()) throw cutShort(null);
        }
    }

    private RunEnd runOnce(long startMillis, LocalAudioTrackExecutor executor, Source source)
            throws Exception {
        if (stopped) throw cutShort(null);
        int run = ++generation;
        VideoSink sink = videoSink;
        SurroundBuffer surround = sink == null ? null : sink.surround();
        boolean wantSurround = surround != null && surround.wanted();
        Ffmpeg.Transcode copy = source.start(startMillis, pictureHeight(), wantSurround);
        current = copy;
        VideoStamper stamper = null;
        SurroundDecoder sidecar = null;
        try {
            if (stopped) copy.abandon();

            VideoRun video = source.hasVideo() ? new VideoRun(run, startMillis) : null;
            stamper = video == null ? null : new VideoStamper(startMillis, video::accept);
            if (wantSurround && source.hasSurroundTrack()) {
                sidecar = new SurroundDecoder(stamper, surround, startMillis);
            } else if (surround != null) {
                surround.clear();
            }
            runCarriesSurround = sidecar != null;
            runningSource = source;
            TsDemuxer.Listener listener = sidecar != null ? sidecar : stamper;
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
                if (!endedByItself(copy)) throw cutShort(e);
                if (video != null) video.end();
                throw e;
            }
            if (!endedByItself(copy)) throw cutShort(null);

            if (demuxer != null) demuxer.flush();
            if (video != null) video.end();
            int exit = copy.awaitExit();
            boolean sound = stamper != null && stamper.heardAudio();
            return new RunEnd(exit, startMillis, sound,
                    sound ? startMillis + stamper.audioMillis() : startMillis, copy.certificateRefused());
        } finally {
            if (stamper != null && stamper.heardAudio()) {
                soundEndMillis = Math.max(soundEndMillis, startMillis + stamper.audioMillis());
                copiesWithSound++;
            }
            current = null;
            if (sidecar != null) sidecar.close();
            copy.close();
        }
    }

    private static boolean endedByItself(Ffmpeg.Transcode copy) {
        return !copy.isAbandoned() && !Thread.currentThread().isInterrupted();
    }

    private static InterruptedException cutShort(Throwable cause) {
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
