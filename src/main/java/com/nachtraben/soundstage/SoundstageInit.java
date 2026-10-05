package com.nachtraben.soundstage;

import com.nachtraben.soundstage.command.SoundstageCommand;
import com.nachtraben.soundstage.registry.ModBlocks;
import com.nachtraben.soundstage.registry.ModItems;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import com.nachtraben.soundstage.registry.ModTabs;
import dev.armadeus.armalib.platform.event.CommandEvents;
import dev.armadeus.armalib.platform.event.LifecycleEvents;
import dev.armadeus.armalib.platform.event.PlayerEvents;
import dev.armadeus.armalib.platform.registry.CreativeTabs;

public class SoundstageInit
{
	public static final String MODID = "soundstage";
	public static ModPlatform PLATFORM = null;

	public static void entrypoint(ModPlatform platform) {
		SoundstageInit.PLATFORM = platform;
        com.nachtraben.soundstage.registry.ModComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModTabs.init();
        com.nachtraben.soundstage.config.SoundstageConfig.load();
        com.nachtraben.soundstage.network.NetworkHandler.init();
        
        CommandEvents.REGISTER.register(SoundstageCommand::register);
        LifecycleEvents.SERVER_STARTING.register(server -> {
            java.nio.file.Path serverDirectory = com.nachtraben.soundstage.util.ServerDirectory.of(server);
            com.nachtraben.soundstage.audio.media.MediaFolder.serverStarting(serverDirectory,
                    com.nachtraben.soundstage.config.SoundstageConfig.enableMediaFolder());
            com.nachtraben.soundstage.audio.media.FfmpegDownload.serverStarting(serverDirectory);
        });
        LifecycleEvents.SERVER_STOPPED.register(server ->
                com.nachtraben.soundstage.audio.media.MediaFolder.serverStopped());
        LifecycleEvents.SERVER_STOPPING.register(server -> {
            if (com.nachtraben.soundstage.audio.AudioManagerState.created()) {
                com.nachtraben.soundstage.audio.AudioManager.get().shutdown();
            }
            com.nachtraben.soundstage.audio.MetadataViewers.forgetAll();
            com.nachtraben.soundstage.audio.SpeakerAudience.forgetAll();
            com.nachtraben.soundstage.audio.video.ScreenDisplays.forgetAll();
        });

        PlayerEvents.LEAVE.register(player -> {
            com.nachtraben.soundstage.audio.MetadataViewers.forgetPlayer(player.getUUID());
            com.nachtraben.soundstage.audio.LiveInputSessions.stop(player.getUUID(),
                    com.nachtraben.soundstage.audio.LiveInputSession.End.LEFT);
        });

        CreativeTabs.append(ModTabs.SOUNDSTAGE_TAB, ModItems.SPEAKER_ITEM,
                ModItems.SATELLITE_SPEAKER_ITEM, ModItems.CENTER_SPEAKER_ITEM,
                ModItems.SUBWOOFER_ITEM,
                ModItems.PARTY_BOX_ITEM, ModItems.RECEIVER_ITEM, ModItems.SCREEN_ITEM,
                ModItems.SPEAKER_LINK);
	}
}
