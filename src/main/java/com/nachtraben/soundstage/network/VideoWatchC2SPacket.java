package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record VideoWatchC2SPacket(BlockPos receiverPos, boolean watching) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "video_watch_c2s");
    public static final Type<VideoWatchC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, VideoWatchC2SPacket> STREAM_CODEC =
            StreamCodec.ofMember(VideoWatchC2SPacket::write, VideoWatchC2SPacket::read);

    private static VideoWatchC2SPacket read(FriendlyByteBuf buf) {
        return new VideoWatchC2SPacket(buf.readBlockPos(), buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeBoolean(watching);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
