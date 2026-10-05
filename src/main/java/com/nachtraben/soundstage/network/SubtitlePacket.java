package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.core.video.SubtitleCue;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SubtitlePacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "subtitle");

    public static final Type<SubtitlePacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SubtitlePacket> STREAM_CODEC =
            StreamCodec.ofMember(SubtitlePacket::write, SubtitlePacket::new);

    public static final int MAX_CUES_PER_PACKET = 64;
    public static final int MAX_CUE_TEXT_LENGTH = 500;

    public final BlockPos receiverPos;
    public final int generation;
    public final boolean reset;
    public final List<SubtitleCue> cues;

    public SubtitlePacket(BlockPos receiverPos, int generation, boolean reset, List<SubtitleCue> cues) {
        this.receiverPos = receiverPos;
        this.generation = generation;
        this.reset = reset;
        this.cues = List.copyOf(cues.size() > MAX_CUES_PER_PACKET ? cues.subList(0, MAX_CUES_PER_PACKET) : cues);
    }

    public static SubtitlePacket reset(BlockPos pos, int generation) {
        return new SubtitlePacket(pos, generation, true, List.of());
    }

    public static SubtitlePacket cues(BlockPos pos, int generation, List<SubtitleCue> cues) {
        return new SubtitlePacket(pos, generation, false, cues);
    }

    public SubtitlePacket(FriendlyByteBuf buf) {
        this.receiverPos = buf.readBlockPos();
        this.generation = buf.readInt();
        this.reset = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CUES_PER_PACKET) {
            throw new DecoderException("SubtitlePacket carries " + count + " cues, more than "
                    + MAX_CUES_PER_PACKET);
        }
        List<SubtitleCue> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long start = buf.readVarLong();
            long end = buf.readVarLong();
            String text = buf.readUtf(MAX_CUE_TEXT_LENGTH);
            int revealCount = buf.readVarInt();
            if (revealCount < 0 || revealCount > SubtitleCue.MAX_REVEALS) {
                throw new DecoderException("SubtitlePacket cue carries " + revealCount + " reveals, more than "
                        + SubtitleCue.MAX_REVEALS);
            }
            List<SubtitleCue.Reveal> reveals = new ArrayList<>(revealCount);
            for (int r = 0; r < revealCount; r++) {
                long after = buf.readVarLong();
                int length = buf.readVarInt();
                if (after < 0 || length < 0 || length > text.length()) {
                    throw new DecoderException("SubtitlePacket reveal outside its cue: +" + after + " ms, "
                            + length + " of " + text.length() + " characters");
                }
                reveals.add(new SubtitleCue.Reveal(start + after, length));
            }
            read.add(new SubtitleCue(start, end, text, reveals));
        }
        this.cues = List.copyOf(read);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(receiverPos);
        buf.writeInt(generation);
        buf.writeBoolean(reset);
        buf.writeVarInt(cues.size());
        for (SubtitleCue cue : cues) {
            buf.writeVarLong(cue.startMs());
            buf.writeVarLong(cue.endMs());
            buf.writeUtf(cue.text(), MAX_CUE_TEXT_LENGTH);
            buf.writeVarInt(cue.reveals().size());
            for (SubtitleCue.Reveal reveal : cue.reveals()) {
                buf.writeVarLong(Math.max(0, reveal.atMillis() - cue.startMs()));
                buf.writeVarInt(Math.min(reveal.length(), cue.text().length()));
            }
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
