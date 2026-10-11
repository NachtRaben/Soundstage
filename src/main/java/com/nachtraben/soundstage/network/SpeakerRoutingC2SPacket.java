package com.nachtraben.soundstage.network;

import lombok.RequiredArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.nachtraben.soundstage.SoundstageInit;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class SpeakerRoutingC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_routing_c2s");
    public static final Type<SpeakerRoutingC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerRoutingC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
        SpeakerRoutingC2SPacket::write, SpeakerRoutingC2SPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public final BlockPos receiverPos;
    public final BlockPos speakerPos;
    public final String channelMode;

    public SpeakerRoutingC2SPacket(FriendlyByteBuf buf) {
        this.receiverPos = buf.readBlockPos();
        this.speakerPos = buf.readBlockPos();
        this.channelMode = buf.readUtf();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeBlockPos(speakerPos);
        buf.writeUtf(channelMode);
    }
}
