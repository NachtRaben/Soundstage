package com.nachtraben.soundstage.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class PlayerMessage {

    private PlayerMessage() {}

    public static void actionBar(Player player, Component message) {
        if (player == null) return;
        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else {
        player.displayClientMessage(message, true);
        //?}
    }
}
