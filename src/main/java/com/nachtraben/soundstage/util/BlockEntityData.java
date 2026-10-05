package com.nachtraben.soundstage.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockEntityData {
    private BlockEntityData() {
    }

    public static CompoundTag save(ServerLevel level, BlockEntity blockEntity) {
        //? if >=1.21 {
        return blockEntity.saveWithFullMetadata(level.registryAccess());
        //?} else {
        /*return blockEntity.saveWithFullMetadata();
        *///?}
    }

    public static BlockEntity load(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        //? if >=1.21 {
        return BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        //?} else {
        /*return BlockEntity.loadStatic(pos, state, tag);
        *///?}
    }
}
