package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SpeakerGrilleC2SPacket implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_grille_c2s");
    public static final Type<SpeakerGrilleC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerGrilleC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            SpeakerGrilleC2SPacket::write, SpeakerGrilleC2SPacket::new
    );

    public final BlockPos pos;
    public final boolean grille;

    public SpeakerGrilleC2SPacket(BlockPos pos, boolean grille) {
        this.pos = pos;
        this.grille = grille;
    }

    public SpeakerGrilleC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.grille = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(grille);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
