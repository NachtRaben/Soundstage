package com.nachtraben.soundstage.util;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

public final class ServerDirectory {
    private ServerDirectory() {
    }

    public static Path of(MinecraftServer server) {
        //? if >=1.21 {
        return server.getServerDirectory();
        //?} else {
        /*return server.getServerDirectory().toPath();
        *///?}
    }
}
