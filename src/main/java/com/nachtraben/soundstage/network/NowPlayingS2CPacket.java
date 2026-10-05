package com.nachtraben.soundstage.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class NowPlayingS2CPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("soundstage", "now_playing");

    public static final CustomPacketPayload.Type<NowPlayingS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, NowPlayingS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(NowPlayingS2CPacket::toBytes, NowPlayingS2CPacket::new);

    private static final int MAX_TEXT = 256;

    public final net.minecraft.core.BlockPos receiverPos;
    public final String title;
    public final String artist;
    public final String artworkUrl;

    public NowPlayingS2CPacket(FriendlyByteBuf buf) {
        this.receiverPos = buf.readBlockPos();
        this.title = buf.readUtf(MAX_TEXT);
        this.artist = buf.readUtf(MAX_TEXT);
        this.artworkUrl = buf.readUtf(MAX_TEXT);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeUtf(truncate(title), MAX_TEXT);
        buf.writeUtf(truncate(artist), MAX_TEXT);
        buf.writeUtf(truncate(artworkUrl), MAX_TEXT);
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
