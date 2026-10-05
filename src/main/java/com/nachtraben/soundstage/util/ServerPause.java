package com.nachtraben.soundstage.util;

import net.minecraft.server.MinecraftServer;

public final class ServerPause {
    public static volatile boolean clientPaused;

    private ServerPause() {
    }

    public static boolean isPaused(MinecraftServer server) {
        //? if >=1.20.5 {
        return server.isPaused();
        //?} else {
        /*return !server.isDedicatedServer() && clientPaused;
        *///?}
    }
}
