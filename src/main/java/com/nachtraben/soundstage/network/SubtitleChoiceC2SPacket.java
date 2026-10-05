package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record SubtitleChoiceC2SPacket(BlockPos pos, String choice) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "subtitle_choice_c2s");
    public static final Type<SubtitleChoiceC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SubtitleChoiceC2SPacket> STREAM_CODEC =
            StreamCodec.ofMember(SubtitleChoiceC2SPacket::write, SubtitleChoiceC2SPacket::read);

    private static SubtitleChoiceC2SPacket read(FriendlyByteBuf buf) {
        return new SubtitleChoiceC2SPacket(buf.readBlockPos(), buf.readUtf(SubtitleOptionsS2CPacket.MAX_KEY));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(choice, SubtitleOptionsS2CPacket.MAX_KEY);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
