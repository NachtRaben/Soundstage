package com.nachtraben.soundstage.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class Players {
    private Players() {
    }

    public static ServerLevel level(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }

    public static String language(ServerPlayer player) {
        //? if >=1.20.2 {
        return player.clientInformation().language();
        //?} else {
        /*return ((PlayerLanguage) player).soundstage$language();
        *///?}
    }

    public static String name(net.minecraft.world.entity.player.Player player) {
        //? if >=1.21.11 {
        /*return player.getGameProfile().name();
        *///?} else {
        return player.getGameProfile().getName();
        //?}
    }

    public static void selectHotbarSlot(net.minecraft.world.entity.player.Inventory inventory, int slot) {
        //? if >=1.21.11 {
        /*inventory.setSelectedSlot(slot);
        *///?} else {
        inventory.selected = slot;
        //?}
    }

    public static void teleportTo(net.minecraft.server.level.ServerPlayer player, net.minecraft.server.level.ServerLevel level,
                                  double x, double y, double z, float yaw, float pitch) {
        //? if >=1.21.11 {
        /*player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, true);
        *///?} else {
        player.teleportTo(level, x, y, z, yaw, pitch);
        //?}
    }
}
