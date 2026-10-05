package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SpeakerRangeC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_range_c2s");
    public static final Type<SpeakerRangeC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerRangeC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            SpeakerRangeC2SPacket::write, SpeakerRangeC2SPacket::new
    );

    public final BlockPos pos;
    public final int range;

    public SpeakerRangeC2SPacket(BlockPos pos, int range) {
        this.pos = pos;
        this.range = range;
    }

    public SpeakerRangeC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.range = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(range);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
