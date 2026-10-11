package com.nachtraben.soundstage.block.speaker;

import com.mojang.serialization.MapCodec;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class SatelliteSpeakerBlock extends AbstractSpeakerBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<SatelliteSpeakerBlock> CODEC = simpleCodec(SatelliteSpeakerBlock::new);
    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}
    public SatelliteSpeakerBlock(Properties properties) {
        super(properties);
    }

    protected static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
            @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.SATELLITE_SPEAKER_BLOCK_ENTITY.get().create(pos, state);
    }
}
