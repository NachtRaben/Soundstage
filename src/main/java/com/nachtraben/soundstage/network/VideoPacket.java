package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class VideoPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "video");

    public static final Type<VideoPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, VideoPacket> STREAM_CODEC =
            CustomPacketPayload.codec(VideoPacket::write, VideoPacket::new);

    public static final int MAX_DATA = 1_000_000;

    private static final byte[] EMPTY = new byte[0];

    public enum Kind { START, FRAME, CLOCK, STOP }

    public final BlockPos receiverPos;
    public final Kind kind;
    public final int generation;
    public final long millis;
    public final boolean flag;
    public final int width;
    public final int height;
    public final byte[] data;

    private VideoPacket(BlockPos receiverPos, Kind kind, int generation, long millis, boolean flag,
                        int width, int height, byte[] data) {
        this.receiverPos = receiverPos;
        this.kind = kind;
        this.generation = generation;
        this.millis = millis;
        this.flag = flag;
        this.width = width;
        this.height = height;
        this.data = data != null ? data : EMPTY;
    }

    public static VideoPacket start(BlockPos pos, int generation, int width, int height) {
        return new VideoPacket(pos, Kind.START, generation, 0L, false, width, height, EMPTY);
    }

    public static VideoPacket frame(BlockPos pos, int generation, long ptsMillis, boolean keyframe, byte[] data) {
        return new VideoPacket(pos, Kind.FRAME, generation, ptsMillis, keyframe, 0, 0, data);
    }

    public static VideoPacket clock(BlockPos pos, int generation, long millis, boolean paused) {
        return new VideoPacket(pos, Kind.CLOCK, generation, millis, paused, 0, 0, EMPTY);
    }

    public static VideoPacket stop(BlockPos pos, int generation) {
        return new VideoPacket(pos, Kind.STOP, generation, 0L, false, 0, 0, EMPTY);
    }

    public VideoPacket(FriendlyByteBuf buf) {
        this.receiverPos = buf.readBlockPos();
        int ordinal = buf.readByte();
        Kind[] kinds = Kind.values();
        if (ordinal < 0 || ordinal >= kinds.length) {
            throw new DecoderException("Unknown VideoPacket.Kind ordinal " + ordinal);
        }
        this.kind = kinds[ordinal];
        this.generation = buf.readInt();
        this.millis = buf.readLong();
        this.flag = buf.readBoolean();
        this.width = buf.readInt();
        this.height = buf.readInt();
        this.data = buf.readByteArray(MAX_DATA);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeByte(kind.ordinal());
        buf.writeInt(generation);
        buf.writeLong(millis);
        buf.writeBoolean(flag);
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeByteArray(data);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
