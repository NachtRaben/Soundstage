package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SubtitleOptionsS2CPacket(BlockPos pos, List<SubtitleChoice.Option> options, String choice,
                                       @Nullable String autoKey) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "subtitle_options");
    public static final Type<SubtitleOptionsS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SubtitleOptionsS2CPacket> STREAM_CODEC =
            StreamCodec.ofMember(SubtitleOptionsS2CPacket::write, SubtitleOptionsS2CPacket::read);

    public static final int MAX_OPTIONS = 64;
    static final int MAX_KEY = 64;
    static final int MAX_LABEL = 120;

    public SubtitleOptionsS2CPacket {
        pos = pos.immutable();
        options = List.copyOf(options.size() > MAX_OPTIONS ? options.subList(0, MAX_OPTIONS) : options);
    }

    public static SubtitleOptionsS2CPacket clear(BlockPos pos) {
        return new SubtitleOptionsS2CPacket(pos, List.of(), SubtitleChoice.AUTO, null);
    }

    private static SubtitleOptionsS2CPacket read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = Math.min(buf.readVarInt(), MAX_OPTIONS);
        List<SubtitleChoice.Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(new SubtitleChoice.Option(buf.readUtf(MAX_KEY), buf.readUtf(MAX_LABEL)));
        }
        String choice = buf.readUtf(MAX_KEY);
        String autoKey = buf.readBoolean() ? buf.readUtf(MAX_KEY) : null;
        return new SubtitleOptionsS2CPacket(pos, options, choice, autoKey);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(options.size());
        for (SubtitleChoice.Option option : options) {
            buf.writeUtf(clip(option.key(), MAX_KEY), MAX_KEY);
            buf.writeUtf(clip(option.label(), MAX_LABEL), MAX_LABEL);
        }
        buf.writeUtf(clip(choice, MAX_KEY), MAX_KEY);
        buf.writeBoolean(autoKey != null);
        if (autoKey != null) buf.writeUtf(clip(autoKey, MAX_KEY), MAX_KEY);
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
