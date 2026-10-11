package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record RemoteOpenS2CPacket(BlockPos receiverPos) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "remote_open_s2c");
    public static final Type<RemoteOpenS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, RemoteOpenS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(RemoteOpenS2CPacket::write, RemoteOpenS2CPacket::read);

    private static RemoteOpenS2CPacket read(FriendlyByteBuf buf) {
        return new RemoteOpenS2CPacket(buf.readBlockPos());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
