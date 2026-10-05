package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SpeakerVolumeC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SoundstageInit.MODID, "speaker_volume_c2s");
    public static final Type<SpeakerVolumeC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerVolumeC2SPacket> STREAM_CODEC =
            StreamCodec.ofMember(SpeakerVolumeC2SPacket::write, SpeakerVolumeC2SPacket::new);

    public final BlockPos pos;
    public final int volumePercent;

    public SpeakerVolumeC2SPacket(BlockPos pos, int volumePercent) {
        this.pos = pos;
        this.volumePercent = volumePercent;
    }

    public SpeakerVolumeC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.volumePercent = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(volumePercent);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
