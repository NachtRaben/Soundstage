package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.core.ChannelDownmix;
import com.nachtraben.soundstage.core.HandedFrameMap;
import com.nachtraben.soundstage.core.StreamSyncState;
import com.nachtraben.soundstage.core.SpeakerOutput;
import com.nachtraben.soundstage.core.LevelMeterModel;
import com.nachtraben.soundstage.core.ConeExcursionModel;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.OptionalDouble;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTDeviceClock;
//? if >=1.20.5 {
import org.lwjgl.openal.SOFTSourceStartDelay;
//?}

public class JitterAudioStream implements AudioStream {

    private static final long MIN_JOIN_START_LEAD_NANOS = 100_000_000L;
    private static volatile long joinStartLeadNanos = MIN_JOIN_START_LEAD_NANOS;
    private static boolean legacyGroupMapping() {
        return com.nachtraben.soundstage.RigStaging.fault("legacy-group-mapping");
    }
    private static volatile boolean deviceClockProbed;
    private static volatile boolean deviceClockSupported;
    private static volatile boolean clockReadSupported;
    private static volatile boolean sourceLatencySupported;

    public static boolean probeDeviceClockSupport() {
        if (deviceClockProbed) return deviceClockSupported;
        try {
            long context = ALC10.alcGetCurrentContext();
            long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
            sourceLatencySupported = device != 0L && AL.getCapabilities().AL_SOFT_source_latency;
            clockReadSupported = sourceLatencySupported
                    && ALC10.alcIsExtensionPresent(device, "ALC_SOFT_device_clock");
            //? if >=1.20.5 {
            deviceClockSupported = clockReadSupported
                    && AL.getCapabilities().AL_SOFT_source_start_delay;
            //?} else {
            /*deviceClockSupported = false;
            *///?}
            if (deviceClockSupported) {
                ALC10.alcGetError(device);
                int refresh = ALC10.alcGetInteger(device, ALC10.ALC_REFRESH);
                boolean refreshAnswered = ALC10.alcGetError(device) == ALC10.ALC_NO_ERROR;
                if (refreshAnswered && refresh > 0) {
                    joinStartLeadNanos = Math.max(MIN_JOIN_START_LEAD_NANOS, 2L * 1_000_000_000L / refresh);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            deviceClockSupported = false;
            clockReadSupported = false;
            sourceLatencySupported = false;
        }
        deviceClockProbed = true;
        org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                "SOUNDSTAGE-DEVICE-CLOCK: supported={} readable={} startLeadMs={}",
                deviceClockSupported, clockReadSupported,
                joinStartLeadNanos / 1_000_000L);
        return deviceClockSupported;
    }

    public static boolean probeClockReadSupport() {
        probeDeviceClockSupport();
        return clockReadSupported;
    }

    public static boolean probeSourceLatencySupport() {
        probeDeviceClockSupport();
        return sourceLatencySupported;
    }

    public static boolean canAlignJoin(ClientAudioReceiver receiver) {
        if (com.nachtraben.soundstage.RigStaging.fault("no-aligned-join")) return false;
        return deviceClockProbed && deviceClockSupported
                && receiver.hasDeviceClockReference();
    }

    private final StreamSyncState sync = new StreamSyncState();

    public static final java.util.concurrent.atomic.AtomicLong SMOKE_BACKWARD_REPOSITIONS =
            new java.util.concurrent.atomic.AtomicLong();

    private final StreamingSoundInstance soundInstance;
    private final AudioFormat format;

    private static final boolean SMOKE = Boolean.getBoolean("soundstage.synctrace");
    private int readIndex;
    private long padTotal;
    private long shortTotal;

    public long getNextSequenceToRead() {
        return sync.nextSequence();
    }

    public int getRealisedDelayFrames() {
        return sync.realisedDelayFrames();
    }

    public JitterAudioStream(StreamingSoundInstance soundInstance) {
        this.soundInstance = soundInstance;
        this.format = new AudioFormat(48000f, 16, 1, true, false);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    private static final long EDGE_HOLD_MIN_QUEUED_SAMPLES = 5 * 960L;

    public static final java.util.concurrent.atomic.AtomicLong EDGE_HOLDS =
            new java.util.concurrent.atomic.AtomicLong();

    private byte[] reserve;

    private boolean tailFlushed;

    private boolean fadedOut = true;

    private static byte[] rampedIn(byte[] frame) {
        ByteBuffer in = ByteBuffer.wrap(frame).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        byte[] out = new byte[frame.length];
        ByteBuffer ob = ByteBuffer.wrap(out).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int samples = frame.length / 2;
        for (int i = 0; i < samples; i++) {
            ob.putShort(i * 2, (short) Math.round(in.getShort(i * 2) * (i / (double) samples)));
        }
        return out;
    }

    private static ByteBuffer rampedTail(byte[] frame) {
        ByteBuffer out = ByteBuffer.allocateDirect(frame.length);
        out.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        ByteBuffer in = ByteBuffer.wrap(frame).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int samples = frame.length / 2;
        for (int i = 0; i < samples; i++) {
            double gain = 1.0 - (i / (double) samples);
            out.putShort((short) Math.round(in.getShort(i * 2) * gain));
        }
        out.position(0);
        return out;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer out = fill(size);
        if (out != null && !handedFrames.handed(out.remaining() / 2) && !renderMeasureBroken) {
            markMirrorBroken();
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn(
                    "Soundstage: a speaker handed its engine a buffer its frame map does not describe; "
                            + "its mixer position is no longer published");
        }
        return out;
    }

    private ByteBuffer fill(int size) {
        ClientAudioReceiver receiver = AudioDecoder.getReceiver(soundInstance.getReceiverPos());
        if (receiver == null) {
            return ByteBuffer.allocateDirect(0);
        }

        boolean firstPump = sync.nextSequence() == -1;
        long groupStartSeq = soundInstance.getStartAnchorSequence();
        long ringLow = receiver.getLowestSequence();
        if (groupStartSeq == -1 || (ringLow != -1 && groupStartSeq < ringLow)) {
            groupStartSeq = receiver.getGroupStartSequence();
        }
        if (firstPump && soundInstance.isAlignToPlayingGroup()) {
            long measured = measuredJoinAnchor(receiver, ringLow, receiver.ring.highestSequence());
            if (measured != -1) groupStartSeq = measured;
        }
        int ownDelay = soundInstance.getDelayFrames();
        if (com.nachtraben.soundstage.RigStaging.fault("delay-reaches-siblings")) {
            for (StreamingSoundInstance sibling : receiver.streams.values()) {
                ownDelay = Math.max(ownDelay, sibling.getDelayFrames());
            }
        }
        final int delayFrames = ownDelay;
        long designed = receiver.getDesignedPosition();
        sync.beginPump(designed != -1 ? designed : receiver.getReferencePlayhead(), groupStartSeq,
                ringLow, receiver.ring.highestSequence(),
                delayFrames, receiver.latenessFrames, System.currentTimeMillis());
        if (sync.fellOffRingCount() != seenFloorClamps) {
            seenFloorClamps = sync.fellOffRingCount();
            soundInstance.setNeedsRestart(true);
            if (SMOKE) {
                org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                        "SOUNDSTAGE-FLOOR: speaker={} t={} fellTo={} ringHigh={} count={} -> restart",
                        System.identityHashCode(soundInstance), System.currentTimeMillis() % 1000000,
                        sync.nextSequence(), receiver.ring.highestSequence(), seenFloorClamps);
            }
        }
        if ((SMOKE || com.nachtraben.soundstage.RigStaging.ACTIVE) && sync.repositionCount() != seenRepositions) {
            seenRepositions = sync.repositionCount();
            if (sync.lastRepositionTo() < sync.lastRepositionFrom()) {
                SMOKE_BACKWARD_REPOSITIONS.incrementAndGet();
            }
            if (SMOKE) org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-YANK: speaker={} t={} from={} to={} designed={} ringHigh={} count={}",
                    System.identityHashCode(soundInstance), System.currentTimeMillis() % 1000000,
                    sync.lastRepositionFrom(), sync.lastRepositionTo(), designed,
                    receiver.ring.highestSequence(), seenRepositions);
        }
        if (firstPump && SMOKE) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-ANCHOR: speaker={} t={} stamp={} groupStart={} ringLow={} "
                            + "ringHigh={} size={} delay={} -> seq={} leadFromHead={} lineFrac={}",
                    System.identityHashCode(soundInstance), System.currentTimeMillis() % 1000000,
                    soundInstance.getStartAnchorSequence(), groupStartSeq, ringLow,
                    receiver.ring.highestSequence(),
                    receiver.ring.size(), soundInstance.getDelayFrames(), sync.nextSequence(),
                    receiver.ring.highestSequence() - sync.nextSequence(),
                    String.format("%.3f", receiver.smoothedHead.currentFrameExact(System.nanoTime())));
        }

        int bytesToReturn = Math.min(size, 9600);
        bytesToReturn = (bytesToReturn / 1920) * 1920;
        if (bytesToReturn == 0) bytesToReturn = 1920;

        ByteBuffer result = ByteBuffer.allocateDirect(bytesToReturn);
        result.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        long startSeq = sync.nextSequence();
        int framesFilled = 0;
        int renderedThisRead = 0;
        int padsThisRead = 0;

        while (result.hasRemaining()) {
            long lineBeforeSilence = sync.nextSequence() + sync.realisedDelayFrames();
            if (sync.consumeSilenceFrame()) {
                int silent = Math.min(1920, result.remaining());
                for (int i = 0; i < silent; i++) result.put((byte) 0);
                handedFrames.produceFiller(lineBeforeSilence, true);
                continue;
            }

            long wanted = sync.nextSequence();
            byte[] stereoPcm = receiver.ring.get(wanted);
            if (stereoPcm != null) {
                short[] surroundPcm = receiver.ring.surround(wanted);
                com.nachtraben.soundstage.core.SurroundDownmix.Layout layout = receiver.surroundLayout;
                byte[] rendered = soundInstance.renderAudioFrame(
                        () -> renderFrame(stereoPcm, surroundPcm, layout, wanted));

                if (fadedOut) {
                    rendered = rampedIn(rendered);
                    fadedOut = false;
                    if (SMOKE) {
                        org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                                "SOUNDSTAGE-DECLICK: speaker={} ramped in on resume",
                                System.identityHashCode(soundInstance));
                    }
                }
                if (reserve != null) {
                    result.put(reserve);
                    framesFilled++;
                    handedFrames.produceHeld();
                }
                reserve = rendered;
                handedFrames.hold(wanted, wanted + sync.realisedDelayFrames());
                renderedThisRead++;

                sync.frameConsumed(System.currentTimeMillis());
            } else if (result.position() == 0
                    && StreamSyncState.notYetProduced(wanted, receiver.ring.highestSequence())
                    && queuedBeyondPlayingBuffer() >= EDGE_HOLD_MIN_QUEUED_SAMPLES) {
                EDGE_HOLDS.incrementAndGet();
                break;
            } else if (StreamSyncState.notYetProduced(wanted, receiver.ring.highestSequence())
                    && (result.position() > 0 || reserve != null)) {
                if (result.position() == 0) {
                    handedFrames.produceHeld();
                    if (renderedThisRead > 0) {
                        result.put(reserve);
                        reserve = null;
                    } else {
                        result.put(rampedTail(reserve));
                        reserve = null;
                        fadedOut = true;
                        if (SMOKE) {
                            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                                    "SOUNDSTAGE-DECLICK: speaker={} ramped out into starvation",
                                    System.identityHashCode(soundInstance));
                        }
                    }
                    framesFilled++;
                }
                shortTotal++;
                if (SMOKE && shortTotal <= 5) {
                    org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                            "SOUNDSTAGE-SHORT: speaker={} read#{} wanted={} ringHigh={} "
                                    + "filled={} shortTotal={}",
                            System.identityHashCode(soundInstance), readIndex, wanted,
                            receiver.ring.highestSequence(), framesFilled, shortTotal);
                }
                break;
            } else {
                long now = System.currentTimeMillis();

                long resyncDesigned = receiver.getDesignedPosition();
                long resyncTarget = resyncDesigned == -1
                        ? -1
                        : resyncDesigned - delayFrames;
                if (sync.frameMissing(now, soundInstance.getLastPacketTime(), resyncTarget,
                        StreamSyncState.END_OF_STREAM_MILLIS + receiver.leadFrames * 20L)
                        == StreamSyncState.Gap.END_OF_STREAM) {
                    if (result.position() == 0) {
                        if (reserve != null && !tailFlushed) {
                            tailFlushed = true;
                            ByteBuffer tail = rampedTail(reserve);
                            reserve = null;
                            handedFrames.produceHeld();
                            if (SMOKE) {
                                org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                                        "SOUNDSTAGE-DECLICK: speaker={} ramped tail on stop",
                                        System.identityHashCode(soundInstance));
                            }
                            return tail;
                        }
                        return ByteBuffer.allocateDirect(0);
                    }
                    break;
                }

                if (StreamSyncState.notYetProduced(wanted, receiver.ring.highestSequence())) {
                    receiver.recordEdgeStarvation(System.nanoTime());
                }
                long lineBeforePad = sync.nextSequence() + sync.realisedDelayFrames();
                boolean slotConsumed = sync.padConsumesSlot(now, soundInstance.getLastPacketTime());
                padTotal++;
                padsThisRead++;
                if (SMOKE) {
                    org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                            "SOUNDSTAGE-PAD: speaker={} t={} read#{} wanted={} ringHigh={} "
                                    + "filled={} slotConsumed={} padTotal={}",
                            System.identityHashCode(soundInstance),
                            System.currentTimeMillis() % 1000000, readIndex, wanted,
                            receiver.ring.highestSequence(), framesFilled, slotConsumed, padTotal);
                }
                for (int i = 0; i < 1920; i++) {
                    result.put((byte) 0);
                }
                handedFrames.produceFiller(lineBeforePad, slotConsumed);
            }
        }

        if (SMOKE && (readIndex < 8 || padsThisRead > 0)) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-READ: speaker={} t={} read#{} fromSeq={} toSeq={} filled={} "
                            + "pads={} ringHigh={} bytes={}",
                    System.identityHashCode(soundInstance), System.currentTimeMillis() % 1000000,
                    readIndex, startSeq, sync.nextSequence(), framesFilled, padsThisRead,
                    receiver.ring.highestSequence(), result.position());
        }
        readIndex++;

        result.flip();
        if (firstEmittedSeq == -1 && result.remaining() > 0) {
            firstEmittedSeq = startSeq;
        }
        framesHanded += result.remaining() / 1920;
        if (SMOKE && readIndex % 25 == 0) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-CUM: speaker={} t={} handed={} nextSeq={} anchor={} real={} "
                            + "padC={} unaccounted={} padTotal={} shortTotal={}",
                    System.identityHashCode(soundInstance), System.currentTimeMillis() % 1000000,
                    framesHanded, sync.nextSequence(), sync.anchorSeq(), sync.realConsumed(),
                    sync.padsConsumed(), sync.unaccounted(), padTotal, shortTotal);
        }
        return result;
    }

    private byte[] renderFrame(byte[] stereoPcm, short[] surroundPcm,
                               com.nachtraben.soundstage.core.SurroundDownmix.Layout layout, long sequence) {
        ByteBuffer processedMonoBuffer = SpeakerOutput.render(
                stereoPcm, surroundPcm, layout, soundInstance.getChannelMode(), soundInstance.getDsp(),
                soundInstance.getSpeakerGain());
        ShortBuffer processedMonoShorts = processedMonoBuffer.asShortBuffer();

        soundInstance.traceFrame(sequence, processedMonoShorts, format.getSampleRate());

        processedMonoBuffer.position(0);
        byte[] rendered = new byte[processedMonoBuffer.remaining()];
        processedMonoBuffer.get(rendered);
        return rendered;
    }

    private long framesHanded;

    private long seenRepositions;

    private long seenFloorClamps;

    private volatile long firstEmittedSeq = -1;

    private static final int JOIN_ANCHOR_MARGIN_FRAMES = 2;

    private long measuredJoinAnchor(ClientAudioReceiver receiver, long ringLow, long ringHigh) {
        if (!probeDeviceClockSupport()) return -1;
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        if (device == 0L) return -1;
        long now = SOFTDeviceClock.alcGetInteger64vSOFT(device, SOFTDeviceClock.ALC_DEVICE_CLOCK_SOFT);
        OptionalDouble groupSample = receiver.groupSampleAt(now, soundInstance.getPos());
        if (groupSample.isEmpty()) return -1;
        long anchor = (long) Math.floor(groupSample.getAsDouble() / 960.0) - JOIN_ANCHOR_MARGIN_FRAMES;
        if (ringHigh != -1) anchor = Math.min(anchor, ringHigh);
        if (ringLow != -1) anchor = Math.max(anchor, ringLow);
        return anchor;
    }

    public boolean playAlignedToGroup(int source) {
        if (!probeDeviceClockSupport()
                || !soundInstance.isAlignToPlayingGroup() || firstEmittedSeq == -1) {
            return false;
        }

        ClientAudioReceiver receiver = AudioDecoder.getReceiver(soundInstance.getReceiverPos());
        if (receiver == null) return false;
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        if (device == 0L) return false;

        long startClock = SOFTDeviceClock.alcGetInteger64vSOFT(
                device, SOFTDeviceClock.ALC_DEVICE_CLOCK_SOFT) + joinStartLeadNanos;
        OptionalDouble groupSample = receiver.groupSampleAt(startClock, soundInstance.getPos());
        if (groupSample.isEmpty()) {
            restartGroupAfterFailedJoin(receiver, "no fresh sibling observation", 0L);
            return false;
        }

        double seek = legacyGroupMapping()
                ? handedFrames.legacyQueueOffsetOf(groupSample.getAsDouble(), firstEmittedSeq,
                        sync.realisedDelayFrames())
                : handedFrames.queueOffsetOf(groupSample.getAsDouble());
        int offsetFrames = com.nachtraben.soundstage.RigStaging.fault("join-offset") ? 3 : 0;
        long skipSamples = Double.isNaN(seek) ? -1L : Math.round(seek) + offsetFrames * 960L;
        long maximumSkip = Math.max(0L, framesHanded * 960L - 960L);
        if (skipSamples < 0L || skipSamples > maximumSkip || skipSamples > Integer.MAX_VALUE) {
            restartGroupAfterFailedJoin(receiver, "queued " + framesHanded * 960L + " samples", skipSamples);
            return false;
        }

        AL10.alSourcei(source, AL11.AL_SAMPLE_OFFSET, (int) skipSamples);
        //? if >=1.20.5 {
        SOFTSourceStartDelay.alSourcePlayAtTimeSOFT(source, startClock);
        //?} else {
        /*AL10.alSourcePlay(source);
        *///?}
        if (SMOKE) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-DEVICE-CLOCK: aligned speaker={} first={} skip={} start={}",
                    System.identityHashCode(soundInstance), firstEmittedSeq, skipSamples, startClock);
        }
        return true;
    }

    public boolean playAtGroupStart(int source) {
        GroupStartClock group = soundInstance.getGroupStart();
        if (group == null) return false;
        if (!probeDeviceClockSupport()) return holdForGroup(group, source);
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        if (device == 0L) return false;
        long now = SOFTDeviceClock.alcGetInteger64vSOFT(device, SOFTDeviceClock.ALC_DEVICE_CLOCK_SOFT);
        long start = group.claim(now + joinStartLeadNanos);
        if (splitGroupStart()) start += group.nextIndex() * 60_000_000L;
        if (start <= now) {
            if (SMOKE) {
                org.slf4j.LoggerFactory.getLogger("Soundstage").warn(
                        "SOUNDSTAGE-DEVICE-CLOCK: group start passed {} ms ago, speaker={} plays at once",
                        (now - start) / 1_000_000L, System.identityHashCode(soundInstance));
            }
            return false;
        }
        //? if >=1.20.5 {
        SOFTSourceStartDelay.alSourcePlayAtTimeSOFT(source, start);
        //?} else {
        /*AL10.alSourcePlay(source);
        *///?}
        if (SMOKE) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-DEVICE-CLOCK: group start speaker={} start={} leadMs={}",
                    System.identityHashCode(soundInstance), start, (start - now) / 1_000_000L);
        }
        return true;
    }

    private volatile GroupStartClock heldGroup;

    private boolean holdForGroup(GroupStartClock group, int source) {
        heldGroup = group;
        long now = System.nanoTime();
        int[] start = group.hold(source, now);
        if (start == null && splitGroupStart() && group.nextIndex() == 0) start = group.releaseIfDue(now, 0);
        if (start != null) playTogether(start);
        return true;
    }

    public void releaseHeldGroupIfDue() {
        GroupStartClock group = heldGroup;
        if (group == null) return;
        int[] start = group.releaseIfDue(System.nanoTime(),
                splitGroupStart() ? 60_000_000L : GroupStartClock.HOLD_LIMIT_NANOS);
        if (start != null) playTogether(start);
    }

    private static boolean splitGroupStart() {
        return com.nachtraben.soundstage.RigStaging.fault("split-starts");
    }

    private void playTogether(int[] sources) {
        AL10.alSourcePlayv(sources);
        if (SMOKE) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-DEVICE-CLOCK: group of {} started together (alSourcePlayv)", sources.length);
        }
    }

    private void restartGroupAfterFailedJoin(ClientAudioReceiver receiver, String reason, long skip) {
        for (StreamingSoundInstance stream : receiver.streams.values()) {
            stream.setNeedsRestart(true);
        }
        if (SMOKE) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn(
                    "SOUNDSTAGE-DEVICE-CLOCK: could not align speaker={} skip={} ({}) -> group restart",
                    System.identityHashCode(soundInstance), skip, reason);
        }
    }

    public void onDeviceClockMeasure(long fixedPointSampleOffset, long deviceClockNanos) {
        if (firstEmittedSeq == -1) return;
        ClientAudioReceiver receiver = AudioDecoder.getReceiver(soundInstance.getReceiverPos());
        if (receiver == null) return;
        if (renderMeasureBroken) return;
        double sourceOffset = fixedPointSampleOffset / 4_294_967_296.0;
        if (!locateMixer(sourceOffset)) return;
        receiver.recordDeviceClock(soundInstance, mixerGroupSample, deviceClockNanos);
        recordMixer(receiver, deviceClockNanos, ClientAudioReceiver.HEARD_FROM_DEVICE_CLOCK, System.nanoTime());
    }

    private double mixerGroupSample;

    private boolean locateMixer(double queueOffset) {
        if (legacyGroupMapping()) {
            mixerGroupSample = handedFrames.legacyGroupSampleAt(queueOffset, firstEmittedSeq,
                    sync.realisedDelayFrames());
            return true;
        }
        if (!handedFrames.locate(queueOffset)) return false;
        mixerGroupSample = handedFrames.locatedGroupSample();
        return true;
    }

    private void recordMixer(ClientAudioReceiver receiver, long deviceClockNanos, long heardAtNanos,
                             long sampledAtNanos) {
        receiver.recordMixer(soundInstance, mixerGroupSample,
                legacyGroupMapping() ? null : handedFrames.heardDelays(),
                deviceClockNanos, heardAtNanos, handedEndSample(), sampledAtNanos);
    }

    private double handedEndSample() {
        return legacyGroupMapping()
                ? handedFrames.legacyHandedEndSample(firstEmittedSeq, sync.realisedDelayFrames())
                : handedFrames.handedEndSample();
    }

    public void onRenderClock(double sourceOffsetSamples, long latencyNanos, long sampledAtNanos, long device) {
        if (firstEmittedSeq == -1 || renderMeasureBroken) return;
        ClientAudioReceiver receiver = AudioDecoder.getReceiver(soundInstance.getReceiverPos());
        if (receiver == null) return;
        if (!locateMixer(sourceOffsetSamples)) return;
        long asClock = Math.round(mixerGroupSample * NANOS_PER_SAMPLE);
        renderClock.offer(asClock, latencyNanos, sampledAtNanos);
        long offset = renderClock.heardOffsetNanos();
        if (offset == com.nachtraben.soundstage.core.DeviceClockMap.UNKNOWN) return;
        recordMixer(receiver, ClientAudioReceiver.NO_DEVICE_CLOCK, asClock - offset, sampledAtNanos);
    }

    private static final double NANOS_PER_SAMPLE = 1_000_000_000.0 / 48_000.0;

    private final com.nachtraben.soundstage.core.DeviceClockMap renderClock =
            new com.nachtraben.soundstage.core.DeviceClockMap();

    private final HandedFrameMap handedFrames = new HandedFrameMap();
    private volatile boolean renderMeasureBroken;
    private int renderMeasureCount;

    private long queuedBeyondPlayingBuffer() {
        return renderMeasureBroken ? 0 : handedFrames.samplesBeyondHead();
    }

    public boolean isQueueMirrorBroken() {
        return renderMeasureBroken;
    }

    public void onBuffersQueued(int alQueued) {
        if (!renderMeasureBroken && alQueued != handedFrames.queuedBuffers()) {
            markMirrorBroken();
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn(
                    "Soundstage: OpenAL holds {} buffers for a speaker whose mirror holds {}; "
                            + "edge hold disabled for this stream", alQueued, handedFrames.queuedBuffers());
        }
    }

    public void onBuffersUnqueued(int count) {
        if (!handedFrames.retired(count)) {
            markMirrorBroken();
        }
    }

    private void markMirrorBroken() {
        renderMeasureBroken = true;
        ClientAudioReceiver receiver = AudioDecoder.getReceiver(soundInstance.getReceiverPos());
        if (receiver != null) receiver.forgetDeviceClock(soundInstance);
    }

    public void onRenderMeasure(int sampleOffset, long nanos) {
        if (renderMeasureBroken) return;
        long first = firstEmittedSeq;
        ClientAudioReceiver receiver = AudioDecoder.getReceiver(soundInstance.getReceiverPos());
        if (receiver == null || first == -1) return;
        double lineExact = receiver.smoothedHead.currentFrameExact(nanos);
        if (Double.isNaN(lineExact)) return;
        if (!handedFrames.locate(sampleOffset)) return;
        long rendered = handedFrames.retiredSamples() + sampleOffset;
        double contentAtMixer = handedFrames.locatedContentSample() / 960.0;
        double lateFrames = lineExact - receiver.leadFrames + 1
                - handedFrames.locatedGroupSample() / 960.0;
        renderMeasureCount++;
        if (SMOKE && (renderMeasureCount <= 6 || renderMeasureCount % 50 == 0)) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-AUDIBLE: speaker={} t={} late={} rendered={} first={} atMixer={} "
                            + "queued={} bufs={}",
                    System.identityHashCode(soundInstance), System.currentTimeMillis() % 1000000,
                    String.format("%.3f", lateFrames), rendered, first,
                    String.format("%.3f", contentAtMixer),
                    String.format("%.3f", (framesHanded * 960.0 - rendered) / 960.0),
                    handedFrames.queuedBuffers());
        }
    }

    @Override
    public void close() throws IOException {
        soundInstance.markChannelClosed();
    }
}
