package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.util.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MusicSource {

    private MusicSource() {}

    public static final TagKey<Block> MUSIC_SOURCES =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(
                    com.nachtraben.soundstage.SoundstageInit.MODID, "music_source"));

    public static boolean isMusicSource(BlockState state) {
        return state.is(Blocks.JUKEBOX) || state.is(MUSIC_SOURCES) && !com.nachtraben.soundstage.RigStaging.fault("not-in-tag");
    }

    public static double danceRadius(Level level, BlockPos pos, double vanillaRadius) {
        if (WorldAccess.blockEntityIfLoaded(level, pos) instanceof SpeakerBlockEntity speaker) {
            return Math.max(vanillaRadius, speaker.getRange());
        }
        return vanillaRadius;
    }
}
