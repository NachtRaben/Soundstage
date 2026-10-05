package com.nachtraben.soundstage.client.render;

import net.minecraft.client.Minecraft;

public final class LevelRenderers {
    private LevelRenderers() {
    }

    public static void rebuildAll() {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        /*minecraft.levelRenderer.invalidateCompiledGeometry(minecraft.level, minecraft.options,
                minecraft.gameRenderer.mainCamera(), minecraft.getBlockColors());
        *///?} else {
        minecraft.levelRenderer.allChanged();
        //?}
    }
}
