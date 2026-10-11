package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.core.SpeakerAcoustics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AudioPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "audio_packet");

    public static final Type<AudioPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, AudioPacket> STREAM_CODEC = StreamCodec.ofMember(
        AudioPacket::write, AudioPacket::new
    );

    public record SurroundBlock(byte[] flFr, byte[] clfe, byte[] slSr) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public final BlockPos receiverPos;
    public final List<BlockPos> speakers;

    public final List<SpeakerAcoustics> acoustics;

    public final List<Integer> volumes;
    public final long sequenceNumber;
    public final byte[] opusData;

    public final SurroundBlock surround;

    public final boolean resumedAfterGap;

    public static final long RESUME_GAP_NANOS = 200_000_000L;

    public final long timecodeMillis;

    public AudioPacket(BlockPos receiverPos, List<BlockPos> speakers, long sequenceNumber, byte[] opusData) {
        this(receiverPos, speakers, null, null, sequenceNumber, opusData, false, -1L, null);
    }

    public AudioPacket(BlockPos receiverPos, List<BlockPos> speakers,
                       List<SpeakerAcoustics> acoustics,
                       long sequenceNumber, byte[] opusData) {
        this(receiverPos, speakers, acoustics, null, sequenceNumber, opusData, false, -1L, null);
    }

    public AudioPacket(BlockPos receiverPos, List<BlockPos> speakers,
                       List<SpeakerAcoustics> acoustics,
                       List<Integer> volumes,
                       long sequenceNumber, byte[] opusData) {
        this(receiverPos, speakers, acoustics, volumes, sequenceNumber, opusData, false, -1L, null);
    }

    public AudioPacket(BlockPos receiverPos, List<BlockPos> speakers,
                       List<SpeakerAcoustics> acoustics,
                       List<Integer> volumes,
                       long sequenceNumber, byte[] opusData, boolean resumedAfterGap) {
        this(receiverPos, speakers, acoustics, volumes, sequenceNumber, opusData, resumedAfterGap, -1L, null);
    }

    public AudioPacket(BlockPos receiverPos, List<BlockPos> speakers,
                       List<SpeakerAcoustics> acoustics,
                       List<Integer> volumes,
                       long sequenceNumber, byte[] opusData, boolean resumedAfterGap, long timecodeMillis) {
        this(receiverPos, speakers, acoustics, volumes, sequenceNumber, opusData, resumedAfterGap,
                timecodeMillis, null);
    }

    public AudioPacket(BlockPos receiverPos, List<BlockPos> speakers,
                       List<SpeakerAcoustics> acoustics,
                       List<Integer> volumes,
                       long sequenceNumber, byte[] opusData, boolean resumedAfterGap, long timecodeMillis,
                       SurroundBlock surround) {
        this.receiverPos = receiverPos;
        this.speakers = speakers != null ? new ArrayList<>(speakers) : new ArrayList<>();
        this.acoustics = acoustics != null ? new ArrayList<>(acoustics) : new ArrayList<>();
        this.volumes = volumes != null ? new ArrayList<>(volumes) : new ArrayList<>();
        this.sequenceNumber = sequenceNumber;
        this.opusData = opusData;
        this.resumedAfterGap = resumedAfterGap;
        this.timecodeMillis = timecodeMillis;
        this.surround = surround;
    }

    public AudioPacket(FriendlyByteBuf buf) {
        this.receiverPos = buf.readBlockPos();
        int count = buf.readInt();
        this.speakers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.speakers.add(buf.readBlockPos());
        }
        this.acoustics = new ArrayList<>(count);
        SpeakerAcoustics[] all =
                SpeakerAcoustics.values();
        for (int i = 0; i < count; i++) {
            int ordinal = buf.readByte();
            this.acoustics.add(ordinal >= 0 && ordinal < all.length ? all[ordinal] : null);
        }
        this.volumes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.volumes.add(Math.min(100, buf.readUnsignedByte()));
        }
        this.sequenceNumber = buf.readLong();
        this.opusData = buf.readByteArray();
        this.resumedAfterGap = buf.readBoolean();
        this.timecodeMillis = buf.readLong();
        if (buf.readableBytes() > 0 && buf.readBoolean()) {
            byte[] flFr = buf.readByteArray();
            byte[] clfe = buf.readByteArray();
            byte[] slSr = buf.readByteArray();
            this.surround = new SurroundBlock(flFr, clfe, slSr);
        } else {
            this.surround = null;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeInt(speakers.size());
        for (BlockPos sp : speakers) {
            buf.writeBlockPos(sp);
        }
        for (int i = 0; i < speakers.size(); i++) {
            SpeakerAcoustics a =
                    i < acoustics.size() ? acoustics.get(i) : null;
            buf.writeByte(a == null ? -1 : a.ordinal());
        }
        for (int i = 0; i < speakers.size(); i++) {
            int volume = i < volumes.size() ? volumes.get(i) : 100;
            buf.writeByte(Math.max(0, Math.min(100, volume)));
        }
        buf.writeLong(sequenceNumber);
        buf.writeByteArray(opusData);
        buf.writeBoolean(resumedAfterGap);
        buf.writeLong(timecodeMillis);
        if (surround != null) {
            buf.writeBoolean(true);
            buf.writeByteArray(surround.flFr());
            buf.writeByteArray(surround.clfe());
            buf.writeByteArray(surround.slSr());
        } else {
            buf.writeBoolean(false);
        }
    }
}
