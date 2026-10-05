package com.nachtraben.soundstage.client;

import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.client.gui.SpeakerConfigScreen;
import com.nachtraben.soundstage.client.gui.AudioPlayerScreen;
import com.nachtraben.soundstage.client.render.SpeakerBlockEntityRenderer;
import com.nachtraben.soundstage.network.NetworkHandler;
import dev.armadeus.armalib.platform.client.ClientRegistration;
import dev.armadeus.armalib.platform.client.event.ClientLifecycleEvents;
import dev.armadeus.armalib.platform.client.event.ClientPlayerEvents;
import dev.armadeus.armalib.platform.client.event.ClientTickEvents;
import dev.armadeus.armalib.platform.client.event.LevelRenderEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

public class SoundstageClientInit {

    public static void clientEntrypoint() {
        init();
    }

    public static void clientSetup() {
    }

    public static void openSpeakerScreen(SpeakerBlockEntity sbe) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new AudioPlayerScreen(sbe));
    }

    public static void openReceiverScreen(com.nachtraben.soundstage.block.ReceiverBlockEntity cbe) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new AudioPlayerScreen(cbe));
    }

    public static void openSpeakerConfigScreen(SpeakerBlockEntity sbe) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new SpeakerConfigScreen(sbe));
    }

    public static void openDisplayScreen(com.nachtraben.soundstage.block.ScreenBlockEntity screen) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.nachtraben.soundstage.client.gui.DisplayScreen(screen));
    }

    public static final ClientPlayerEvents.LevelChanged ON_LEVEL_CHANGED = new ClientPlayerEvents.LevelChanged() {
        @Override
        public void on(net.minecraft.client.multiplayer.ClientLevel oldLevel,
                       net.minecraft.client.multiplayer.ClientLevel newLevel) {
            com.nachtraben.soundstage.client.audio.AudioDecoder.clear();
            com.nachtraben.soundstage.client.video.ClientVideoReceivers.clear();
            com.nachtraben.soundstage.client.video.ClientSubtitles.clear();
            com.nachtraben.soundstage.client.video.ClientDisplayInfo.clear();
            com.nachtraben.soundstage.client.video.ClientSubtitleOptions.clear();
            com.nachtraben.soundstage.client.video.NowPlayingCards.clear();
            com.nachtraben.soundstage.client.render.StaticMesh.invalidateAll();
        }
    };

    public static void init() {
        NetworkHandler.initClient();

        //? if <1.20.5 {
        /*ClientTickEvents.POST.register(client -> com.nachtraben.soundstage.util.ServerPause.clientPaused = client.isPaused());
        *///?}
        ClientTickEvents.POST.register(
                minecraft -> com.nachtraben.soundstage.client.audio.LiveInputClient.tick());
        ClientTickEvents.POST.register(com.nachtraben.soundstage.client.audio.AudioDecoder::tick);
        ClientTickEvents.POST.register(
                minecraft -> {
                    com.nachtraben.soundstage.client.video.ClientVideoReceivers.tick();
                    com.nachtraben.soundstage.client.video.NowPlayingCards.tick();
                });
        ClientLifecycleEvents.STOPPING.register(
                minecraft -> com.nachtraben.soundstage.client.audio.LinuxShareOutput.removeIfCreated());

        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.SPEAKER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.SATELLITE_SPEAKER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.CENTER_SPEAKER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.SUBWOOFER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.PARTY_BOX_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.RECEIVER_BLOCK_ENTITY,
                com.nachtraben.soundstage.client.render.ReceiverBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            com.nachtraben.soundstage.registry.ModBlockEntities.SCREEN_BLOCK_ENTITY,
                com.nachtraben.soundstage.client.render.ScreenBlockEntityRenderer::new
        );

        if (com.nachtraben.soundstage.RigStaging.sessionStages("soundstage:item-and-cabinet-drawn", "no-item-renderer")
                || com.nachtraben.soundstage.RigStaging.sessionStages("soundstage:item-icons", "no-item-renderer")) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn("Soundstage: red control — the item "
                    + "renderer is not registered (rig soundstage:item-and-cabinet-drawn)");
        } else {
            //? if >=1.21.11 {
            /*ClientRegistration.specialModelRenderer(
                    ResourceLocation.fromNamespaceAndPath(com.nachtraben.soundstage.SoundstageInit.MODID, "block_item"),
                    com.nachtraben.soundstage.client.render.BlockItemRenderer.Unbaked.CODEC);
            *///?} else {
            ClientRegistration.itemRenderer(com.nachtraben.soundstage.client.render.BlockItemRenderer::new,
                    com.nachtraben.soundstage.registry.ModItems.SPEAKER_ITEM,
                    com.nachtraben.soundstage.registry.ModItems.SATELLITE_SPEAKER_ITEM,
                    com.nachtraben.soundstage.registry.ModItems.CENTER_SPEAKER_ITEM,
                    com.nachtraben.soundstage.registry.ModItems.SUBWOOFER_ITEM,
                    com.nachtraben.soundstage.registry.ModItems.PARTY_BOX_ITEM,
                    com.nachtraben.soundstage.registry.ModItems.RECEIVER_ITEM);
            //?}
        }

        //? if >=1.21.11 {
        /*ClientRegistration.renderPipeline(com.nachtraben.soundstage.client.render.SoundstageRenderTypes.SPIDER_PIPELINE,
                compiled -> com.nachtraben.soundstage.client.render.SoundstageRenderTypes.spiderCompiled());
        if (com.nachtraben.soundstage.client.render.SoundstageRenderTypes.SPIDER_NO_DEPTH_PIPELINE != null) {
            ClientRegistration.renderPipeline(com.nachtraben.soundstage.client.render.SoundstageRenderTypes.SPIDER_NO_DEPTH_PIPELINE,
                    compiled -> { });
        }
        *///?} else {
        ClientRegistration.coreShader(
                com.nachtraben.soundstage.client.render.SoundstageRenderTypes.spiderShaderId(),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.nachtraben.soundstage.client.render.SoundstageRenderTypes::setSpiderShader);
        //?}

        LevelRenderEvents.AFTER_BLOCK_ENTITIES.register(context -> {
            com.nachtraben.soundstage.client.render.StaticMesh.flushDeferred();
            //? if <1.21.11 {
            com.nachtraben.soundstage.client.render.ScreenBlockEntityRenderer.flushCaptions();
            //?}
            com.nachtraben.soundstage.client.audio.ClientAudioReceiver.endRenderFrame();
        });
        LevelRenderEvents.AFTER_LEVEL.register(context ->
                com.nachtraben.soundstage.client.render.ScreenOverlay.drawAfterLevel(
                        context.modelView(), context.projection()));

        ClientPlayerEvents.LEAVE.register(player -> {
            com.nachtraben.soundstage.client.audio.LiveInputClient.shutdown();
            com.nachtraben.soundstage.client.audio.AudioDecoder.clear();
            com.nachtraben.soundstage.client.video.ClientVideoReceivers.clear();
            com.nachtraben.soundstage.client.video.ClientSubtitles.clear();
            com.nachtraben.soundstage.client.video.ClientDisplayInfo.clear();
            com.nachtraben.soundstage.client.video.ClientSubtitleOptions.clear();
            com.nachtraben.soundstage.client.video.NowPlayingCards.clear();
            com.nachtraben.soundstage.client.render.StaticMesh.invalidateAll();
        });

        ClientPlayerEvents.LEVEL_CHANGED.register(ON_LEVEL_CHANGED);

        ClientRegistration.reloadListener(
                ResourceLocation.fromNamespaceAndPath(
                        com.nachtraben.soundstage.SoundstageInit.MODID, "static_meshes"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        manager -> com.nachtraben.soundstage.client.render.StaticMesh.invalidateAll());
    }
}
