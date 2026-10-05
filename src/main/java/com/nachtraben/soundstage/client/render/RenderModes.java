package com.nachtraben.soundstage.client.render;

import net.minecraft.client.Minecraft;

public final class RenderModes {
    private RenderModes() {
    }

    public static boolean improvedTransparency() {
        //? if >=1.21.11 {
        /*return Minecraft.getInstance().options.improvedTransparency().get();
        *///?} else {
        return Minecraft.getInstance().options.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FABULOUS;
        //?}
    }
}
