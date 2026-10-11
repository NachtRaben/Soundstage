package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SpeakerDelayC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_delay_c2s");
    public static final Type<SpeakerDelayC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerDelayC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            SpeakerDelayC2SPacket::write, SpeakerDelayC2SPacket::new
    );

    public final BlockPos pos;
    public final int delayMs;

    public SpeakerDelayC2SPacket(BlockPos pos, int delayMs) {
        this.pos = pos;
        this.delayMs = delayMs;
    }

    public SpeakerDelayC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.delayMs = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(Math.max(0, delayMs));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
