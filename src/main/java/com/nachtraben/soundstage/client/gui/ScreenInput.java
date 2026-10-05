package com.nachtraben.soundstage.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenInput {
    private ScreenInput() {
    }

    public static boolean click(Screen screen, double x, double y) {
        //? if >=1.21.11 {
        /*return screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
        *///?} else {
        return screen.mouseClicked(x, y, InputConstants.MOUSE_BUTTON_LEFT);
        //?}
    }
}
