package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.core.ArrivalLateness;
import com.nachtraben.soundstage.core.AudibleClock;
import com.nachtraben.soundstage.core.AudioDSP;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.core.DeviceClockTimeline;
import com.nachtraben.soundstage.core.HandedFrameMap;
import com.nachtraben.soundstage.core.HeardDelays;
import com.nachtraben.soundstage.core.LevelMeterModel;
import com.nachtraben.soundstage.core.OpusCodec;
import com.nachtraben.soundstage.core.PcmRing;
import com.nachtraben.soundstage.core.SmoothedHead;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import com.nachtraben.soundstage.core.SpeakerOutput;
import com.nachtraben.soundstage.core.StartPhaseGrid;
import com.nachtraben.soundstage.core.StreamSyncState;
import com.nachtraben.soundstage.core.SurroundDownmix;
import com.nachtraben.soundstage.core.TraceBuffer;
import com.nachtraben.soundstage.core.TubeThermalModel;
import com.nachtraben.soundstage.core.video.AudioTimeline;
import com.nachtraben.soundstage.network.AudioPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientAudioReceiver {

    public final BlockPos receiverPos;
    public final OpusCodec.Decoder decoder;

    public final PcmRing ring = new PcmRing();
    public final Map<BlockPos, StreamingSoundInstance> streams = new ConcurrentHashMap<>();

    private final AudioTimeline timeline = new AudioTimeline();

    public AudioTimeline timeline() {
        return timeline;
    }

    private final Map<SpeakerAnimation.Key, SpeakerAnimation> animations = new ConcurrentHashMap<>();

    SpeakerAnimation animationFor(SpeakerAnimation.Key key) {
        return animations.computeIfAbsent(key, k -> new SpeakerAnimation(k.acoustics()));
    }

    void releaseAnimation(SpeakerAnimation.Key key) {
        for (StreamingSoundInstance other : streams.values()) {
            if (key.equals(other.animationKey())) return;
        }
        SpeakerAnimation dropped = animations.remove(key);
        if (dropped != null) dropped.clear();
    }

    private final Map<BlockPos, DeviceClockObservation> deviceClockObservations =
            new ConcurrentHashMap<>();

    private record DeviceClockObservation(StreamingSoundInstance stream,
                                          DeviceClockTimeline.Point point) {}

    void recordDeviceClock(StreamingSoundInstance stream, double groupSample,
                           long deviceClockNanos) {
        BlockPos pos = stream.getPos();
        if (streams.get(pos) == stream) {
            deviceClockObservations.put(pos, new DeviceClockObservation(
                    stream, new DeviceClockTimeline.Point(groupSample, deviceClockNanos)));
        }
    }

    void forgetDeviceClock(StreamingSoundInstance stream) {
        deviceClockObservations.computeIfPresent(stream.getPos(), (pos, observation) ->
                observation.stream() == stream ? null : observation);
        mixerObservations.computeIfPresent(stream.getPos(), (pos, observation) ->
                observation.stream() == stream ? null : observation);
    }

    static final long NO_DEVICE_CLOCK = Long.MIN_VALUE;

    static final long HEARD_FROM_DEVICE_CLOCK = Long.MIN_VALUE;

    private record MixerObservation(StreamingSoundInstance stream, double groupSample,
                                    HeardDelays heardDelays, long deviceClockNanos, long heardAtNanos,
                                    double handedEndSample, long sampledAtNanos) {}

    private final Map<BlockPos, MixerObservation> mixerObservations = new ConcurrentHashMap<>();

    void recordMixer(StreamingSoundInstance stream, double groupSample, HeardDelays heardDelays,
                     long deviceClockNanos, long heardAtNanos, double handedEndSample, long sampledAtNanos) {
        BlockPos pos = stream.getPos();
        if (streams.get(pos) == stream) {
            mixerObservations.put(pos, new MixerObservation(stream, groupSample, heardDelays,
                    deviceClockNanos, heardAtNanos, handedEndSample, sampledAtNanos));
        }
    }

    public static final double NO_OWN_SEQUENCE = Double.NEGATIVE_INFINITY;

    double ownSequenceAt(StreamingSoundInstance stream, double groupSequence) {
        MixerObservation observation = mixerObservations.get(stream.getPos());
        if (observation == null || observation.stream() != stream || observation.heardDelays() == null
                || System.nanoTime() - observation.sampledAtNanos() > MIXER_FRESH_NANOS) {
            return NO_OWN_SEQUENCE;
        }
        double delay = observation.heardDelays().delayAt(groupSequence * HandedFrameMap.FRAME_SAMPLES);
        if (delay == HeardDelays.UNKNOWN) return NO_OWN_SEQUENCE;
        return Double.isNaN(delay) ? Double.NaN : groupSequence - delay;
    }

    boolean hasDeviceClockReference() {
        for (var entry : deviceClockObservations.entrySet()) {
            DeviceClockObservation observation = entry.getValue();
            if (streams.get(entry.getKey()) == observation.stream()) return true;
        }
        return false;
    }

    OptionalDouble groupSampleAt(long deviceClockNanos, BlockPos joiningSpeaker) {
        ArrayList<DeviceClockTimeline.Point> points = new ArrayList<>();
        for (var entry : deviceClockObservations.entrySet()) {
            if (entry.getKey().equals(joiningSpeaker)) continue;
            DeviceClockObservation observation = entry.getValue();
            if (streams.get(entry.getKey()) == observation.stream()) {
                points.add(observation.point());
            }
        }
        return DeviceClockTimeline.medianAt(points, deviceClockNanos, OpusCodec.SAMPLE_RATE,
                250_000_000L);
    }

    public double audibleSpreadFrames() {
        long newest = Long.MIN_VALUE;
        for (var entry : deviceClockObservations.entrySet()) {
            DeviceClockObservation observation = entry.getValue();
            if (streams.get(entry.getKey()) != observation.stream()) continue;
            if (observation.stream().getDelayFrames() != 0) continue;
            newest = Math.max(newest, observation.point().deviceClockNanos());
        }
        return newest == Long.MIN_VALUE ? -1 : audibleSpreadFrames(newest);
    }

    public double audibleSpreadFrames(long deviceClockNanos) {
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        int seen = 0;
        for (var entry : deviceClockObservations.entrySet()) {
            DeviceClockObservation observation = entry.getValue();
            if (streams.get(entry.getKey()) != observation.stream()) continue;
            if (observation.stream().getDelayFrames() != 0) continue;
            long age = deviceClockNanos - observation.point().deviceClockNanos();
            if (age < 0 || age > 250_000_000L) continue;
            double at = observation.point().groupSample()
                    + age * (OpusCodec.SAMPLE_RATE / 1_000_000_000.0);
            lowest = Math.min(lowest, at);
            highest = Math.max(highest, at);
            seen++;
        }
        return seen < 2 ? -1 : (highest - lowest) / 960.0;
    }

    private final AudibleClock audibleClock = new AudibleClock();

    private final AudibleClock.Positions heardPositions = new AudibleClock.Positions();

    private static final long MIXER_FRESH_NANOS = 250_000_000L;

    private static final boolean CLOCK_TRACE = Boolean.getBoolean("soundstage.synctrace");
    private static final long CLOCK_TRACE_INTERVAL_NANOS = 2_000_000_000L;
    private long lastClockTraceNanos;

    private final TraceBuffer leftLevel = new TraceBuffer(PcmRing.RETAINED_FRAMES);
    private final TraceBuffer rightLevel = new TraceBuffer(PcmRing.RETAINED_FRAMES);

    private final LevelMeterModel leftMeter = new LevelMeterModel(OpusCodec.SAMPLE_RATE);
    private final LevelMeterModel rightMeter = new LevelMeterModel(OpusCodec.SAMPLE_RATE);

    private final AudioDSP leftFlat = new AudioDSP(AudioDSP.FilterType.NONE, OpusCodec.SAMPLE_RATE, 1000f, 0.707f);
    private final AudioDSP rightFlat = new AudioDSP(AudioDSP.FilterType.NONE, OpusCodec.SAMPLE_RATE, 1000f, 0.707f);

    private final TubeThermalModel tubes = new TubeThermalModel();

    private static final float FRAME_SECONDS = 0.02f;

    public void traceOutput(long sequence, byte[] stereoPcm, int speakerCount) {
        float[] left = leftMeter.trace(
                SpeakerOutput.render(stereoPcm, ChannelMode.LEFT, leftFlat).asShortBuffer());
        float[] right = rightMeter.trace(
                SpeakerOutput.render(stereoPcm, ChannelMode.RIGHT, rightFlat).asShortBuffer());
        leftLevel.record(sequence, left);
        rightLevel.record(sequence, right);

        tubes.advance(TubeThermalModel.drive(mean(left), mean(right), speakerCount), FRAME_SECONDS);
    }

    private static float mean(float[] trace) {
        if (trace.length == 0) return 0f;
        float total = 0f;
        for (float value : trace) total += value;
        return total / trace.length;
    }

    public float tubeGlow() {
        return tubes.glow();
    }

    public float tubeRedness() {
        return tubes.redness();
    }

    public String meterTraceState() {
        return leftLevel.size() + " frames, sequences " + leftLevel.lowestSequence()
                + ".." + leftLevel.highestSequence();
    }

    public float sampleLeftLevel(double sequence) {
        return leftLevel.sample(sequence);
    }

    public float sampleRightLevel(double sequence) {
        return rightLevel.sample(sequence);
    }

    public final Map<BlockPos, SpeakerAcoustics>
            declaredAcoustics = new ConcurrentHashMap<>();

    public final Map<BlockPos, Integer> declaredVolumes =
            new ConcurrentHashMap<>();

    private final AtomicInteger unfilteredFrames =
            new AtomicInteger();
    private final AtomicInteger wrongFilterFrames =
            new AtomicInteger();

    void recordFilterFrame(boolean unresolved, boolean wrong) {
        if (unresolved) unfilteredFrames.incrementAndGet();
        if (wrong) wrongFilterFrames.incrementAndGet();
    }

    void recordWrongFilterFrames(int count) {
        if (count > 0) wrongFilterFrames.addAndGet(count);
    }

    public int getUnfilteredFrames() {
        return unfilteredFrames.get();
    }

    public int getWrongFilterFrames() {
        return wrongFilterFrames.get();
    }

    public ClientAudioReceiver(BlockPos pos, OpusCodec.Decoder decoder) {
        this.receiverPos = pos;
        this.decoder = decoder;
    }

    public long getLowestSequence() {
        return ring.lowestSequence();
    }

    public long getReferencePlayhead() {
        long head = -1;
        for (StreamingSoundInstance stream : streams.values()) {
            if (stream.getJitterStream() != null) {
                long own = stream.getJitterStream().getNextSequenceToRead();
                if (own == -1) continue;
                long logical = own + stream.getJitterStream().getRealisedDelayFrames();
                if (logical > head) head = logical;
            }
        }
        if (head != -1) return head;
        return getCurrentPlayhead();
    }

    public long getCurrentPlayhead() {
        long head = -1;
        for (StreamingSoundInstance stream : streams.values()) {
            if (stream.getJitterStream() != null) {
                long sHead = stream.getJitterStream().getNextSequenceToRead();
                if (sHead > head) head = sHead;
            }
        }
        if (head != -1) return head;

        long lowest = getLowestSequence();
        if (lowest == -1) return -1;
        long highest = ring.highestSequence();
        if (highest == -1) return lowest;
        return Math.max(Math.max(lowest, resumeFloorSequence), highest - leadFrames + 1);
    }

    private long groupStartSequence(long lowest) {
        long highest = ring.highestSequence();
        if (highest == -1) return lowest;
        double designedExact = smoothedHead.currentFrameExact(System.nanoTime())
                - leadFrames + 1;
        long floor = Math.max(lowest, resumeFloorSequence);
        if (!Double.isNaN(designedExact)) {
            long anchor = startPhaseGrid.anchorFor(designedExact);
            if (anchor > highest) anchor = highest;
            return Math.max(floor, anchor);
        }
        return Math.max(floor, highest - leadFrames + 1);
    }

    public volatile Set<BlockPos> lastDispatchTargets = Set.of();

    public volatile long lastPacketNanos = 0L;

    public volatile int leadFrames = StreamSyncState.PREROLL_FRAMES;

    public volatile int latenessFrames;

    public final ArrivalLateness arrivalLateness =
            new ArrivalLateness();

    public volatile long resumeFloorSequence = -1;

    public volatile long lastEdgeStarvedNanos;

    public volatile long edgeStarvedSinceNanos;

    public void recordEdgeStarvation(long nanos) {
        if (nanos - lastEdgeStarvedNanos > 200_000_000L) {
            edgeStarvedSinceNanos = nanos;
        }
        lastEdgeStarvedNanos = nanos;
    }

    public long edgeStarvedMillis(long nanos) {
        if (lastEdgeStarvedNanos == 0L || nanos - lastEdgeStarvedNanos > 200_000_000L) return 0L;
        return (lastEdgeStarvedNanos - edgeStarvedSinceNanos) / 1_000_000L;
    }

    private final StartPhaseGrid startPhaseGrid =
            new StartPhaseGrid();

    public long getGroupStartSequence() {
        long lowest = getLowestSequence();
        if (lowest == -1) return -1;
        return groupStartSequence(lowest);
    }

    public double getAudibleSequence() {
        long now = System.nanoTime();
        long pumped = getReferencePlayhead();
        boolean pumpIsFill = false;
        for (StreamingSoundInstance stream : streams.values()) {
            JitterAudioStream jitter = stream.getJitterStream();
            if (jitter != null && jitter.getNextSequenceToRead() != -1) {
                pumpIsFill = true;
                break;
            }
        }
        DeviceClockSampler.DeviceTime device = DeviceClockSampler.current();
        heardPositions.clear();
        for (var entry : mixerObservations.entrySet()) {
            MixerObservation observation = entry.getValue();
            if (streams.get(entry.getKey()) != observation.stream()) continue;
            if (now - observation.sampledAtNanos() > MIXER_FRESH_NANOS) continue;
            long heardAt;
            if (observation.deviceClockNanos() != NO_DEVICE_CLOCK) {
                if (device == null) continue;
                heardAt = observation.deviceClockNanos() - device.heardOffsetNanos();
            } else {
                heardAt = observation.heardAtNanos();
            }
            heardPositions.add(observation.groupSample(), heardAt, observation.handedEndSample(), now);
        }
        double latencyFrames = device == null ? 0.0 : device.latencyNanos() / AudibleClock.NANOS_PER_FRAME;
        double audible = audibleClock.sample(now, new AudibleClock.Reading(pumped, pumpIsFill,
                heardPositions.medianSequence(), heardPositions.capSequence(), latencyFrames));
        if (CLOCK_TRACE && now - lastClockTraceNanos > CLOCK_TRACE_INTERVAL_NANOS && pumped != -1) {
            lastClockTraceNanos = now;
            double old = pumped - 8.0;
            LoggerFactory.getLogger("Soundstage").info(
                    "SOUNDSTAGE-CLOCK: receiver={} source={} speakers={} audible={} fillMinus8={} ({} frames ahead) "
                            + "fillLead={} deviceLatencyMs={}",
                    receiverPos.toShortString(), audibleClock.source(), heardPositions.count(),
                    String.format(Locale.ROOT, "%.2f", audible),
                    String.format(Locale.ROOT, "%.2f", old),
                    audible < 0 ? "n/a" : String.format(Locale.ROOT, "%+.2f", old - audible),
                    String.format(Locale.ROOT, "%.2f", audibleClock.fillLeadFrames()),
                    device == null ? "n/a" : String.format(Locale.ROOT, "%.2f", device.latencyNanos() / 1e6));
        }
        return audible;
    }

    public double audibleSequenceThisFrame() {
        long frame = renderFrame;
        if (sampledFrame != frame) {
            sampledFrame = frame;
            sampledAudible = getAudibleSequence();
        }
        return sampledAudible;
    }

    public static void endRenderFrame() {
        renderFrame++;
    }

    public static long renderFrame() {
        return renderFrame;
    }

    private static long renderFrame;
    private long sampledFrame = -1;
    private double sampledAudible;

    public final SmoothedHead smoothedHead =
            new SmoothedHead();

    public long getDesignedPosition() {
        long line = smoothedHead.currentFrame(System.nanoTime());
        if (line == -1) return -1;
        long designed = line - leadFrames + 1;
        long lowest = ring.lowestSequence();
        long highest = ring.highestSequence();
        if (highest != -1 && designed > highest) designed = highest;
        if (lowest != -1 && designed < lowest) designed = lowest;
        return designed;
    }

    public void addSequence(long sequence, byte[] stereoPcm) {
        addSequence(sequence, stereoPcm, null);
    }

    public void addSequence(long sequence, byte[] stereoPcm, short[] surroundPcm) {
        smoothedHead.observe(sequence, System.nanoTime());
        ring.add(sequence, stereoPcm, surroundPcm);
    }

    public volatile SurroundDownmix.Layout surroundLayout =
            SurroundDownmix.Layout.NONE;

    public void refreshSurroundLayout() {
        EnumSet<ChannelMode> modes = EnumSet.noneOf(ChannelMode.class);
        for (StreamingSoundInstance stream : streams.values()) {
            ChannelMode mode = stream.getChannelMode();
            if (mode != null) modes.add(mode);
        }
        surroundLayout = SurroundDownmix.Layout.of(modes);
    }

    private OpusCodec.Decoder frontDecoder;
    private OpusCodec.Decoder centreDecoder;
    private OpusCodec.Decoder rearDecoder;

    public short[] decodeSurround(AudioPacket.SurroundBlock block) {
        if (block == null) return null;
        if (frontDecoder == null) {
            frontDecoder = new OpusCodec.Decoder();
            centreDecoder = new OpusCodec.Decoder();
            rearDecoder = new OpusCodec.Decoder();
        }
        byte[] front = frontDecoder.decode(block.flFr());
        byte[] centre = centreDecoder.decode(block.clfe());
        byte[] rear = rearDecoder.decode(block.slSr());
        if (front == null || centre == null || rear == null
                || front.length != centre.length || front.length != rear.length) {
            return null;
        }
        int samples = front.length / 4;
        ShortBuffer f = ByteBuffer.wrap(front).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        ShortBuffer c = ByteBuffer.wrap(centre).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        ShortBuffer r = ByteBuffer.wrap(rear).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] six = new short[samples * 6];
        for (int s = 0; s < samples; s++) {
            six[s * 6] = f.get(s * 2);
            six[s * 6 + 1] = f.get(s * 2 + 1);
            six[s * 6 + 2] = c.get(s * 2);
            six[s * 6 + 3] = c.get(s * 2 + 1);
            six[s * 6 + 4] = r.get(s * 2);
            six[s * 6 + 5] = r.get(s * 2 + 1);
        }
        return six;
    }

    public volatile boolean destroyed;

    public synchronized void destroy() {
        destroyed = true;
        decoder.close();
        if (frontDecoder != null) {
            frontDecoder.close();
            centreDecoder.close();
            rearDecoder.close();
        }
        for (StreamingSoundInstance stream : streams.values()) {
            stream.clearExcursion();
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().getSoundManager().stop(stream);
            });
        }
        streams.clear();
        deviceClockObservations.clear();
        mixerObservations.clear();
        ring.clear();
        timeline.clear();
        resumeFloorSequence = -1;
        smoothedHead.reset();
        startPhaseGrid.reset();
        audibleClock.reset();
        leftLevel.clear();
        rightLevel.clear();
        tubes.reset();
    }
}
