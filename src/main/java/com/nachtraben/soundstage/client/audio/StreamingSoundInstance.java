package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.core.AudioDSP;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import com.nachtraben.soundstage.core.StreamRenderGate;
import dev.armadeus.armalib.audio.client.StreamSoundInstance;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ShortBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class StreamingSoundInstance extends StreamSoundInstance {

    @Getter
    private final BlockPos pos;
    @Getter
    private final BlockPos receiverPos;
    private final AudioDSP dsp;

    private static final Method BRIDGE_FACTORY = resolveBridgeFactory();

    private static Method resolveBridgeFactory() {
        try {
            Class<?> bridge = Class.forName(
                    "com.nachtraben.soundstage.client.audio.resonance.ResonanceStreamingSoundInstance");
            return bridge.getMethod("create", BlockPos.class, ClientAudioReceiver.class, SpeakerAcoustics.class);
        } catch (LinkageError | ReflectiveOperationException e) {
            LoggerFactory.getLogger("Soundstage").info(
                    "Resonance's directional-sound bridge is unavailable ({}); "
                            + "speakers will play without directional sound", e.toString());
            return null;
        }
    }

    public static StreamingSoundInstance create(BlockPos pos, ClientAudioReceiver receiver,
                                                SpeakerAcoustics initialAcoustics) {
        Method factory = BRIDGE_FACTORY;
        if (factory != null) {
            try {
                return (StreamingSoundInstance) factory.invoke(null, pos, receiver, initialAcoustics);
            } catch (LinkageError | ReflectiveOperationException e) {
                LoggerFactory.getLogger("Soundstage").warn(
                        "Resonance's directional-sound bridge failed to build a stream; "
                                + "falling back to a plain stream", e);
            }
        }
        return initialize(new StreamingSoundInstance(pos, receiver.receiverPos), receiver, initialAcoustics);
    }

    protected static StreamingSoundInstance initialize(
            StreamingSoundInstance stream, ClientAudioReceiver receiver, SpeakerAcoustics initialAcoustics) {
        stream.owner = receiver;
        stream.applyAcoustics(initialAcoustics);
        return stream;
    }

    protected StreamingSoundInstance(BlockPos pos, BlockPos receiverPos) {
        super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID,
                "live_stream_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ())), SoundSource.RECORDS,
                SoundInstance.createUnseededRandom());
        this.pos = pos;
        this.receiverPos = receiverPos;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = false;
        this.delay = 0;
        this.volume = 1.0f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;

        this.dsp = new AudioDSP(AudioDSP.FilterType.NONE, 48000f, 1000f, 0.707f);
    }

    private volatile ClientAudioReceiver owner;

    private void applyAcoustics(SpeakerAcoustics declared) {
        if (declared == null || (resolvedBlockType && declared == this.acoustics)) return;
        this.acoustics = declared;
        this.dsp.setType(declared.getFilterType(), declared.getFilterFrequency(), 0.707f);
        this.resolvedBlockType = true;
    }

    private int currentRange() {
        if (Minecraft.getInstance().level != null) {
            BlockEntity be =
                    Minecraft.getInstance().level.getBlockEntity(pos);
            if (be instanceof SpeakerBlockEntity speaker) {
                return speaker.getRange();
            }
        }
        return SpeakerBlockEntity.DEFAULT_RANGE;
    }

    private volatile int resolvedRange =
            SpeakerBlockEntity.DEFAULT_RANGE;

    @Override
    public @NotNull WeighedSoundEvents resolve(@NotNull SoundManager soundManager) {
        ResourceLocation soundLoc = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "live_stream");
        this.resolvedRange = currentRange();
        this.sound = new Sound(
            //? if >=1.21 {
            soundLoc,
            //?} else {
            /*soundLoc.toString(),
            *///?}
            ConstantFloat.of(1.0f),
            ConstantFloat.of(1.0f),
            1,
            Sound.Type.SOUND_EVENT,
            true,
            false,
            resolvedRange
        );

        WeighedSoundEvents events = new WeighedSoundEvents(soundLoc, null);
        events.addSound(this.sound);
        return events;
    }

    @Getter
    private volatile boolean isPendingStart = true;

    private volatile boolean channelOpen;

    public boolean isChannelOpen() { return channelOpen; }

    void markChannelClosed() { channelOpen = false; }

    @Getter
    private volatile JitterAudioStream jitterStream = null;

    @Getter @lombok.Setter
    private volatile boolean needsRestart = false;

    @Getter @lombok.Setter
    private volatile boolean alignToPlayingGroup = false;

    @Getter @lombok.Setter
    private volatile GroupStartClock groupStart = null;

    @Override
    protected @NotNull CompletableFuture<AudioStream> openStream(@NotNull SoundBufferLibrary soundBuffers,
            @NotNull Sound sound, boolean looping) {
        isPendingStart = false;
        channelOpen = true;
        jitterStream = new JitterAudioStream(this);
        return CompletableFuture.completedFuture(jitterStream);
    }

    @Getter @lombok.Setter
    private volatile long startAnchorSequence = -1;

    @Getter
    private volatile long lastPacketTime = System.currentTimeMillis();

    public void notifyPacketReceived() {
        lastPacketTime = System.currentTimeMillis();
    }

    private volatile boolean resolvedBlockType = false;

    @Getter
    private volatile SpeakerAcoustics acoustics = SpeakerAcoustics.MAIN;

    @Getter
    private volatile ChannelMode channelMode =
            ChannelMode.MONO;

    @Getter
    private volatile int delayFrames = 0;

    private volatile int volumePercent = 100;

    public float getSpeakerGain() {
        ClientAudioReceiver receiver = owner;
        Integer declared = receiver == null ? null : receiver.declaredVolumes.get(pos);
        int percent = declared != null ? declared : volumePercent;
        return Math.max(0, Math.min(100, percent)) / 100.0f;
    }

    private volatile SpeakerAnimation ownAnimation;

    public SpeakerAnimation.Key animationKey() {
        return new SpeakerAnimation.Key(channelMode, acoustics, getSpeakerGain());
    }

    private SpeakerAnimation animation() {
        ClientAudioReceiver receiver = owner;
        if (receiver != null) {
            return receiver.animationFor(animationKey());
        }
        SpeakerAnimation own = ownAnimation;
        if (own == null) {
            synchronized (this) {
                own = ownAnimation;
                if (own == null) {
                    own = new SpeakerAnimation(acoustics);
                    ownAnimation = own;
                }
            }
        }
        return own;
    }

    public void clearExcursion() {
        SpeakerAnimation own = ownAnimation;
        if (own != null) own.clear();
        ClientAudioReceiver receiver = owner;
        if (receiver != null) receiver.releaseAnimation(animationKey());
    }

    void traceFrame(long sequence, ShortBuffer mono, float sampleRate) {
        animation().trace(sequence, mono, sampleRate);
    }

    private double ownTimeline(double groupSequence) {
        JitterAudioStream stream = getJitterStream();
        if (stream != null) {
            ClientAudioReceiver receiver = AudioDecoder.getReceiver(receiverPos);
            double own = receiver == null ? ClientAudioReceiver.NO_OWN_SEQUENCE
                    : receiver.ownSequenceAt(this, groupSequence);
            if (own != ClientAudioReceiver.NO_OWN_SEQUENCE) return own;
        }
        int realised = stream != null ? stream.getRealisedDelayFrames() : delayFrames;
        return groupSequence - realised;
    }

    public boolean playsContentAt(double sequence) {
        return !Double.isNaN(ownTimeline(sequence));
    }

    public float sampleExcursion(double sequence) {
        double own = ownTimeline(sequence);
        return Double.isNaN(own) ? 0f : animation().sampleExcursion(own);
    }

    public float sampleLevel(double sequence) {
        double own = ownTimeline(sequence);
        return Double.isNaN(own) ? 0f : animation().sampleLevel(own);
    }

    public float peakLevel(double sequence, int framesBack) {
        double own = ownTimeline(sequence);
        return Double.isNaN(own) ? 0f : animation().peakLevel(own, framesBack);
    }

    public record FacingVector(double x, double y, double z) {
        static final FacingVector DEFAULT = new FacingVector(0.0, 0.0, -1.0);
    }

    private volatile FacingVector facing = FacingVector.DEFAULT;

    protected FacingVector facingSnapshot() {
        return facing;
    }

    public double getFacingX() {
        return facing.x();
    }

    public double getFacingY() {
        return facing.y();
    }

    public double getFacingZ() {
        return facing.z();
    }

    private void resolveAcoustics() {
        if (resolvedBlockType || Minecraft.getInstance().level == null) {
            return;
        }

        if (tryDeclaredAcoustics()) {
            return;
        }

        BlockState state =
                Minecraft.getInstance().level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        this.acoustics = SpeakerAcoustics.forBlockPath(path);
        this.dsp.setType(acoustics.getFilterType(), acoustics.getFilterFrequency(), 0.707f);
        this.resolvedBlockType = true;
    }

    private boolean tryDeclaredAcoustics() {
        ClientAudioReceiver receiver = owner;
        if (receiver == null) {
            return resolvedBlockType;
        }
        SpeakerAcoustics declared = receiver.declaredAcoustics.get(pos);
        if (declared == null) {
            return resolvedBlockType;
        }
        applyAcoustics(declared);
        return true;
    }

    private final StreamRenderGate renderGate = new StreamRenderGate();

    boolean traceVirtualFrame(Runnable renderer) {
        return renderGate.renderVirtual(renderer);
    }

    <T> T renderAudioFrame(Supplier<T> renderer) {
        return renderGate.renderAudio(renderer);
    }

    public boolean isPumped() {
        return renderGate.isAudioOwned();
    }

    private volatile SpeakerAcoustics blockTruth;
    private final Object filterAuditLock = new Object();
    private final int[] framesBeforeBlockTruth = new int[SpeakerAcoustics.values().length];

    private final AtomicInteger wrongFilterFrames =
            new AtomicInteger();

    public int getWrongFilterFrames() { return wrongFilterFrames.get(); }

    public AudioDSP getDsp() {
        boolean resolved = tryDeclaredAcoustics();
        boolean wrong = false;
        synchronized (filterAuditLock) {
            if (resolved && blockTruth == null) {
                framesBeforeBlockTruth[acoustics.ordinal()]++;
            } else if (blockTruth != null && acoustics != blockTruth) {
                wrong = true;
            }
        }
        if (wrong) {
            wrongFilterFrames.incrementAndGet();
        }
        if (!resolved) {
            unfilteredFrames.incrementAndGet();
        }
        ClientAudioReceiver receiver = owner;
        if (receiver != null) receiver.recordFilterFrame(!resolved, wrong);
        return dsp;
    }

    private final AtomicInteger unfilteredFrames =
            new AtomicInteger();

    public int getUnfilteredFrames() { return unfilteredFrames.get(); }

    @Override
    public void tick() {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        BlockState state =
                Minecraft.getInstance().level.getBlockState(pos);

        if (blockTruth == null && !state.isAir()) {
            SpeakerAcoustics observed = SpeakerAcoustics.forBlockPath(net.minecraft.core.registries
                    .BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            int earlierWrong = 0;
            synchronized (filterAuditLock) {
                if (blockTruth == null) {
                    blockTruth = observed;
                    for (SpeakerAcoustics candidate : SpeakerAcoustics.values()) {
                        int frames = framesBeforeBlockTruth[candidate.ordinal()];
                        framesBeforeBlockTruth[candidate.ordinal()] = 0;
                        if (candidate != observed) earlierWrong += frames;
                    }
                }
            }
            if (earlierWrong > 0) {
                wrongFilterFrames.addAndGet(earlierWrong);
                ClientAudioReceiver receiver = owner;
                if (receiver != null) receiver.recordWrongFilterFrames(earlierWrong);
            }
        }
        if (state.isAir()) {
            return;
        }

        resolveAcoustics();

        BlockEntity be =
                Minecraft.getInstance().level.getBlockEntity(pos);
        if (!(be instanceof SpeakerBlockEntity speaker)) {
            return;
        }

        this.channelMode = speaker.getChannelMode();
        this.delayFrames = speaker.getDelayFrames();
        this.volumePercent = speaker.getVolumePercent();

        if (speaker.getRange() != resolvedRange) {
            this.needsRestart = true;
        }

        int rotation16 = state.getValue(AbstractSpeakerBlock.ROTATION);
        float baseYaw = rotation16 * 360f / 16f;
        float yaw = baseYaw - speaker.getYawOffset();
        float pitch = speaker.getPitch();

        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);

        this.facing = new FacingVector(
                -Math.sin(yawRad) * cosPitch,
                Math.sin(pitchRad),
                -Math.cos(yawRad) * cosPitch);
    }
}
