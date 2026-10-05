package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MediaBrowseReplyS2CPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "media_browse_reply_s2c");
    public static final Type<MediaBrowseReplyS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, MediaBrowseReplyS2CPacket> STREAM_CODEC = StreamCodec.ofMember(
            MediaBrowseReplyS2CPacket::write, MediaBrowseReplyS2CPacket::new
    );

    public final BlockPos pos;
    public final List<String> names;
    public final boolean truncated;

    public MediaBrowseReplyS2CPacket(BlockPos pos, List<String> names, boolean truncated) {
        this.pos = pos;
        this.names = names == null ? List.of() : names;
        this.truncated = truncated;
    }

    public MediaBrowseReplyS2CPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<String> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            read.add(buf.readUtf(com.nachtraben.soundstage.core.video.MediaNames.MAX_LENGTH));
        }
        this.names = read;
        this.truncated = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(names.size());
        for (String name : names) {
            buf.writeUtf(name, com.nachtraben.soundstage.core.video.MediaNames.MAX_LENGTH);
        }
        buf.writeBoolean(truncated);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
