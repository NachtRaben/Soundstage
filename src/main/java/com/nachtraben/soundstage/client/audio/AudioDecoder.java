package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.core.ChannelDownmix;
import com.nachtraben.soundstage.core.LevelMeterModel;
import com.nachtraben.soundstage.core.ConeExcursionModel;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.core.SpeakerOutput;
import com.nachtraben.soundstage.core.OpusCodec;
import com.nachtraben.soundstage.core.SpeakerId;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import com.nachtraben.soundstage.core.SpeakerPriority;
import com.nachtraben.soundstage.core.SpeakerHost;
import com.nachtraben.soundstage.core.SpeakerDispatch;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.network.AudioPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioDecoder {

    public static final java.util.concurrent.atomic.AtomicLong GROUP_RESTARTS =
            new java.util.concurrent.atomic.AtomicLong();

    public record GroupRestart(long arrivalGapMillis, long starvedMillis, int leadBefore, int leadAfter,
                               boolean producerGap) {}

    public static volatile GroupRestart lastGroupRestart;

    public static final java.util.concurrent.atomic.AtomicLong RENUMBERS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final long MIN_STARVED_MILLIS = 60;

    private static final int MIN_LEAD_GROWTH_FRAMES = 5;

    private static final long REBUILD_STARVED_MILLIS = 1000;

    private static final int SAMPLE_RATE = OpusCodec.SAMPLE_RATE;
    private static final int CHANNELS = OpusCodec.CHANNELS;
    
    private static final Map<BlockPos, ClientAudioReceiver> receivers = new ConcurrentHashMap<>();

    private record Listener(double x, double y, double z) {}

    private static volatile Listener listener;

    public static void tick(Minecraft minecraft) {
        Player player = minecraft.player;
        Listener now = minecraft.level == null || player == null || minecraft.getConnection() == null ? null
                : new Listener(player.getX(), player.getY(), player.getZ());
        if (now == null && !receivers.isEmpty()) clear();
        listener = now;
    }

    public static void clear() {
        listener = null;
        for (BlockPos pos : receivers.keySet()) {
            removeReceiver(pos);
        }
    }

    public static ClientAudioReceiver getReceiver(BlockPos pos) {
        return receivers.get(pos);
    }

    public static int receiverCount() {
        return receivers.size();
    }

    public static void removeReceiver(BlockPos pos) {
        ClientAudioReceiver receiver = receivers.remove(pos);
        if (receiver != null) {
            receiver.destroy();
        }
    }

    public static void removeSpeaker(BlockPos pos) {
        for (ClientAudioReceiver receiver : receivers.values()) {
            synchronized (receiver) {
                StreamingSoundInstance stream = receiver.streams.remove(pos);
                if (stream == null) {
                    continue;
                }
                receiver.forgetDeviceClock(stream);

                stream.clearExcursion();
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().getSoundManager().stop(stream);
                });

                if (receiver.streams.isEmpty()) {
                    receivers.remove(receiver.receiverPos);
                    receiver.destroy();
                }
            }
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean THREAD_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean UNAVAILABLE_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicLong DROPPED_WITHOUT_CODEC =
            new java.util.concurrent.atomic.AtomicLong();

    public static long droppedWithoutCodec() {
        return DROPPED_WITHOUT_CODEC.get();
    }

    private static void reportAudioUnavailable() {
        if (!UNAVAILABLE_REPORTED.compareAndSet(false, true)) return;
        org.slf4j.LoggerFactory.getLogger("Soundstage").error(
                "Soundstage cannot play audio on this system: the Opus native library did not load for this "
                        + "platform, so incoming audio is being dropped.", OpusCodec.unavailableCause());
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var message = net.minecraft.network.chat.Component.translatable(
                        "chat.soundstage.audio_unavailable").withStyle(net.minecraft.ChatFormatting.RED);
                //? if >=26.1 {
                /*player.sendSystemMessage(message);
                *///?} else {
                player.displayClientMessage(message, false);
                //?}
            }
        });
    }

    public static void handlePacket(AudioPacket packet) {
        if (!OpusCodec.available()) {
            DROPPED_WITHOUT_CODEC.incrementAndGet();
            reportAudioUnavailable();
            return;
        }
        Listener here = listener;
        if (here == null) return;
        if (!THREAD_REPORTED.getAndSet(true)) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").info("Soundstage: audio packets are handled on {}{}",
                    Thread.currentThread().getName(),
                    Minecraft.getInstance().isSameThread() ? " (the main thread)" : " (not the main thread)");
        }
        ClientAudioReceiver receiver = receivers.computeIfAbsent(packet.receiverPos, p -> {
            return new ClientAudioReceiver(p, new OpusCodec.Decoder());
        });

        synchronized (receiver) {
            if (receiver.destroyed) {
                return;
            }
            if (listener == null) {
                receivers.remove(receiver.receiverPos, receiver);
                receiver.destroy();
                return;
            }

            for (int i = 0; i < packet.speakers.size(); i++) {
                com.nachtraben.soundstage.core.SpeakerAcoustics a =
                        i < packet.acoustics.size() ? packet.acoustics.get(i) : null;
                BlockPos speaker = packet.speakers.get(i);
                if (a != null) receiver.declaredAcoustics.put(speaker, a);
                int volume = i < packet.volumes.size() ? packet.volumes.get(i) : 100;
                receiver.declaredVolumes.put(speaker, Math.max(0, Math.min(100, volume)));
            }

            long nowNanos = System.nanoTime();
            if (packet.sequenceNumber == 0 || packet.resumedAfterGap) {
                receiver.arrivalLateness.rebase();
            }
            receiver.arrivalLateness.observe(packet.sequenceNumber, nowNanos);
            int recommendedLead = receiver.arrivalLateness.leadFrames(
                    nowNanos, com.nachtraben.soundstage.core.StreamSyncState.PREROLL_FRAMES);
            if (com.nachtraben.soundstage.RigStaging.fault("lead-fixed")) {
                recommendedLead = com.nachtraben.soundstage.core.StreamSyncState.PREROLL_FRAMES;
            }
            receiver.latenessFrames = (int) (receiver.arrivalLateness.peakLatenessNanos(nowNanos) / 20_000_000L);

            if (packet.sequenceNumber == 0) {

                RENUMBERS.incrementAndGet();
                receiver.ring.clear();
                receiver.timeline().clear();
                receiver.resumeFloorSequence = -1;

                for (StreamingSoundInstance held : receiver.streams.values()) {

                    held.setNeedsRestart(true);

                }

            }

            receiver.timeline().record(packet.sequenceNumber, packet.timecodeMillis);

            byte[] rawPcm = receiver.decoder.decode(packet.opusData);

            if (rawPcm != null) {
                receiver.addSequence(packet.sequenceNumber, rawPcm, receiver.decodeSurround(packet.surround));
                receiver.refreshSurroundLayout();

                receiver.traceOutput(packet.sequenceNumber, rawPcm, packet.speakers.size());

                java.util.List<BlockPos> playAt = packet.speakers;

                if (playAt.size() > 1) {
                    List<SpeakerId> ids = new ArrayList<>(playAt.size());
                    for (BlockPos sp : playAt) ids.add(new SpeakerId(sp.getX(), sp.getY(), sp.getZ()));
                    List<SpeakerId> ordered = SpeakerPriority.nearestFirst(
                            ids, here.x(), here.y(), here.z());
                    List<BlockPos> sorted = new ArrayList<>(ordered.size());
                    for (SpeakerId id : ordered) sorted.add(new BlockPos(id.x(), id.y(), id.z()));
                    playAt = sorted;
                }

                List<SpeakerId> ordered = new ArrayList<>(playAt.size());
                for (BlockPos sp : playAt) ordered.add(new SpeakerId(sp.getX(), sp.getY(), sp.getZ()));

                long previousPacketNanos = receiver.lastPacketNanos;
                long sinceLast = previousPacketNanos == 0L ? 0L : nowNanos - previousPacketNanos;
                receiver.lastPacketNanos = nowNanos;
                long starvedNanos = receiver.leadFrames * 20_000_000L;
                long starvedMillis = receiver.edgeStarvedMillis(nowNanos);
                boolean leadTooShort = starvedMillis >= MIN_STARVED_MILLIS
                        && recommendedLead >= receiver.leadFrames + MIN_LEAD_GROWTH_FRAMES;
                boolean nearEndOfStream = starvedMillis >= REBUILD_STARVED_MILLIS;
                if (packet.resumedAfterGap || sinceLast > starvedNanos || leadTooShort || nearEndOfStream) {
                    if (!receiver.streams.isEmpty()) {
                        long played = -1;
                        for (StreamingSoundInstance held : receiver.streams.values()) {
                            held.setNeedsRestart(true);
                            if (held.getJitterStream() != null) {
                                played = Math.max(played, held.getJitterStream().getNextSequenceToRead());
                            }
                        }
                        if (packet.sequenceNumber != 0 && played > receiver.resumeFloorSequence
                                && played <= receiver.ring.highestSequence() + receiver.leadFrames
                                && !com.nachtraben.soundstage.RigStaging.fault("resume-floor-ignored")) {
                            receiver.resumeFloorSequence = played;
                        }
                        lastGroupRestart = new GroupRestart(sinceLast / 1_000_000L, starvedMillis,
                                receiver.leadFrames, recommendedLead, packet.resumedAfterGap);
                        GROUP_RESTARTS.incrementAndGet();
                    }
                }

                boolean wholeGroupStarting = receiver.streams.isEmpty()
                        || receiver.streams.values().stream().allMatch(StreamingSoundInstance::isNeedsRestart);
                if (wholeGroupStarting) {
                    receiver.leadFrames = recommendedLead;
                    long lowest = receiver.ring.lowestSequence();
                    long floor = receiver.resumeFloorSequence;
                    long ready = floor >= lowest
                            ? receiver.ring.highestSequence() - floor + 1
                            : receiver.ring.size();
                    if (ready < receiver.leadFrames) {
                        if (!receiver.streams.isEmpty() && floor != -1) {
                            MinecraftSpeakerHost stopping = new MinecraftSpeakerHost(
                                    receiver, packet.receiverPos, -1L, false);
                            for (BlockPos held : new ArrayList<>(receiver.streams.keySet())) {
                                stopping.release(new SpeakerId(held.getX(), held.getY(), held.getZ()));
                            }
                        }
                        return;
                    }
                }

                java.util.Set<BlockPos> targetSet = new java.util.HashSet<>(playAt);
                boolean deviceClockJoin = JitterAudioStream.canAlignJoin(receiver);
                if (!receiver.streams.isEmpty()) {
                    boolean joining = false;
                    for (BlockPos target : targetSet) {
                        if (!receiver.lastDispatchTargets.contains(target)) {
                            joining = true;
                            break;
                        }
                    }
                    if (joining && !deviceClockJoin) {
                        for (StreamingSoundInstance held : receiver.streams.values()) {
                            held.setNeedsRestart(true);
                        }
                        GROUP_RESTARTS.incrementAndGet();
                    }
                }
                receiver.lastDispatchTargets = targetSet;
                boolean restartingWholeGroup = !receiver.streams.isEmpty()
                        && receiver.streams.values().stream().allMatch(
                                StreamingSoundInstance::isNeedsRestart);
                long dispatchAnchor = receiver.getGroupStartSequence();
                MinecraftSpeakerHost host = new MinecraftSpeakerHost(receiver, packet.receiverPos, dispatchAnchor,
                        deviceClockJoin && !restartingWholeGroup);
                SpeakerDispatch.reconcile(host, ordered, rawPcm, packet.sequenceNumber);
                host.groupStart().seal();
            }
        }
    }

    private record MinecraftSpeakerHost(ClientAudioReceiver receiver, BlockPos receiverPos,
                                        long dispatchAnchor, boolean alignIndividualStarts,
                                        GroupStartClock groupStart)
            implements SpeakerHost {

        MinecraftSpeakerHost(ClientAudioReceiver receiver, BlockPos receiverPos, long dispatchAnchor,
                             boolean alignIndividualStarts) {
            this(receiver, receiverPos, dispatchAnchor, alignIndividualStarts, new GroupStartClock());
        }

        private static BlockPos toPos(SpeakerId id) {
            return new BlockPos(id.x(), id.y(), id.z());
        }

        @Override
        public java.util.Set<SpeakerId> heldSpeakers() {
            java.util.Set<SpeakerId> out = new java.util.LinkedHashSet<>();
            for (BlockPos pos : receiver.streams.keySet()) {
                out.add(new SpeakerId(pos.getX(), pos.getY(), pos.getZ()));
            }
            return out;
        }

        @Override
        public boolean needsRestart(SpeakerId speaker) {
            StreamingSoundInstance stream = receiver.streams.get(toPos(speaker));
            return stream != null && stream.isNeedsRestart();
        }

        @Override
        public boolean hasLostItsChannel(SpeakerId speaker) {
            StreamingSoundInstance stream = receiver.streams.get(toPos(speaker));
            if (stream == null) return false;
            return !stream.isPendingStart() && !stream.isChannelOpen();
        }

        @Override
        public boolean hasChannel(SpeakerId speaker) {
            StreamingSoundInstance stream = receiver.streams.get(toPos(speaker));
            return stream != null && stream.getJitterStream() != null;
        }

        @Override
        public void release(SpeakerId speaker) {
            StreamingSoundInstance stream = receiver.streams.remove(toPos(speaker));
            if (stream == null) return;
            receiver.forgetDeviceClock(stream);
            stream.clearExcursion();
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop(stream));
        }

        @Override
        public void begin(SpeakerId speaker) {
            BlockPos pos = toPos(speaker);

            SpeakerAcoustics initialAcoustics = receiver.declaredAcoustics.get(pos);
            if (initialAcoustics == null) {
                return;
            }

            StreamingSoundInstance stream =
                    StreamingSoundInstance.create(pos, receiver, initialAcoustics);
            stream.setAlignToPlayingGroup(alignIndividualStarts && !receiver.streams.isEmpty());
            if (!stream.isAlignToPlayingGroup()) {
                stream.setGroupStart(groupStart);
                groupStart.expectOne();
            }
            stream.setStartAnchorSequence(dispatchAnchor);
            receiver.streams.put(pos, stream);
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().play(stream));
        }

        @Override
        public void traceWithoutChannel(SpeakerId speaker, byte[] stereoPcm, long sequence) {
            StreamingSoundInstance stream = receiver.streams.get(toPos(speaker));
            if (stream != null) {
                stream.traceVirtualFrame(() -> traceVirtualOutput(receiver, stream, stereoPcm, sequence));
            }
        }

        @Override
        public void keepAlive() {
            for (StreamingSoundInstance s : receiver.streams.values()) {
                s.notifyPacketReceived();
            }
        }
    }

    private static void traceVirtualOutput(ClientAudioReceiver receiver, StreamingSoundInstance stream,
                                           byte[] rawPcm, long sequenceNumber) {
        ByteBuffer monoBuffer = SpeakerOutput.render(rawPcm, receiver.ring.surround(sequenceNumber),
                receiver.surroundLayout, stream.getChannelMode(), stream.getDsp(), stream.getSpeakerGain());
        ShortBuffer monoShorts = monoBuffer.asShortBuffer();

        stream.traceFrame(sequenceNumber, monoShorts, SAMPLE_RATE);
    }
}
