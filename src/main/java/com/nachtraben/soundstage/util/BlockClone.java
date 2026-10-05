package com.nachtraben.soundstage.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockClone {
    private BlockClone() {
    }

    public static ItemStack clone(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        //? if >=1.21.11 {
        /*return state.getCloneItemStack(level, pos, true);
        *///?} else if >=1.20.5 {
        return state.getBlock().getCloneItemStack(level, pos, state);
        //?} else {
        /*return state.getBlock().getCloneItemStack(level, pos, state);
        *///?}
    }
}
