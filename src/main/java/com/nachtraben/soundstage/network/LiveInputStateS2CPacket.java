package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class LiveInputStateS2CPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "live_input_state_s2c");
    public static final Type<LiveInputStateS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, LiveInputStateS2CPacket> STREAM_CODEC = StreamCodec.ofMember(
            LiveInputStateS2CPacket::write, LiveInputStateS2CPacket::new);

    public static final long NO_SHARE = -1;

    public final BlockPos pos;
    public final long shareId;
    public final boolean sharing;

    private LiveInputStateS2CPacket(BlockPos pos, long shareId, boolean sharing) {
        this.pos = pos;
        this.shareId = shareId;
        this.sharing = sharing;
    }

    public static LiveInputStateS2CPacket started(BlockPos pos, long shareId) {
        return new LiveInputStateS2CPacket(pos, shareId, true);
    }

    public static LiveInputStateS2CPacket ended(BlockPos pos, long shareId) {
        return new LiveInputStateS2CPacket(pos, shareId, false);
    }

    public static LiveInputStateS2CPacket refused(BlockPos pos) {
        return new LiveInputStateS2CPacket(pos, NO_SHARE, false);
    }

    private LiveInputStateS2CPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.shareId = buf.readLong();
        this.sharing = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeLong(shareId);
        buf.writeBoolean(sharing);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
