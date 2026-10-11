package com.nachtraben.soundstage.block.speaker;

import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SubwooferBlockEntity extends SpeakerBlockEntity {

    public SubwooferBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUBWOOFER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected SpeakerFinish defaultFinish() {
        return SpeakerFinish.DEFAULT.withGrille(Blocks.BLACK_WOOL);
    }
}
