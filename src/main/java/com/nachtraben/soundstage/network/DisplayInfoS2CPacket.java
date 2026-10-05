package com.nachtraben.soundstage.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisplayInfoS2CPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("soundstage", "display_info_s2c");
    public static final Type<DisplayInfoS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, DisplayInfoS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(DisplayInfoS2CPacket::write, DisplayInfoS2CPacket::new);

    private static final int MAX_TEXT = 256;

    public final BlockPos receiverPos;
    public final int generation;
    public final String title;
    public final String author;
    public final @Nullable String artworkUrl;
    public final long durationMillis;
    public final boolean stream;
    public final boolean pictureExpected;
    public final boolean clear;

    public DisplayInfoS2CPacket(BlockPos receiverPos, int generation, String title, String author,
                                 @Nullable String artworkUrl, long durationMillis, boolean stream,
                                 boolean pictureExpected, boolean clear) {
        this.receiverPos = receiverPos;
        this.generation = generation;
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.artworkUrl = artworkUrl;
        this.durationMillis = durationMillis;
        this.stream = stream;
        this.pictureExpected = pictureExpected;
        this.clear = clear;
    }

    public static DisplayInfoS2CPacket clear(BlockPos receiverPos, int generation) {
        return new DisplayInfoS2CPacket(receiverPos, generation, "", "", null, 0L, false, false, true);
    }

    public DisplayInfoS2CPacket(FriendlyByteBuf buf) {
        this.receiverPos = buf.readBlockPos();
        this.generation = buf.readVarInt();
        this.title = buf.readUtf(MAX_TEXT);
        this.author = buf.readUtf(MAX_TEXT);
        String artwork = buf.readUtf(MAX_TEXT);
        this.artworkUrl = artwork.isEmpty() ? null : artwork;
        this.durationMillis = buf.readLong();
        this.stream = buf.readBoolean();
        this.pictureExpected = buf.readBoolean();
        this.clear = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeVarInt(generation);
        buf.writeUtf(truncate(title), MAX_TEXT);
        buf.writeUtf(truncate(author), MAX_TEXT);
        buf.writeUtf(truncate(artworkUrl == null ? "" : artworkUrl), MAX_TEXT);
        buf.writeLong(durationMillis);
        buf.writeBoolean(stream);
        buf.writeBoolean(pictureExpected);
        buf.writeBoolean(clear);
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
