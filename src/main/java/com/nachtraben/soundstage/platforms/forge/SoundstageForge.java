package com.nachtraben.soundstage.platforms.forge;

//? if forge {
/*import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SoundstageInit.MODID)
public class SoundstageForge {
    public SoundstageForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        com.nachtraben.soundstage.config.SoundstageConfig.install(new com.nachtraben.soundstage.config.SoundstageConfigSpec());
        SoundstageInit.entrypoint(new com.nachtraben.soundstage.ModPlatform() {
            @Override
            public String getModloader() {
                return "Forge";
            }
            @Override
            public boolean isModLoaded(String modid) {
                return net.minecraftforge.fml.ModList.get().isLoaded(modid);
            }
        });
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.nachtraben.soundstage.config.SoundstageAppearance.install(new com.nachtraben.soundstage.config.AppearanceConfigSpec());
            SoundstageClientInit.clientEntrypoint();
            modEventBus.addListener(this::clientSetup);
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        SoundstageClientInit.clientSetup();
    }
}
*///?}
