package com.nachtraben.soundstage.platforms.neoforge;

//? if neoforge {
import com.nachtraben.soundstage.ModPlatform;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.config.AppearanceConfigSpec;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.config.SoundstageConfigSpec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(SoundstageInit.MODID)
public class SoundstageNeoForge {
    public SoundstageNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        SoundstageConfig.install(new SoundstageConfigSpec());
        SoundstageInit.entrypoint(new ModPlatform() {
            @Override
            public String getModloader() {
                return "NeoForge";
            }
            @Override
            public boolean isModLoaded(String modid) {
                return ModList.get().isLoaded(modid);
            }
        });
        //? if >=1.21.11 {
        /*if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
        *///?} else {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
        //?}
            SoundstageAppearance.install(new AppearanceConfigSpec());
            SoundstageClientInit.clientEntrypoint();
            modEventBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        SoundstageClientInit.clientSetup();
    }

}
//?}
