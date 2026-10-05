package com.nachtraben.soundstage.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class WorldAccess {

    private WorldAccess() {}

    @Nullable
    public static BlockEntity blockEntityIfLoaded(Level level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
    }
}
