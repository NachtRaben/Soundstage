package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ScreenFitModeC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "screen_fit_mode_c2s");
    public static final Type<ScreenFitModeC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, ScreenFitModeC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            ScreenFitModeC2SPacket::write, ScreenFitModeC2SPacket::new
    );

    public final BlockPos pos;
    public final int fitMode;

    public ScreenFitModeC2SPacket(BlockPos pos, int fitMode) {
        this.pos = pos;
        this.fitMode = fitMode;
    }

    public ScreenFitModeC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.fitMode = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(fitMode);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
