package com.nachtraben.soundstage.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class Registered {
    private Registered() {
    }

    public static Block block(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalArgumentException("no block " + id);
        //? if >=1.21.11 {
        /*return BuiltInRegistries.BLOCK.getValue(key);
        *///?} else {
        return BuiltInRegistries.BLOCK.get(key);
        //?}
    }
}
