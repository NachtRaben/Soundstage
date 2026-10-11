package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SpeakerAimC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_aim_c2s");
    public static final Type<SpeakerAimC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerAimC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            SpeakerAimC2SPacket::write, SpeakerAimC2SPacket::new
    );

    public final BlockPos pos;
    public final float pitch;
    public final float yaw;

    public SpeakerAimC2SPacket(BlockPos pos, float pitch, float yaw) {
        this.pos = pos;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    public SpeakerAimC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.pitch = buf.readFloat();
        this.yaw = buf.readFloat();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(pitch);
        buf.writeFloat(yaw);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
