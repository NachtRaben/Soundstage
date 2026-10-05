package com.nachtraben.soundstage;

public interface ModPlatform {
    String getModloader();
    boolean isModLoaded(String modloader);
}
