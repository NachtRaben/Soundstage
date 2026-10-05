package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.core.LiveInputBuffer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class LiveAudioC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "live_audio_c2s");
    public static final Type<LiveAudioC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, LiveAudioC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            LiveAudioC2SPacket::write, LiveAudioC2SPacket::new);

    public final byte[] opus;

    public LiveAudioC2SPacket(byte[] opus) {
        this.opus = opus;
    }

    private LiveAudioC2SPacket(FriendlyByteBuf buf) {
        this.opus = buf.readByteArray(LiveInputBuffer.MAX_PACKET_BYTES);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByteArray(opus);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
