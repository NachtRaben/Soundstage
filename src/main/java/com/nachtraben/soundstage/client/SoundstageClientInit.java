package com.nachtraben.soundstage.client;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.client.audio.LinuxShareOutput;
import com.nachtraben.soundstage.client.audio.LiveInputClient;
import com.nachtraben.soundstage.client.gui.AudioPlayerScreen;
import com.nachtraben.soundstage.client.gui.DisplayScreen;
import com.nachtraben.soundstage.client.gui.SpeakerConfigScreen;
import com.nachtraben.soundstage.client.render.ReceiverBlockEntityRenderer;
import com.nachtraben.soundstage.client.render.ScreenBlockEntityRenderer;
import com.nachtraben.soundstage.client.render.ScreenOverlay;
import com.nachtraben.soundstage.client.render.SpeakerBlockEntityRenderer;
import com.nachtraben.soundstage.client.render.StaticMesh;
import com.nachtraben.soundstage.client.video.ClientDisplayInfo;
import com.nachtraben.soundstage.client.video.ClientSubtitleOptions;
import com.nachtraben.soundstage.client.video.ClientSubtitles;
import com.nachtraben.soundstage.client.video.ClientVideoReceivers;
import com.nachtraben.soundstage.client.video.NowPlayingCards;
import com.nachtraben.soundstage.network.NetworkHandler;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import dev.armadeus.armalib.platform.client.ClientRegistration;
import dev.armadeus.armalib.platform.client.event.ClientLifecycleEvents;
import dev.armadeus.armalib.platform.client.event.ClientPlayerEvents;
import dev.armadeus.armalib.platform.client.event.ClientTickEvents;
import dev.armadeus.armalib.platform.client.event.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class SoundstageClientInit {

    public static void clientEntrypoint() {
        init();
    }

    public static void clientSetup() {
    }

    public static void openSpeakerScreen(SpeakerBlockEntity sbe) {
        Minecraft.getInstance().setScreen(new AudioPlayerScreen(sbe));
    }

    public static void openReceiverScreen(ReceiverBlockEntity cbe) {
        Minecraft.getInstance().setScreen(new AudioPlayerScreen(cbe));
    }

    public static void openFromRemote(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            openReceiverScreen(receiver);
        }
    }

    public static void openSpeakerConfigScreen(SpeakerBlockEntity sbe) {
        Minecraft.getInstance().setScreen(new SpeakerConfigScreen(sbe));
    }

    public static void openDisplayScreen(ScreenBlockEntity screen) {
        Minecraft.getInstance().setScreen(
                new DisplayScreen(screen));
    }

    public static final ClientPlayerEvents.LevelChanged ON_LEVEL_CHANGED = new ClientPlayerEvents.LevelChanged() {
        @Override
        public void on(ClientLevel oldLevel,
                       ClientLevel newLevel) {
            AudioDecoder.clear();
            ClientVideoReceivers.clear();
            ClientSubtitles.clear();
            ClientDisplayInfo.clear();
            ClientSubtitleOptions.clear();
            NowPlayingCards.clear();
            StaticMesh.invalidateAll();
        }
    };

    public static void init() {
        NetworkHandler.initClient();

        //? if <1.20.5 {
        /*ClientTickEvents.POST.register(client ->
                com.nachtraben.soundstage.util.ServerPause.clientPaused = client.isPaused());
        *///?}
        ClientTickEvents.POST.register(
                minecraft -> LiveInputClient.tick());
        ClientTickEvents.POST.register(AudioDecoder::tick);
        ClientTickEvents.POST.register(
                minecraft -> {
                    ClientVideoReceivers.tick();
                    NowPlayingCards.tick();
                });
        ClientLifecycleEvents.STOPPING.register(
                minecraft -> LinuxShareOutput.removeIfCreated());

        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.SPEAKER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.SATELLITE_SPEAKER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.CENTER_SPEAKER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.SUBWOOFER_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.PARTY_BOX_BLOCK_ENTITY,
                SpeakerBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.RECEIVER_BLOCK_ENTITY,
                ReceiverBlockEntityRenderer::new
        );
        ClientRegistration.blockEntityRenderer(
            ModBlockEntities.SCREEN_BLOCK_ENTITY,
                ScreenBlockEntityRenderer::new
        );

        //? if >=1.21.11 {
        /*ClientRegistration.specialModelRenderer(
                ResourceLocation.fromNamespaceAndPath(com.nachtraben.soundstage.SoundstageInit.MODID, "block_item"),
                com.nachtraben.soundstage.client.render.BlockItemRenderer.Unbaked.CODEC);
        ClientRegistration.specialModelRenderer(
                ResourceLocation.fromNamespaceAndPath(com.nachtraben.soundstage.SoundstageInit.MODID, "remote"),
                com.nachtraben.soundstage.client.render.RemoteItemRenderer.Unbaked.CODEC);
        *///?} else {
        ClientRegistration.itemRenderer(com.nachtraben.soundstage.client.render.BlockItemRenderer::new,
                com.nachtraben.soundstage.registry.ModItems.SPEAKER_ITEM,
                com.nachtraben.soundstage.registry.ModItems.SATELLITE_SPEAKER_ITEM,
                com.nachtraben.soundstage.registry.ModItems.CENTER_SPEAKER_ITEM,
                com.nachtraben.soundstage.registry.ModItems.SUBWOOFER_ITEM,
                com.nachtraben.soundstage.registry.ModItems.PARTY_BOX_ITEM,
                com.nachtraben.soundstage.registry.ModItems.RECEIVER_ITEM);
        ClientRegistration.itemRenderer(com.nachtraben.soundstage.client.render.RemoteItemRenderer::new,
                com.nachtraben.soundstage.registry.ModItems.REMOTE);
        //?}

        //? if >=1.21.11 {
        /*ClientRegistration.renderPipeline(
                com.nachtraben.soundstage.client.render.SoundstageRenderTypes.SPIDER_PIPELINE,
                compiled -> com.nachtraben.soundstage.client.render.SoundstageRenderTypes.spiderCompiled());
        //? if >=26.2 {
        /^ClientRegistration.renderPipeline(
                com.nachtraben.soundstage.client.render.SoundstageRenderTypes.VIDEO_MIP_PIPELINE,
                compiled -> com.nachtraben.soundstage.client.render.SoundstageRenderTypes.videoMipCompiled());
        ^///?}
        *///?} else {
        ClientRegistration.coreShader(
                com.nachtraben.soundstage.client.render.SoundstageRenderTypes.spiderShaderId(),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.nachtraben.soundstage.client.render.SoundstageRenderTypes::setSpiderShader);
        //?}

        LevelRenderEvents.AFTER_BLOCK_ENTITIES.register(context -> {
            StaticMesh.flushDeferred();
            //? if <1.21.11 {
            ScreenBlockEntityRenderer.flushCaptions();
            //?}
            ClientVideoReceivers.endRenderFrame();
            ClientAudioReceiver.endRenderFrame();
        });
        LevelRenderEvents.AFTER_LEVEL.register(context ->
                ScreenOverlay.drawAfterLevel(
                        context.modelView(), context.projection()));

        ClientPlayerEvents.LEAVE.register(player -> {
            LiveInputClient.shutdown();
            AudioDecoder.clear();
            ClientVideoReceivers.clear();
            ClientSubtitles.clear();
            ClientDisplayInfo.clear();
            ClientSubtitleOptions.clear();
            NowPlayingCards.clear();
            StaticMesh.invalidateAll();
        });

        ClientPlayerEvents.LEVEL_CHANGED.register(ON_LEVEL_CHANGED);

        ClientRegistration.reloadListener(
                ResourceLocation.fromNamespaceAndPath(
                        SoundstageInit.MODID, "static_meshes"),
                (ResourceManagerReloadListener)
                        manager -> StaticMesh.invalidateAll());
    }
}
