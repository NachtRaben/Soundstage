package com.nachtraben.soundstage.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class Mobs {
    private Mobs() {
    }

    public static void spawnParrot(ServerLevel level, double x, double y, double z, boolean pinned) {
        //? if >=26.2 {
        /*spawn(level, net.minecraft.world.entity.EntityTypes.PARROT, x, y, z, pinned);
        *///?} else {
        spawn(level, EntityType.PARROT, x, y, z, pinned);
        //?}
    }

    public static void spawnAllay(ServerLevel level, double x, double y, double z) {
        //? if >=26.2 {
        /*spawn(level, net.minecraft.world.entity.EntityTypes.ALLAY, x, y, z, false);
        *///?} else {
        spawn(level, EntityType.ALLAY, x, y, z, false);
        //?}
    }

    private static <T extends Mob> void spawn(ServerLevel level, EntityType<T> type, double x, double y, double z,
                                              boolean pinned) {
        //? if >=1.21.11 {
        /*T mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        *///?} else {
        T mob = type.create(level);
        //?}
        if (mob == null) throw new IllegalStateException("could not make a " + type);
        //? if >=1.21.11 {
        /*mob.snapTo(x, y, z, 0.0f, 0.0f);
        *///?} else {
        mob.moveTo(x, y, z, 0.0f, 0.0f);
        //?}
        if (pinned) mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    public static int[] parrots(Level level, BlockPos pos, double radius) {
        int all = 0, dancing = 0;
        for (net.minecraft.world.entity.animal.Parrot parrot
                : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Parrot.class, new AABB(pos).inflate(radius))) {
            all++;
            if (parrot.isPartyParrot()) dancing++;
        }
        return new int[]{all, dancing};
    }

    public static int[] allays(Level level, BlockPos pos, double radius) {
        int all = 0, dancing = 0;
        for (net.minecraft.world.entity.animal.allay.Allay allay
                : level.getEntitiesOfClass(net.minecraft.world.entity.animal.allay.Allay.class, new AABB(pos).inflate(radius))) {
            all++;
            if (allay.isDancing()) dancing++;
        }
        return new int[]{all, dancing};
    }
}
