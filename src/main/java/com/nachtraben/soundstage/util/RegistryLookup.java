package com.nachtraben.soundstage.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class RegistryLookup {
    private RegistryLookup() {
    }

    public static Block block(String id) {
        //? if >=1.21.11 {
        /*return BuiltInRegistries.BLOCK.getValue(ResourceLocation.tryParse(id));
        *///?} else {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(id));
        //?}
    }

    public static Item item(String id) {
        //? if >=1.21.11 {
        /*return BuiltInRegistries.ITEM.getValue(ResourceLocation.tryParse(id));
        *///?} else {
        return BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        //?}
    }
}
