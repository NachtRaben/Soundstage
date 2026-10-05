package com.nachtraben.soundstage.block.speaker;

import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CenterSpeakerBlockEntity extends SpeakerBlockEntity {
    public CenterSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTER_SPEAKER_BLOCK_ENTITY.get(), pos, state);
    }
}
