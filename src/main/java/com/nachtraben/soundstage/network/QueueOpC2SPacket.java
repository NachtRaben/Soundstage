package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class QueueOpC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "queue_op_c2s");
    public static final Type<QueueOpC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, QueueOpC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            QueueOpC2SPacket::write, QueueOpC2SPacket::new
    );

    public enum Action {
        MOVE,
        REMOVE,
        CLEAR,
        PLAY_NOW
    }

    public final BlockPos pos;
    public final Action action;
    public final long entryId;
    public final int targetIndex;

    public QueueOpC2SPacket(BlockPos pos, Action action, long entryId, int targetIndex) {
        this.pos = pos;
        this.action = action;
        this.entryId = entryId;
        this.targetIndex = targetIndex;
    }

    public QueueOpC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        this.entryId = buf.readLong();
        this.targetIndex = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeLong(entryId);
        buf.writeVarInt(Math.max(0, targetIndex));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
