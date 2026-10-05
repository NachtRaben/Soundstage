package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.core.OpusCodec;
import com.nachtraben.soundstage.network.AudioPacket;
import com.nachtraben.soundstage.network.NetworkHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class AudioEncoder {
    public static final int SAMPLE_RATE = OpusCodec.SAMPLE_RATE;
    public static final int CHANNELS = OpusCodec.CHANNELS;
    public static final int FRAME_SIZE = OpusCodec.FRAME_SIZE;
    public static final int MAX_PACKET_SIZE = OpusCodec.MAX_PACKET_SIZE;

    private static final long PREFILL_NANOS =
            com.nachtraben.soundstage.core.StreamSyncState.PREROLL_FRAMES * 20_000_000L;

    private final OpusCodec.Encoder encoder = OpusCodec.available() ? new OpusCodec.Encoder() : null;
    private static final java.util.concurrent.atomic.AtomicBoolean UNAVAILABLE_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean isRunning = false;
    private Thread encoderThread = null;
    private long sequence = 0;
    private long sendFailures = 0;

    private com.nachtraben.soundstage.core.video.SurroundBuffer surround;
    private final com.nachtraben.soundstage.core.VolumeBoost.Limiter limiter =
            new com.nachtraben.soundstage.core.VolumeBoost.Limiter();
    private com.nachtraben.soundstage.core.LoudnessLeveller leveller;
    private final com.nachtraben.soundstage.core.video.SurroundFrame surroundFrame =
            new com.nachtraben.soundstage.core.video.SurroundFrame();
    private OpusCodec.Encoder frontEncoder;
    private OpusCodec.Encoder centreEncoder;
    private OpusCodec.Encoder surroundEncoder;

    public void setSurround(com.nachtraben.soundstage.core.video.SurroundBuffer surround) {
        this.surround = surround;
    }

    public void startEncodingLoop(AudioPlayer player, ServerLevel level, BlockPos pos,
                                   java.util.List<BlockPos> targets,
                                   @org.jetbrains.annotations.Nullable com.nachtraben.soundstage.block.IAudioReceiver receiver) {
        if (encoder == null) {
            if (UNAVAILABLE_REPORTED.compareAndSet(false, true)) {
                org.slf4j.LoggerFactory.getLogger("Soundstage").error(
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
                    retakeForSurround(player, level, pos);
                    if (player.isPaused()) {
                        long sleepNanos = 20_000_000L;
                        java.util.concurrent.locks.LockSupport.parkNanos(sleepNanos);
                        nextFrameNanos = System.nanoTime() - PREFILL_NANOS;
                        continue;
                    }
                    AudioFrame frame = player.provide(20, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        if (com.nachtraben.soundstage.RigStaging.fault("encoder-waits-for-server")) {
                            level.getServer().executeBlocking(() -> { });
                        }
                        byte[] pcm = frame.getData();
                        int volume = receiver != null ? receiver.getVolume()
                                : com.nachtraben.soundstage.block.IAudioReceiver.DEFAULT_VOLUME;
                        boolean surroundRead = readSurround(frame);
                        if (surroundRead) surroundFrame.applyVolume(volume);
                        double[] gains = gains(pcm, surroundRead, volume,
                                receiver != null && receiver.isLevelling()
                                        && !com.nachtraben.soundstage.RigStaging.fault("levelling-ignored"));
                        if (gains != null) com.nachtraben.soundstage.core.VolumeBoost.apply(pcm, gains);
                        byte[] opusBytes = encoder.encode(pcm);
                        AudioPacket.SurroundBlock surroundBlock = surroundRead ? surroundBlock(gains) : null;
                        if (opusBytes != null) {
                            long sentNanos = System.nanoTime();
                            boolean resumed = lastSentNanos != 0L
                                    && sentNanos - lastSentNanos > AudioPacket.RESUME_GAP_NANOS;
                            lastSentNanos = sentNanos;
                            try {
                                if (targets != null) {
                                    java.util.List<com.nachtraben.soundstage.core.SpeakerAcoustics> acoustics =
                                            new java.util.ArrayList<>(targets.size());
                                    java.util.List<Integer> volumes =
                                            new java.util.ArrayList<>(targets.size());
                                    for (BlockPos t : targets) {
                                        acoustics.add(com.nachtraben.soundstage.audio.SpeakerAudience.acousticsFor(level, t));
                                        volumes.add(com.nachtraben.soundstage.audio.SpeakerAudience.volumeFor(level, t));
                                    }
                                    NetworkHandler.sendToTracking(level, pos, targets,
                                            new AudioPacket(pos, targets, acoustics, volumes,
                                                    sequence, opusBytes, resumed, frame.getTimecode(), surroundBlock));
                                } else {
                                    NetworkHandler.sendToTracking(level, pos, null, new AudioPacket(pos, null, null, null, sequence, opusBytes, resumed, frame.getTimecode(), surroundBlock));
                                }
                            } catch (Exception ex) {
                                if (sendFailures++ == 0) {
                                    org.slf4j.LoggerFactory.getLogger("Soundstage").error(
                                            "Audio delivery for receiver {} failed; later failures are counted, not logged",
                                            pos, ex);
                                }
                            }

                            sequence++;
                        }
                        
                        nextFrameNanos += 20_000_000L;
                        long sleepNanos = nextFrameNanos - System.nanoTime();
                        if (sleepNanos > 0) {
                            java.util.concurrent.locks.LockSupport.parkNanos(sleepNanos);
                        }
                    } else {
                        nextFrameNanos += 20_000_000L;
                        long sleepNanos = nextFrameNanos - System.nanoTime();
                        if (sleepNanos > 0) {
                            java.util.concurrent.locks.LockSupport.parkNanos(sleepNanos);
                        } else {
                            nextFrameNanos = System.nanoTime();
                        }
                        nullFrames++;
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger("Soundstage").error(
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

    private long nextSurroundLookNanos;

    private void retakeForSurround(AudioPlayer player, ServerLevel level, BlockPos pos) {
        long now = System.nanoTime();
        if (now < nextSurroundLookNanos) return;
        nextSurroundLookNanos = now + 1_000_000_000L;
        com.sedmelluq.discord.lavaplayer.track.AudioTrack track = player.getPlayingTrack();
        if (!(track instanceof com.nachtraben.soundstage.audio.video.VideoCapableTrack video) || !video.surroundPending()) return;
        if (com.nachtraben.soundstage.RigStaging.fault("surround-not-retaken")) return;
        nextSurroundLookNanos = now + 5_000_000_000L;
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            TrackScheduler scheduler = AudioManagerState.scheduler(pos, level);
            if (scheduler != null && player.getPlayingTrack() == track) scheduler.retakeForSurround();
        });
    }

    private void applyServerPauseState(AudioPlayer player, ServerLevel level, BlockPos pos) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;

        boolean serverPaused = com.nachtraben.soundstage.util.ServerPause.isPaused(server);

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
        double boost = com.nachtraben.soundstage.core.VolumeBoost.extraGainFor(volume);
        if (!levelling) leveller = null;
        if (!levelling && boost <= 1.0) {
            limiter.reset();
            return null;
        }
        double[] levels = null;
        if (levelling) {
            if (leveller == null) leveller = new com.nachtraben.soundstage.core.LoudnessLeveller();
            levels = leveller.gains(pcm, com.nachtraben.soundstage.core.VolumeBoost.lavaPlayerGain(volume));
        }
        return limiter.gains(pcm, surroundRead ? surroundFrame.samples() : null, boost, levels);
    }

    private boolean readSurround(com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame frame) {
        if (surround == null || !surround.wanted()) return false;
        if (!surround.read(frame.getTimecode() * (OpusCodec.SAMPLE_RATE / 1000),
                com.nachtraben.soundstage.core.video.SurroundFrame.SAMPLES, surroundFrame.samples())) {
            return false;
        }
        if (com.nachtraben.soundstage.RigStaging.fault("surround-swap")) {
            short[] six = surroundFrame.samples();
            for (int i = 0; i < six.length; i += 6) {
                short centre = six[i + 2];
                six[i + 2] = six[i + 4];
                six[i + 4] = centre;
            }
        }
        return true;
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
        return front == null || centre == null || rear == null ? null : new AudioPacket.SurroundBlock(front, centre, rear);
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
