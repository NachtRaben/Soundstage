package com.nachtraben.soundstage;

import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.audio.AudioManagerState;
import com.nachtraben.soundstage.audio.LiveInputSession;
import com.nachtraben.soundstage.audio.LiveInputSessions;
import com.nachtraben.soundstage.audio.MetadataViewers;
import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.audio.media.FfmpegDownload;
import com.nachtraben.soundstage.audio.media.MediaFolder;
import com.nachtraben.soundstage.audio.video.ScreenDisplays;
import com.nachtraben.soundstage.command.SoundstageCommand;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.network.NetworkHandler;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import com.nachtraben.soundstage.registry.ModBlocks;
import com.nachtraben.soundstage.registry.ModComponents;
import com.nachtraben.soundstage.registry.ModItems;
import com.nachtraben.soundstage.registry.ModTabs;
import com.nachtraben.soundstage.util.ServerDirectory;
import dev.armadeus.armalib.platform.event.CommandEvents;
import dev.armadeus.armalib.platform.event.LifecycleEvents;
import dev.armadeus.armalib.platform.event.PlayerEvents;
import dev.armadeus.armalib.platform.registry.CreativeTabs;

import java.nio.file.Path;

public class SoundstageInit {
    public static final String MODID = "soundstage";
    public static ModPlatform PLATFORM = null;

    public static void entrypoint(ModPlatform platform) {
        SoundstageInit.PLATFORM = platform;
        ModComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModTabs.init();
        SoundstageConfig.load();
        NetworkHandler.init();

        CommandEvents.REGISTER.register(SoundstageCommand::register);
        LifecycleEvents.SERVER_STARTING.register(server -> {
            Path serverDirectory = ServerDirectory.of(server);
            MediaFolder.serverStarting(serverDirectory,
                    SoundstageConfig.enableMediaFolder());
            FfmpegDownload.serverStarting(serverDirectory);
        });
        LifecycleEvents.SERVER_STOPPED.register(server ->
                MediaFolder.serverStopped());
        LifecycleEvents.SERVER_STOPPING.register(server -> {
            if (AudioManagerState.created()) {
                AudioManager.get().shutdown();
            }
            MetadataViewers.forgetAll();
            SpeakerAudience.forgetAll();
            ScreenDisplays.forgetAll();
        });

        PlayerEvents.LEAVE.register(player -> {
            MetadataViewers.forgetPlayer(player.getUUID());
            LiveInputSessions.stop(player.getUUID(),
                    LiveInputSession.End.LEFT);
        });

        CreativeTabs.append(ModTabs.SOUNDSTAGE_TAB, ModItems.SPEAKER_ITEM,
                ModItems.SATELLITE_SPEAKER_ITEM, ModItems.CENTER_SPEAKER_ITEM,
                ModItems.SUBWOOFER_ITEM,
                ModItems.PARTY_BOX_ITEM, ModItems.RECEIVER_ITEM, ModItems.SCREEN_ITEM,
                ModItems.SPEAKER_LINK, ModItems.REMOTE);
    }
}
