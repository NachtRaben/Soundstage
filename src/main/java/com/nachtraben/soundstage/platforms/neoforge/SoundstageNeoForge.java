package com.nachtraben.soundstage.platforms.neoforge;

//? if neoforge {
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

@Mod(SoundstageInit.MODID)
public class SoundstageNeoForge {
    public SoundstageNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        com.nachtraben.soundstage.config.SoundstageConfig.install(new com.nachtraben.soundstage.config.SoundstageConfigSpec());
        SoundstageInit.entrypoint(new com.nachtraben.soundstage.ModPlatform() {
            @Override
            public String getModloader() {
                return "NeoForge";
            }
            @Override
            public boolean isModLoaded(String modid) {
                return net.neoforged.fml.ModList.get().isLoaded(modid);
            }
        });
        //? if >=1.21.11 {
        /*if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
        *///?} else {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
        //?}
            com.nachtraben.soundstage.config.SoundstageAppearance.install(new com.nachtraben.soundstage.config.AppearanceConfigSpec());
            SoundstageClientInit.clientEntrypoint();
            modEventBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        SoundstageClientInit.clientSetup();
    }

}
//?}
