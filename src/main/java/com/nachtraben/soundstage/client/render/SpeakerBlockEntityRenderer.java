package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nachtraben.soundstage.block.PartyBoxBlock;
import com.nachtraben.soundstage.block.PartyBoxBlockEntity;
import com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.CenterSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SatelliteSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.block.speaker.SubwooferBlock;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.client.audio.StreamingSoundInstance;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.core.SpeakerGeometry;
import com.nachtraben.soundstage.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

//? if >=1.21.11 {
/*public class SpeakerBlockEntityRenderer<T extends SpeakerBlockEntity>
        implements BlockEntityRenderer<T, BlockEntityState<T>> {
*///?} else {
public class SpeakerBlockEntityRenderer<T extends SpeakerBlockEntity> implements BlockEntityRenderer<T> {
//?}

    private static final int PEAK_HOLD_FRAMES = 12;

    private record CabinetDesign(CabinetRenderer.Layout layout, DriverRenderer.Driver[] drivers) {
        CabinetRenderer.Hole[] holes() {
            return Arrays.stream(drivers).map(DriverRenderer.Driver::hole)
                    .toArray(CabinetRenderer.Hole[]::new);
        }
    }

    private static DriverRenderer.Driver woofer(float x, float y, float width) {
        return new DriverRenderer.Driver(DriverRenderer.Kind.WOOFER, x, y, width);
    }

    private static DriverRenderer.Driver tweeter(float x, float y, float radius) {
        return new DriverRenderer.Driver(DriverRenderer.Kind.TWEETER, x, y, radius);
    }

    private static final CabinetDesign SPEAKER_DESIGN = new CabinetDesign(
            new CabinetRenderer.Layout(1, 0, 4, 15, 16, 14, 1, 0.625f, 0.6f),
            new DriverRenderer.Driver[]{tweeter(8, 13.0f, 1.8f),
                    woofer(8, 6.0f, SpeakerGeometry.MAIN_WOOFER_WIDTH_PX)});

    private static final CabinetDesign SATELLITE_DESIGN = new CabinetDesign(
            new CabinetRenderer.Layout(4, 0, 4, 12, 12, 14, 1, 0.5f, 0.6f),
            new DriverRenderer.Driver[]{tweeter(8, 9.0f, 1.35f),
                    woofer(8, 4.2f, SpeakerGeometry.SATELLITE_WOOFER_WIDTH_PX)});

    private static final CabinetDesign CENTER_DESIGN = new CabinetDesign(
            new CabinetRenderer.Layout(1, 0, 4, 15, 8, 14, 1, 0.5f, 0.6f),
            new DriverRenderer.Driver[]{
                    woofer(4.45f, 4, SpeakerGeometry.CENTER_WOOFER_WIDTH_PX),
                    tweeter(8, 4, 1.2f),
                    woofer(11.55f, 4, SpeakerGeometry.CENTER_WOOFER_WIDTH_PX)});

    private static final CabinetDesign SUBWOOFER_DESIGN = new CabinetDesign(
            new CabinetRenderer.Layout(0, 0, 1.2f, 16, 16, 16, 1, 0.625f, 1.2f),
            new DriverRenderer.Driver[]{woofer(8, 8, SpeakerGeometry.SUBWOOFER_WOOFER_WIDTH_PX)});

    public static float minimumGrilleClearance() {
        float worst = Float.MAX_VALUE;
        CabinetDesign[] designs = {SPEAKER_DESIGN, SATELLITE_DESIGN, CENTER_DESIGN, SUBWOOFER_DESIGN};
        for (CabinetDesign design : designs) {
            worst = Math.min(worst, clearance(design.layout(), design.drivers()));
        }
        return Math.min(worst, clearance(PartyBoxRenderer.SLAB, PartyBoxRenderer.DRIVERS));
    }

    private static float clearance(CabinetRenderer.Layout layout, DriverRenderer.Driver[] drivers) {
        float worst = Float.MAX_VALUE;
        for (DriverRenderer.Driver driver : drivers) {
            if (driver.kind() != DriverRenderer.Kind.WOOFER) continue;
            float front = layout.baffleZ() - WooferRenderer.coneReachUnits(driver.size());
            worst = Math.min(worst, front - layout.grilleFrontZ());
        }
        return worst;
    }

    private static CabinetDesign designFor(BlockState state) {
        var block = state.getBlock();
        if (block instanceof SubwooferBlock) return SUBWOOFER_DESIGN;
        if (block instanceof SatelliteSpeakerBlock) return SATELLITE_DESIGN;
        if (block instanceof CenterSpeakerBlock) return CENTER_DESIGN;
        if (block instanceof SpeakerBlock) return SPEAKER_DESIGN;
        return null;
    }

    public SpeakerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.11 {
    /*@Override
    public BlockEntityState<T> createRenderState() {
        return new BlockEntityState<>();
    }

    @Override
    public void extractRenderState(T blockEntity, BlockEntityState<T> state, float partialTick,
            net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jetbrains.annotations.Nullable
                    CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, breaking);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(BlockEntityState<T> state, PoseStack poseStack,
                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.CameraRenderState camera) {
        if (state.blockEntity == null) return;
        draw(state.blockEntity, poseStack, GeometrySinks.submit(poseStack, collector), state.lightCoords,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }
    *///?} else {
    @Override
    public void render(T blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight,
                       int packedOverlay) {
        draw(blockEntity, poseStack, GeometrySinks.immediate(poseStack, bufferSource), packedLight, packedOverlay);
    }
    //?}

    void drawItem(T blockEntity, PoseStack poseStack, GeometrySink sink, int packedLight, int packedOverlay) {
        draw(blockEntity, poseStack, sink, packedLight, packedOverlay);
    }

    private void draw(T blockEntity, PoseStack poseStack, GeometrySink sink, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof AbstractSpeakerBlock)) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        int rotation = state.getValue(AbstractSpeakerBlock.ROTATION);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation * 360f / 16f - blockEntity.getYawOffset()));
        poseStack.mulPose(Axis.XP.rotationDegrees(blockEntity.getPitch()));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Playback playback = restIfTooFarToRead(blockEntity, state, playbackFor(blockEntity));
        boolean grille = state.getValue(AbstractSpeakerBlock.GRILLE);

        if (blockEntity instanceof PartyBoxBlockEntity box && state.getBlock() instanceof PartyBoxBlock) {
            float level = playback.level();
            float peak = playback.peak();
            PartyBoxRenderer.render(box, grille, playback.excursion(), level, peak,
                    poseStack, sink, packedLight, packedOverlay);
        } else {
            CabinetDesign design = designFor(state);
            if (design != null) {
                renderCabinet(design, blockEntity, grille, playback.excursion(), poseStack, sink,
                        packedLight, packedOverlay);
            }
        }

        poseStack.popPose();
    }

    private record CabinetStaticKey(CabinetDesign design, SpeakerFinish finish,
                                    boolean grille, int light, int overlay) {}

    private record CabinetIdleKey(CabinetDesign design, SpeakerFinish finish,
                                  boolean grille, int light, int overlay) {}

    private static void renderCabinet(CabinetDesign design, SpeakerBlockEntity speaker, boolean grille,
                                      float excursion, PoseStack poseStack, GeometrySink sink,
                                      int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        var finish = speaker.getFinish();
        boolean clear = finish.isTransparent();

        if (excursion == 0.0f) {
            speaker.renderCache.render(new CabinetIdleKey(design, finish, grille, light, overlay), poseStack, sink,
                    (scratch, idleSink) -> {
                        renderCabinetStatic(design, finish, grille, scratch, idleSink, light, overlay);
                        renderCabinetDrivers(design, 0.0f, clear, scratch, idleSink, light, overlay);
                    });
        } else {
            speaker.renderCache.render(new CabinetStaticKey(design, finish, grille, light, overlay), poseStack, sink,
                    (scratch, staticSink) ->
                            renderCabinetStatic(design, finish, grille, scratch, staticSink, light, overlay));
            renderCabinetDrivers(design, excursion, clear, poseStack, sink, light, overlay);
        }

        CabinetRenderer.renderClearCasing(design.layout(), finish, sink, light, overlay);
        poseStack.popPose();
    }

    private static void renderCabinetDrivers(CabinetDesign design, float excursion, boolean interior,
                                             PoseStack poseStack, GeometrySink sink,
                                             int light, int overlay) {
        float baffleZ = design.layout().baffleZ();
        for (DriverRenderer.Driver driver : design.drivers()) {
            if (driver.kind() == DriverRenderer.Kind.WOOFER) {
                WooferRenderer.renderFrontDynamic(excursion, driver.size(), driver.x(), driver.y(),
                        baffleZ, interior, poseStack, sink, light, overlay);
            }
        }
    }

    private static void renderCabinetStatic(CabinetDesign design, SpeakerFinish finish,
                                            boolean grille, PoseStack poseStack, GeometrySink sink,
                                            int light, int overlay) {
        CabinetRenderer.render(design.layout(), finish, grille, design.holes(), poseStack, sink, light, overlay);
        float baffleZ = design.layout().baffleZ();
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) -> {
            for (DriverRenderer.Driver driver : design.drivers()) {
                if (driver.kind() == DriverRenderer.Kind.TWEETER) {
                    DriverRenderer.tweeter(driver, baffleZ, pose, consumer, light, overlay);
                }
            }
        }));
        for (DriverRenderer.Driver driver : design.drivers()) {
            if (driver.kind() == DriverRenderer.Kind.WOOFER) {
                WooferRenderer.renderFrontStatic(driver.size(), driver.x(), driver.y(), baffleZ,
                        finish.isTransparent(), poseStack, sink, light, overlay);
            }
        }
    }

    private static final double ANIMATION_RANGE = 8.0;
    private static final double ANIMATION_RANGE_SQR = ANIMATION_RANGE * ANIMATION_RANGE;
    private static final double SUB_ANIMATION_RANGE = 16.0;
    private static final double SUB_ANIMATION_RANGE_SQR = SUB_ANIMATION_RANGE * SUB_ANIMATION_RANGE;

    private static Playback restIfTooFarToRead(SpeakerBlockEntity blockEntity, BlockState state,
                                               Playback playback) {
        if (playback.excursion() == 0.0f) return playback;
        boolean subwoofer = state.is(ModBlocks.SUBWOOFER_BLOCK.get());
        Camera camera = Minecraft.getInstance()
                .gameRenderer.getMainCamera();
        if (camera == null) return playback;
        double range = subwoofer ? SUB_ANIMATION_RANGE_SQR : ANIMATION_RANGE_SQR;
        //? if >=1.21.11 {
        /*net.minecraft.world.phys.Vec3 eye = camera.position();
        *///?} else {
        net.minecraft.world.phys.Vec3 eye = camera.getPosition();
        //?}
        if (blockEntity.getBlockPos().distToCenterSqr(eye) <= range) {
            return playback;
        }
        return new Playback(0.0f, playback.level(), playback.peak());
    }

    private record Playback(float excursion, float level, float peak) {
        static final Playback SILENT = new Playback(0.0f, 0.0f, 0.0f);
    }

    private Playback playbackFor(T blockEntity) {
        if (blockEntity.getReceiverPos() == null) {
            return Playback.SILENT;
        }
        ClientAudioReceiver receiver =
                AudioDecoder.getReceiver(blockEntity.getReceiverPos());
        if (receiver == null) {
            return Playback.SILENT;
        }
        StreamingSoundInstance stream =
                receiver.streams.get(blockEntity.getBlockPos());
        if (stream == null) {
            return Playback.SILENT;
        }

        double audible = receiver.audibleSequenceThisFrame();
        float gain = SoundstageAppearance.driverExcursionGain();
        return new Playback(
                Mth.clamp(stream.sampleExcursion(audible) * gain, -1.0f, 1.0f),
                Mth.clamp(stream.sampleLevel(audible), 0.0f, 1.0f),
                Mth.clamp(stream.peakLevel(audible, PEAK_HOLD_FRAMES), 0.0f, 1.0f));
    }
}
