package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.LoudnessLeveller;
import com.nachtraben.soundstage.core.OpusCodec;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import com.nachtraben.soundstage.core.StreamSyncState;
import com.nachtraben.soundstage.core.VolumeBoost;
import com.nachtraben.soundstage.core.video.SurroundBuffer;
import com.nachtraben.soundstage.core.video.SurroundFrame;
import com.nachtraben.soundstage.network.AudioPacket;
import com.nachtraben.soundstage.network.NetworkHandler;
import com.nachtraben.soundstage.util.ServerPause;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class AudioEncoder {
    public static final int SAMPLE_RATE = OpusCodec.SAMPLE_RATE;
    public static final int CHANNELS = OpusCodec.CHANNELS;
    public static final int FRAME_SIZE = OpusCodec.FRAME_SIZE;
    public static final int MAX_PACKET_SIZE = OpusCodec.MAX_PACKET_SIZE;

    private static final long PREFILL_NANOS =
            StreamSyncState.PREROLL_FRAMES * 20_000_000L;

    private final OpusCodec.Encoder encoder = OpusCodec.available() ? new OpusCodec.Encoder() : null;
    private static final AtomicBoolean UNAVAILABLE_REPORTED =
            new AtomicBoolean();
    private volatile boolean isRunning = false;
    private Thread encoderThread = null;
    private long sequence = 0;
    private long sendFailures = 0;

    private SurroundBuffer surround;
    private final VolumeBoost.Limiter limiter =
            new VolumeBoost.Limiter();
    private LoudnessLeveller leveller;
    private final SurroundFrame surroundFrame =
            new SurroundFrame();
    private OpusCodec.Encoder frontEncoder;
    private OpusCodec.Encoder centreEncoder;
    private OpusCodec.Encoder surroundEncoder;

    public void setSurround(SurroundBuffer surround) {
        this.surround = surround;
    }

    public void startEncodingLoop(AudioPlayer player, ServerLevel level, BlockPos pos,
                                   List<BlockPos> targets,
                                   @Nullable IAudioReceiver receiver) {
        if (encoder == null) {
            if (UNAVAILABLE_REPORTED.compareAndSet(false, true)) {
                LoggerFactory.getLogger("Soundstage").error(
                        "Soundstage cannot encode audio on this server: the Opus native library did not load "
                                + "for this platform, so receivers will play nothing.", OpusCodec.unavailableCause());
            }
            return;
        }
        isRunning = true;
        encoderThread = new Thread(() -> {
            long nullFrames = 0;
            long nextFrameNanos = System.nanoTime() - PREFILL_NANOS;
            long lastSentNanos = 0L;
            while (isRunning) {
                try {
                    applyServerPauseState(player, level, pos);
                    retake(player, level, pos);
                    advanceStill(player, level, pos);
                    if (player.isPaused()) {
                        long sleepNanos = 20_000_000L;
                        LockSupport.parkNanos(sleepNanos);
                        nextFrameNanos = System.nanoTime() - PREFILL_NANOS;
                        continue;
                    }
                    AudioFrame frame = player.provide(20, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        byte[] pcm = frame.getData();
                        int volume = receiver != null ? receiver.getVolume()
                                : IAudioReceiver.DEFAULT_VOLUME;
                        boolean surroundRead = readSurround(frame);
                        if (surroundRead) surroundFrame.applyVolume(volume);
                        double[] gains = gains(pcm, surroundRead, volume,
                                receiver != null && receiver.isLevelling());
                        if (gains != null) VolumeBoost.apply(pcm, gains);
                        byte[] opusBytes = encoder.encode(pcm);
                        AudioPacket.SurroundBlock surroundBlock = surroundRead ? surroundBlock(gains) : null;
                        if (opusBytes != null) {
                            long sentNanos = System.nanoTime();
                            boolean resumed = lastSentNanos != 0L
                                    && sentNanos - lastSentNanos > AudioPacket.RESUME_GAP_NANOS;
                            lastSentNanos = sentNanos;
                            try {
                                if (targets != null) {
                                    List<SpeakerAcoustics> acoustics =
                                            new ArrayList<>(targets.size());
                                    List<Integer> volumes =
                                            new ArrayList<>(targets.size());
                                    for (BlockPos t : targets) {
                                        acoustics.add(SpeakerAudience.acousticsFor(level, t));
                                        volumes.add(SpeakerAudience.volumeFor(level, t));
                                    }
                                    NetworkHandler.sendToTracking(level, pos, targets,
                                            new AudioPacket(pos, targets, acoustics, volumes,
                                                    sequence, opusBytes, resumed, frame.getTimecode(), surroundBlock));
                                } else {
                                    NetworkHandler.sendToTracking(level, pos, null, new AudioPacket(pos, null, null,
                                            null, sequence, opusBytes, resumed, frame.getTimecode(), surroundBlock));
                                }
                            } catch (Exception ex) {
                                if (sendFailures++ == 0) {
                                    LoggerFactory.getLogger("Soundstage").error(
                                            "Audio delivery for receiver {} failed; later failures are counted, "
                                                    + "not logged",
                                            pos, ex);
                                }
                            }

                            sequence++;
                        }

                        nextFrameNanos += 20_000_000L;
                        long sleepNanos = nextFrameNanos - System.nanoTime();
                        if (sleepNanos > 0) {
                            LockSupport.parkNanos(sleepNanos);
                        }
                    } else {
                        nextFrameNanos += 20_000_000L;
                        long sleepNanos = nextFrameNanos - System.nanoTime();
                        if (sleepNanos > 0) {
                            LockSupport.parkNanos(sleepNanos);
                        } else {
                            nextFrameNanos = System.nanoTime();
                        }
                        nullFrames++;
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LoggerFactory.getLogger("Soundstage").error(
                            "Encoder loop for receiver {} died; audio stops until the next interaction", pos, e);
                    break;
                }
            }
            encoder.close();
            if (frontEncoder != null) {
                frontEncoder.close();
                centreEncoder.close();
                surroundEncoder.close();
            }
        });
        encoderThread.setName("Soundstage-Encoder-" + pos.toShortString());
        encoderThread.setDaemon(true);
        encoderThread.start();
    }

    private boolean autoPausedForServer = false;

    private long nextRetakeLookNanos;

    private void retake(AudioPlayer player, ServerLevel level, BlockPos pos) {
        long now = System.nanoTime();
        if (now < nextRetakeLookNanos) return;
        nextRetakeLookNanos = now + 1_000_000_000L;
        AudioTrack track = player.getPlayingTrack();
        if (!(track instanceof VideoCapableTrack video)) return;
        boolean surround = video.surroundPending();
        VideoCapableTrack.PictureRetake picture = video.pictureRetake();
        if (!surround && picture == VideoCapableTrack.PictureRetake.NONE) return;
        nextRetakeLookNanos = now + 5_000_000_000L;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        VideoCapableTrack.PictureRetake pictureRetake = picture;
        server.execute(() -> {
            TrackScheduler scheduler = AudioManagerState.scheduler(pos, level);
            if (scheduler != null && player.getPlayingTrack() == track) scheduler.retake(surround, pictureRetake);
        });
    }

    private long nextStillLookNanos;

    private void advanceStill(AudioPlayer player, ServerLevel level, BlockPos pos) {
        AudioTrack track = player.getPlayingTrack();
        if (!(track instanceof VideoCapableTrack video) || !video.isStill() || player.isPaused()) return;
        if (track.getPosition() < SoundstageConfig.imageDisplaySeconds() * 1000L) return;
        long now = System.nanoTime();
        if (now < nextStillLookNanos) return;
        nextStillLookNanos = now + 250_000_000L;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            TrackScheduler scheduler = AudioManagerState.scheduler(pos, level);
            if (scheduler != null) scheduler.advanceStill(track);
        });
    }

    private void applyServerPauseState(AudioPlayer player, ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) return;

        boolean serverPaused = ServerPause.isPaused(server);

        if (serverPaused && !autoPausedForServer) {
            if (!player.isPaused()) {
                player.setPaused(true);
                autoPausedForServer = true;
            }
        } else if (!serverPaused && autoPausedForServer) {
            autoPausedForServer = false;
            server.execute(() -> AudioManager.get().resumeAfterServerPause(pos, level, player));
        }
    }

    private double[] gains(byte[] pcm, boolean surroundRead, int volume, boolean levelling) {
        double boost = VolumeBoost.extraGainFor(volume);
        if (!levelling) leveller = null;
        if (!levelling && boost <= 1.0) {
            limiter.reset();
            return null;
        }
        double[] levels = null;
        if (levelling) {
            if (leveller == null) leveller = new LoudnessLeveller();
            levels = leveller.gains(pcm, VolumeBoost.lavaPlayerGain(volume));
        }
        return limiter.gains(pcm, surroundRead ? surroundFrame.samples() : null, boost, levels);
    }

    private boolean readSurround(AudioFrame frame) {
        if (surround == null || !surround.wanted()) return false;
        return surround.read(frame.getTimecode() * (OpusCodec.SAMPLE_RATE / 1000), SurroundFrame.SAMPLES,
                surroundFrame.samples());
    }

    private AudioPacket.SurroundBlock surroundBlock(double[] gains) {
        surroundFrame.split(gains);
        if (frontEncoder == null) {
            frontEncoder = new OpusCodec.Encoder();
            centreEncoder = new OpusCodec.Encoder();
            surroundEncoder = new OpusCodec.Encoder();
        }
        byte[] front = frontEncoder.encode(surroundFrame.front());
        byte[] centre = centreEncoder.encode(surroundFrame.centreAndLfe());
        byte[] rear = surroundEncoder.encode(surroundFrame.surround());
        return front == null || centre == null || rear == null ? null
                : new AudioPacket.SurroundBlock(front, centre, rear);
    }

    public void stop() {
        isRunning = false;
        if (encoderThread != null) {
            encoderThread.interrupt();
        }
    }

    public boolean isAlive() {
        return encoderThread != null && encoderThread.isAlive();
    }
}
