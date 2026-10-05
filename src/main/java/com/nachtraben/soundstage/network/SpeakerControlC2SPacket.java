package com.nachtraben.soundstage.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.nachtraben.soundstage.SoundstageInit;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

public class SpeakerControlC2SPacket implements CustomPacketPayload
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_control_c2s");
    public static final Type<SpeakerControlC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerControlC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
        SpeakerControlC2SPacket::write, SpeakerControlC2SPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        PLAY_PAUSE,
        SKIP,
        SET_VOLUME,
        QUEUE,
        REQUEST_SYNC,
        CLOSE_SYNC,
        STOP,
        TOGGLE_LOOP,
        TOGGLE_SHUFFLE,
        TOGGLE_LOCK,
        SEEK,
        SHARE_START,
        SHARE_STOP,
        TOGGLE_PICTURE,
        TOGGLE_LEVELLING
    }

    public final BlockPos pos;
    public final Action action;
    public final String stringData;
    public final int intData;
    public final long longData;

    public SpeakerControlC2SPacket(BlockPos pos, Action action) {
        this(pos, action, "", 0, 0L);
    }

    public SpeakerControlC2SPacket(BlockPos pos, Action action, String stringData, int intData) {
        this(pos, action, stringData, intData, 0L);
    }

    public SpeakerControlC2SPacket(BlockPos pos, Action action, String stringData, int intData, long longData) {
        this.pos = pos;
        this.action = action;
        this.stringData = stringData;
        this.intData = intData;
        this.longData = longData;
    }

    public SpeakerControlC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        this.stringData = buf.readUtf();
        this.intData = buf.readInt();
        this.longData = buf.readLong();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeUtf(stringData);
        buf.writeInt(intData);
        buf.writeLong(longData);
    }
}
