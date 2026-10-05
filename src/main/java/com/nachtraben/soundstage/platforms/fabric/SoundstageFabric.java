package com.nachtraben.soundstage.platforms.fabric;

//? if fabric {
/*import com.nachtraben.soundstage.ModPlatform;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.config.SoundstageConfigSpec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class SoundstageFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SoundstageConfig.install(new SoundstageConfigSpec());
        SoundstageInit.entrypoint(new ModPlatform() {
            @Override
            public String getModloader() {
                return "Fabric";
            }

            @Override
            public boolean isModLoaded(String modid) {
                return FabricLoader.getInstance().isModLoaded(modid);
            }
        });
    }
}
*///?}
