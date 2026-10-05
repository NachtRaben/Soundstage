package com.nachtraben.soundstage.block.speaker;

import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;

public class CenterSpeakerBlock extends AbstractSpeakerBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<CenterSpeakerBlock> CODEC = simpleCodec(CenterSpeakerBlock::new);
    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}
    public CenterSpeakerBlock(Properties properties) {
        super(properties);
    }

    protected static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(2, 0, 2, 14, 6, 14);

    @Override
    public net.minecraft.world.phys.shapes.@NotNull VoxelShape getShape(@NotNull BlockState state, net.minecraft.world.level.@NotNull BlockGetter level, @NotNull BlockPos pos, net.minecraft.world.phys.shapes.@NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.CENTER_SPEAKER_BLOCK_ENTITY.get().create(pos, state);
    }
}
