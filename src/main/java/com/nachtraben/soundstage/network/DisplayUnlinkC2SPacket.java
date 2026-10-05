package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DisplayUnlinkC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "display_unlink_c2s");
    public static final Type<DisplayUnlinkC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, DisplayUnlinkC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            DisplayUnlinkC2SPacket::write, DisplayUnlinkC2SPacket::new
    );

    public final BlockPos pos;

    public DisplayUnlinkC2SPacket(BlockPos pos) {
        this.pos = pos;
    }

    public DisplayUnlinkC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
