package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlock;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.core.KnobSweep;
import com.nachtraben.soundstage.core.TubeThermalModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

//? if >=1.21.11 {
/*public class ReceiverBlockEntityRenderer
        implements BlockEntityRenderer<ReceiverBlockEntity, BlockEntityState<ReceiverBlockEntity>> {
*///?} else {
public class ReceiverBlockEntityRenderer implements BlockEntityRenderer<ReceiverBlockEntity> {
//?}

    private static final ResourceLocation NEEDLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/led.png");

    private static final float PX_PER_UNIT = 16f;
    private static final float FACE_HEIGHT_UNITS = 6f;
    private static final float FACE_ORIGIN_UNITS = 15f;

    private static final float METER_W = 72f;
    private static final float METER_H = 56f;
    private static final float METER_Y = 14f;
    private static final float LEFT_METER_X = 16f;
    private static final float RIGHT_METER_X = LEFT_METER_X + METER_W + 8f;
    private static final float DIAL_INSET = 3f;
    private static final float NEEDLE_RADIUS_PX = 34f;

    private static final float SWEEP_REST_DEG = -140f;
    private static final float SWEEP_FULL_DEG = -40f;

    private static final float NEEDLE_Z = at(0.9f);

    private static final float NEEDLE_WIDTH_PIVOT_PX = 2.4f;
    private static final float NEEDLE_WIDTH_TIP_PX = 1.0f;

    private static final int NEEDLE_R = 44, NEEDLE_G = 41, NEEDLE_B = 36;

    private static final float BUTTON_FIRST_X_PX = 30f;
    private static final float BUTTON_PITCH_PX = 16f;
    private static final float BUTTON_W_PX = 10f;
    private static final float BUTTON_TOP_PX = 74f;
    private static final float BUTTON_H_PX = 8f;

    private static final float KNOB_X_PX = 190f;
    private static final float KNOB_Y_PX = 44f;
    private static final float KNOB_RADIUS_PX = 17f;
    private static final float KNOB_FRONT_Z = at(0.2f);
    private static final float KNOB_BACK_Z = at(1.0f);

    private static final ResourceLocation TUBE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/receiver_tube.png");

    private static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/tube_halo.png");

    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/led_glow.png");

    private static final float[] TUBE_CENTERS_X = {4.0f, 7.0f, 10.0f, 13.0f};
    private static final float TUBE_CENTER_Z = 8.0f;

    private static final float FILAMENT_RADIUS = 0.98f;
    private static final float FILAMENT_BOTTOM = 7.1f;
    private static final float FILAMENT_TOP = 11.1f;

    private static final float POOL_HALF = 4.6f;
    private static final float POOL_HEIGHT = 6.74f;

    private static final float BACKLIGHT_GAIN = 1.6f;
    private static final float HALO_GAIN = 0.75f;
    private static final float POOL_GAIN = 1.4f;

    private static final float HALO_HALF_WIDTH = 3.0f;
    private static final float HALO_HALF_HEIGHT = 4.2f;
    private static final float HALO_CENTER_Y = 9.4f;

    private static final int GLOW_WARM_R = 255, GLOW_WARM_G = 148, GLOW_WARM_B = 58;
    private static final int GLOW_HOT_R = 255, GLOW_HOT_G = 62, GLOW_HOT_B = 34;

    private static final float FACE_Z = at(1.0f);
    private static final float KEY_OUT_UNITS = 0.45f;
    private static final float KEY_IN_UNITS = 0.12f;

    private static float at(float blockUnits) {
        return CabinetRenderer.at(blockUnits);
    }

    private static float units(float blockUnits) {
        return blockUnits / 16f;
    }

    private static float faceX(float artPx) {
        return at(FACE_ORIGIN_UNITS - artPx / PX_PER_UNIT);
    }

    private static float faceY(float artPx) {
        return at(FACE_HEIGHT_UNITS - artPx / PX_PER_UNIT);
    }

    private static float pxLength(float artPx) {
        return artPx / PX_PER_UNIT / 16f;
    }

    public ReceiverBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.11 {
    /*@Override
    public BlockEntityState<ReceiverBlockEntity> createRenderState() {
        return new BlockEntityState<>();
    }

    @Override
    public void extractRenderState(ReceiverBlockEntity blockEntity, BlockEntityState<ReceiverBlockEntity> state,
            float partialTick, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jetbrains.annotations.Nullable
                    CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, breaking);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(BlockEntityState<ReceiverBlockEntity> state, PoseStack poseStack,
                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.CameraRenderState camera) {
        if (state.blockEntity == null) return;
        draw(state.blockEntity, state.partialTick, poseStack, GeometrySinks.submit(poseStack, collector),
                state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null);
    }
    *///?} else {
    @Override
    public void render(ReceiverBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight,
                       int packedOverlay) {
        draw(blockEntity, partialTick, poseStack, GeometrySinks.immediate(poseStack, bufferSource), packedLight,
                packedOverlay, bufferSource);
    }
    //?}

    private record ReceiverStaticKey(SpeakerFinish finish,
                                     int light, int overlay) {}

    private static final StaticMesh GLASS_MESH = new StaticMesh(8);
    private static final StaticMesh BACKLIGHT_MESH = new StaticMesh(8);

    private record GlassKey(int light, int overlay) {}

    private record BacklightKey(int overlay) {}

    private static final StaticMesh POOL_MESH = new StaticMesh(8);
    private static final StaticMesh HALO_MESH = new StaticMesh(8);

    private record GlowKey(int overlay) {}

    private static GlowKey lastGlowKey = new GlowKey(0);

    private static GlowKey glowKey(int overlay) {
        if (lastGlowKey.overlay() != overlay) lastGlowKey = new GlowKey(overlay);
        return lastGlowKey;
    }

    private static void renderReceiverStatic(SpeakerFinish finish,
                                             GeometrySink sink, int light, int overlay) {
        if (!finish.isTransparent()) {
            renderCheeks(finish, sink, light, overlay);
        }
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) ->
                AmplifierChassisRenderer.render(pose, consumer, light, overlay)));
    }

    private static void renderCheeks(SpeakerFinish finish,
                                     GeometrySink sink, int light, int overlay) {
        CabinetRenderer.renderWoodBox(finish, 0f, 0f, 1f, 1f, 6.8f, 15f, sink, light, overlay);
        CabinetRenderer.renderWoodBox(finish, 15f, 0f, 1f, 16f, 6.8f, 15f, sink, light, overlay);
    }

    void drawItem(ReceiverBlockEntity blockEntity, PoseStack poseStack, GeometrySink sink, int packedLight,
                  int packedOverlay) {
        draw(blockEntity, 0f, poseStack, sink, packedLight, packedOverlay, null);
    }

    private void draw(ReceiverBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      GeometrySink sink, int packedLight, int packedOverlay,
                      Object bufferSource) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof ReceiverBlock)) return;

        poseStack.pushPose();

        poseStack.translate(0.5D, 0.5D, 0.5D);
        int rotation = state.getValue(ReceiverBlock.ROTATION);
        float baseYaw = rotation * 360f / 16f;
        poseStack.mulPose(Axis.YP.rotationDegrees(baseYaw));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        BlockState baseState = state.setValue(ReceiverBlock.ROTATION, 0);

        sink.emitBlockModel(SoundstageRenderTypes.cutoutBlock(), baseState, packedLight, packedOverlay);

        float left = 0f;
        float right = 0f;
        ClientAudioReceiver receiver = AudioDecoder.getReceiver(blockEntity.getBlockPos());
        if (receiver != null) {
            double audible = receiver.audibleSequenceThisFrame();
            left = Mth.clamp(receiver.sampleLeftLevel(audible), 0f, 1f);
            right = Mth.clamp(receiver.sampleRightLevel(audible), 0f, 1f);
        }

        poseStack.translate(0.5D, 0.5D, 0.5D);

        blockEntity.renderCache.render(
                new ReceiverStaticKey(blockEntity.getFinish(), packedLight, packedOverlay),
                poseStack, sink,
                (scratch, staticSink) -> renderReceiverStatic(blockEntity.getFinish(), staticSink,
                        packedLight, packedOverlay));
        if (blockEntity.getFinish().isTransparent()) {
            renderCheeks(blockEntity.getFinish(), sink, packedLight, packedOverlay);
        }
        final ClientAudioReceiver receiverRef = receiver;
        final float leftLevel = left;
        final float rightLevel = right;
        final boolean signal = receiverRef != null && !receiverRef.ring.isEmpty();
        PANEL_MESH.render(new PanelKey(blockEntity.isPlaying(), blockEntity.getLoopMode(),
                        blockEntity.isShuffle(), blockEntity.isLocked(), signal,
                        blockEntity.getVolume(), packedLight, packedOverlay),
                poseStack, sink, (scratch, panelSink) ->
                        panelSink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) ->
                                renderPanel(pose, consumer, blockEntity, signal, packedLight, packedOverlay))));

        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) -> {
            renderNeedle(pose, consumer, LEFT_METER_X, leftLevel, packedLight, packedOverlay);
            renderNeedle(pose, consumer, RIGHT_METER_X, rightLevel, packedLight, packedOverlay);
        }));

        boolean glassFirst = glassUnderLight();
        if (glassFirst) renderTubeGlass(poseStack, sink, packedLight, packedOverlay);
        renderIndicatorGlow(poseStack, sink, blockEntity, receiverRef != null && !receiverRef.ring.isEmpty(),
                packedOverlay);
        renderMeterBacklight(poseStack, sink, packedOverlay);
        renderTubeGlow(poseStack, sink, receiver, baseYaw, packedOverlay);

        //? if <1.21.11 {
        if (bufferSource instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource batched) {
            batched.endBatch(SoundstageRenderTypes.cutoutBlock());
            batched.endBatch(SoundstageRenderTypes.eyes(NEEDLE_TEXTURE));
            batched.endBatch(SoundstageRenderTypes.eyes(HALO_TEXTURE));
        }
        //?}

        if (!glassFirst) {
            renderTubeGlass(poseStack, sink, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private static boolean glassUnderLight() {
        //? if >=1.21.11 {
        /*return IrisCompat.isShaderPackInUse();
        *///?} else {
        return false;
        //?}
    }

    private void renderPanel(PoseStack.Pose pose, VertexConsumer consumer,
                             ReceiverBlockEntity blockEntity, boolean signal,
                             int packedLight, int packedOverlay) {
        renderButton(pose, consumer, 0, blockEntity.isPlaying(), packedLight, packedOverlay);
        renderButton(pose, consumer, 1, blockEntity.getLoopMode() != 0, packedLight, packedOverlay);
        renderButton(pose, consumer, 2, blockEntity.isShuffle(), packedLight, packedOverlay);
        renderButton(pose, consumer, 3, blockEntity.isLocked(), packedLight, packedOverlay);
        renderSignalLamp(pose, consumer, signal, packedLight, packedOverlay);
        renderVolumeKnob(pose, consumer,
                blockEntity.getVolume() / (float) IAudioReceiver.MAX_VOLUME,
                packedLight, packedOverlay);
    }

    private record PanelKey(boolean playing, int loopMode, boolean shuffle, boolean locked,
                            boolean signal, int volume, int light, int overlay) {}

    private static final StaticMesh PANEL_MESH = new StaticMesh(32);

    private void renderVolumeKnob(PoseStack.Pose pose, VertexConsumer consumer, float volume,
                                  int light, int overlay) {
        float cx = faceX(KNOB_X_PX), cy = faceY(KNOB_Y_PX), r = pxLength(KNOB_RADIUS_PX);
        int ribs = 24;
        for (int i = 0; i < ribs; i++) {
            float a0 = (float) (Math.PI * 2 * i / ribs), a1 = (float) (Math.PI * 2 * (i + 1) / ribs);
            float mid = (a0 + a1) / 2f;
            int rgb = (i & 1) == 0 ? 0x2E3034 : 0x202226;
            Mesh.emitQuad(pose, consumer,
                    cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, KNOB_BACK_Z,
                    cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, KNOB_FRONT_Z,
                    cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, KNOB_FRONT_Z,
                    cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, KNOB_BACK_Z,
                    rgb, 255, Mth.cos(mid), Mth.sin(mid), 0, light, overlay);
        }
        Mesh.emitOffsetDisc(pose, consumer, cx, cy, r, KNOB_FRONT_Z, 0x35383D, -1, light, overlay);
        Mesh.emitOffsetDisc(pose, consumer, cx, cy, r * 0.72f, KNOB_FRONT_Z - 0.002f, 0x2A2C30, -1, light, overlay);

        float[] sweep = KnobSweep.direction(volume);
        float dirX = sweep[0], dirY = -sweep[1];
        float x0 = faceX(KNOB_X_PX + dirX * KNOB_RADIUS_PX * 0.25f);
        float y0 = faceY(KNOB_Y_PX + dirY * KNOB_RADIUS_PX * 0.25f);
        float x1 = faceX(KNOB_X_PX + dirX * KNOB_RADIUS_PX * 0.92f);
        float y1 = faceY(KNOB_Y_PX + dirY * KNOB_RADIUS_PX * 0.92f);
        float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
        float px = -dy / len * pxLength(1.4f), py = dx / len * pxLength(1.4f);
        float z = KNOB_FRONT_Z - 0.004f;
        int lit = 0x00F000F0;
        emitAt(pose, consumer, x0 + px, y0 + py, z, 0x6C, 0xFF, 0x7A, lit, overlay);
        emitAt(pose, consumer, x0 - px, y0 - py, z, 0x6C, 0xFF, 0x7A, lit, overlay);
        emitAt(pose, consumer, x1 - px, y1 - py, z, 0x6C, 0xFF, 0x7A, lit, overlay);
        emitAt(pose, consumer, x1 + px, y1 + py, z, 0x6C, 0xFF, 0x7A, lit, overlay);
    }

    private void renderNeedle(PoseStack.Pose pose, VertexConsumer consumer, float meterXPx,
                              float level, int packedLight, int packedOverlay) {
        float pivotPxX = meterXPx + METER_W / 2f;
        float pivotPxY = METER_Y + METER_H - DIAL_INSET - 2f;

        float angle = (float) Math.toRadians(SWEEP_REST_DEG + level * (SWEEP_FULL_DEG - SWEEP_REST_DEG));
        float tipPxX = pivotPxX + NEEDLE_RADIUS_PX * (float) Math.cos(angle);
        float tipPxY = pivotPxY + NEEDLE_RADIUS_PX * (float) Math.sin(angle);

        float x0 = faceX(pivotPxX), y0 = faceY(pivotPxY);
        float x1 = faceX(tipPxX), y1 = faceY(tipPxY);

        float dx = x1 - x0, dy = y1 - y0;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-6f) return;
        float px = -dy / length, py = dx / length;

        float basis = pxLength(NEEDLE_WIDTH_PIVOT_PX) / 2f;
        float tip = pxLength(NEEDLE_WIDTH_TIP_PX) / 2f;

        emit(pose, consumer, x0 + px * basis, y0 + py * basis, packedLight, packedOverlay);
        emit(pose, consumer, x0 - px * basis, y0 - py * basis, packedLight, packedOverlay);
        emit(pose, consumer, x1 - px * tip, y1 - py * tip, packedLight, packedOverlay);
        emit(pose, consumer, x1 + px * tip, y1 + py * tip, packedLight, packedOverlay);
    }

    private void renderButton(PoseStack.Pose pose, VertexConsumer consumer, int index, boolean pressed,
                              int packedLight, int packedOverlay) {
        float leftPx = BUTTON_FIRST_X_PX + index * BUTTON_PITCH_PX;
        float xa = faceX(leftPx + BUTTON_W_PX), xb = faceX(leftPx);
        float ya = faceY(BUTTON_TOP_PX + BUTTON_H_PX), yb = faceY(BUTTON_TOP_PX);
        float slot = pxLength(1.2f);
        float front = FACE_Z - units(pressed ? KEY_IN_UNITS : KEY_OUT_UNITS);

        Mesh.emitQuad(pose, consumer, xa - slot, ya - slot, FACE_Z - 0.001f, xa - slot, yb + slot, FACE_Z - 0.001f,
                xb + slot, yb + slot, FACE_Z - 0.001f, xb + slot, ya - slot, FACE_Z - 0.001f,
                0x151618, 255, 0, 0, -1, packedLight, packedOverlay);
        Mesh.emitBox(pose, consumer, xa, ya, front, xb, yb, FACE_Z, pressed ? 0x5E6368 : 0x8C9197,
                packedLight, packedOverlay);
        float bevel = pxLength(0.8f);
        Mesh.emitQuad(pose, consumer, xa + bevel, ya + bevel, front - 0.001f, xa + bevel, yb - bevel, front - 0.001f,
                xb - bevel, yb - bevel, front - 0.001f, xb - bevel, ya + bevel, front - 0.001f,
                pressed ? 0x9A9FA4 : 0xD6DADE, 255, 0, 0, -1, packedLight, packedOverlay);

        float ledX = (xa + xb) / 2f, ledY = yb - pxLength(2.2f), ledHalfX = pxLength(1.6f), ledHalfY = pxLength(0.8f);
        int led = pressed ? 0x6CFF7A : 0x24402A;
        Mesh.emitQuad(pose, consumer, ledX - ledHalfX, ledY - ledHalfY, front - 0.002f, ledX - ledHalfX,
                ledY + ledHalfY, front - 0.002f,
                ledX + ledHalfX, ledY + ledHalfY, front - 0.002f, ledX + ledHalfX, ledY - ledHalfY, front - 0.002f,
                led, 255, 0, 0, -1, pressed ? 0x00F000F0 : packedLight, packedOverlay);
    }

    private void renderIndicatorGlow(PoseStack poseStack, GeometrySink sink,
                                     ReceiverBlockEntity blockEntity,
                                     boolean signal, int packedOverlay) {
        boolean[] latched = {blockEntity.isPlaying(), blockEntity.getLoopMode() != 0,
                blockEntity.isShuffle(), blockEntity.isLocked()};
        int light = 0x00F000F0;
        for (int index = 0; index < latched.length; index++) {
            if (!latched[index]) continue;
            float leftPx = BUTTON_FIRST_X_PX + index * BUTTON_PITCH_PX;
            float x = (faceX(leftPx + BUTTON_W_PX) + faceX(leftPx)) / 2f;
            float y = faceY(BUTTON_TOP_PX) - pxLength(2.2f);
            float z = FACE_Z - units(KEY_IN_UNITS) - 0.004f;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            sink.emit(SoundstageRenderTypes.eyes(HALO_TEXTURE), (pose, glow) ->
                    quad(pose, glow, -pxLength(INDICATOR_GLOW_PX), pxLength(INDICATOR_GLOW_PX * 0.7f),
                            0x4C, 0xC8, 0x58, 255, light, packedOverlay));
            poseStack.popPose();
        }
        float[] sweep = KnobSweep.direction(
                blockEntity.getVolume() / (float) IAudioReceiver.MAX_VOLUME);
        float stripeX = faceX(KNOB_X_PX + sweep[0] * KNOB_RADIUS_PX * 0.6f);
        float stripeY = faceY(KNOB_Y_PX - sweep[1] * KNOB_RADIUS_PX * 0.6f);
        poseStack.pushPose();
        poseStack.translate(stripeX, stripeY, KNOB_FRONT_Z - 0.008f);
        sink.emit(SoundstageRenderTypes.eyes(HALO_TEXTURE), (pose, glow) ->
                quad(pose, glow, -pxLength(INDICATOR_GLOW_PX), pxLength(INDICATOR_GLOW_PX),
                        0x3A, 0xA0, 0x46, 255, light, packedOverlay));
        poseStack.popPose();
        if (signal) {
            float cx = faceX(BUTTON_FIRST_X_PX + 4 * BUTTON_PITCH_PX + BUTTON_W_PX / 2f);
            float cy = faceY(BUTTON_TOP_PX + BUTTON_H_PX / 2f);
            poseStack.pushPose();
            poseStack.translate(cx, cy, FACE_Z - units(0.25f) - 0.004f);
            sink.emit(SoundstageRenderTypes.eyes(HALO_TEXTURE), (pose, glow) ->
                    quad(pose, glow, -pxLength(INDICATOR_GLOW_PX * 1.3f), pxLength(INDICATOR_GLOW_PX * 1.3f),
                            0xE0, 0x90, 0x30, 255, light, packedOverlay));
            poseStack.popPose();
        }
    }

    private void renderMeterBacklight(PoseStack poseStack, GeometrySink sink, int packedOverlay) {
        int light = 0x00F000F0;
        for (float meterX : new float[]{LEFT_METER_X, RIGHT_METER_X}) {
            float cx = faceX(meterX + METER_W / 2f), cy = faceY(METER_Y + METER_H / 2f);
            poseStack.pushPose();
            poseStack.translate(cx, cy, METER_BACKLIGHT_Z);
            sink.emit(SoundstageRenderTypes.eyes(HALO_TEXTURE), (pose, glow) ->
                    quad(pose, glow, -pxLength(METER_W * 0.62f), pxLength(METER_H * 0.62f),
                            METER_LAMP_R, METER_LAMP_G, METER_LAMP_B, 255, light, packedOverlay));
            poseStack.popPose();
        }
    }

    private static final float METER_BACKLIGHT_Z = at(0.96f);
    private static final int METER_LAMP_R = 0x70, METER_LAMP_G = 0x50, METER_LAMP_B = 0x26;

    private static final float INDICATOR_GLOW_PX = 7.0f;

    private void renderSignalLamp(PoseStack.Pose pose, VertexConsumer consumer, boolean signal,
                                  int packedLight, int packedOverlay) {
        float cx = faceX(BUTTON_FIRST_X_PX + 4 * BUTTON_PITCH_PX + BUTTON_W_PX / 2f);
        float cy = faceY(BUTTON_TOP_PX + BUTTON_H_PX / 2f);
        float lensFront = FACE_Z - units(0.25f);
        Mesh.emitOffsetDisc(pose, consumer, cx, cy, pxLength(5.2f), FACE_Z - 0.001f, 0x121314, -1, packedLight,
                packedOverlay);
        Mesh.emitOffsetCylinderShell(pose, consumer, cx, cy, pxLength(3.6f), lensFront, FACE_Z,
                0x2A2B2D, packedLight, packedOverlay);
        int lens = signal ? 0xFFB347 : 0x4A3A26;
        int light = signal ? 0x00F000F0 : packedLight;
        Mesh.emitOffsetDisc(pose, consumer, cx, cy, pxLength(3.6f), lensFront, lens, -1, light, packedOverlay);
        Mesh.emitOffsetDisc(pose, consumer, cx - pxLength(1.0f), cy + pxLength(1.0f), pxLength(1.0f),
                lensFront - 0.001f, signal ? 0xFFE6B8 : 0x6B5A44, -1, light, packedOverlay);
    }

    private void renderTubeGlow(PoseStack poseStack, GeometrySink sink,
                                ClientAudioReceiver receiver, float baseYaw, int packedOverlay) {
        float glow;
        float redness;
        if (receiver == null) {
            glow = TubeThermalModel.FILAMENT_GLOW;
            redness = 0f;
        } else {
            glow = Mth.clamp(receiver.tubeGlow(), 0f, 1f);
            redness = Mth.clamp(receiver.tubeRedness(), 0f, 1f);
        }
        if (glow <= 0f) return;

        int r = (int) Mth.lerp(redness, GLOW_WARM_R, GLOW_HOT_R);
        int g = (int) Mth.lerp(redness, GLOW_WARM_G, GLOW_HOT_G);
        int b = (int) Mth.lerp(redness, GLOW_WARM_B, GLOW_HOT_B);
        int light = 0x00F000F0;

        float core = glow * BACKLIGHT_GAIN;
        BACKLIGHT_MESH.renderBlended(new BacklightKey(packedOverlay),
                scale(r, core) / 255f, scale(g, core) / 255f, scale(b, core) / 255f,
                poseStack, sink,
                (scratch, backSink) -> backSink.emit(SoundstageRenderTypes.eyes(NEEDLE_TEXTURE), (pose, consumer) ->
                        AmplifierChassisRenderer.backlight(pose, consumer, FILAMENT_BOTTOM, FILAMENT_TOP,
                                FILAMENT_RADIUS, 255, 255, 255, light, packedOverlay)));

        float poolHalf = units(POOL_HALF);
        float pool = glow * POOL_GAIN;
        for (float centerX : TUBE_CENTERS_X) {
            poseStack.pushPose();
            poseStack.translate(at(centerX), at(POOL_HEIGHT), at(TUBE_CENTER_Z));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            int pr = scale(r, pool), pg = scale(g, pool), pb = scale(b, pool);
            POOL_MESH.renderBlended(glowKey(packedOverlay), pr / 255f, pg / 255f, pb / 255f, poseStack, sink,
                    (scratch, poolSink) -> poolSink.emit(SoundstageRenderTypes.eyes(HALO_TEXTURE), (pose, poolBuffer) ->
                            quad(pose, poolBuffer, poolHalf, poolHalf, 255, 255, 255, 255, light, packedOverlay)));
            poseStack.popPose();
        }

        float halo = glow * HALO_GAIN;
        //? if >=1.21.11 {
        /*var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        *///?} else {
        var camera = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        //?}
        for (float centerX : TUBE_CENTERS_X) {
            poseStack.pushPose();
            poseStack.translate(at(centerX), at(HALO_CENTER_Y), at(TUBE_CENTER_Z));
            poseStack.mulPose(Axis.YP.rotationDegrees(-baseYaw));
            poseStack.mulPose(camera);
            //? if <1.21 {
            /*poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            *///?}
            int hr = scale(r, halo), hg = scale(g, halo), hb = scale(b, halo);
            HALO_MESH.renderBlended(glowKey(packedOverlay), hr / 255f, hg / 255f, hb / 255f, poseStack, sink,
                    (scratch, haloSink) -> haloSink.emit(SoundstageRenderTypes.eyes(HALO_TEXTURE), (pose, haloBuffer) ->
                            quad(pose, haloBuffer, units(HALO_HALF_WIDTH), units(HALO_HALF_HEIGHT),
                                    255, 255, 255, 255, light, packedOverlay)));
            poseStack.popPose();
        }
    }

    private static int scale(int channel, float gain) {
        return Mth.clamp((int) (channel * gain), 0, 255);
    }

    private void renderTubeGlass(PoseStack poseStack, GeometrySink sink,
                                 int packedLight, int packedOverlay) {
        GLASS_MESH.renderBlended(new GlassKey(packedLight, packedOverlay), poseStack, sink,
                (scratch, glassSink) -> glassSink.emit(SoundstageRenderTypes.glass(TUBE_TEXTURE), (pose, consumer) ->
                        AmplifierChassisRenderer.glass(pose, consumer, packedLight, packedOverlay)));
    }

    private void quad(PoseStack.Pose pose, VertexConsumer consumer, float halfWidth, float halfHeight,
                      int r, int g, int b, int alpha, int light, int overlay) {
        glowVertex(pose, consumer, -halfWidth, -halfHeight, 0f, 0f, 1f, r, g, b, alpha, light, overlay);
        glowVertex(pose, consumer, halfWidth, -halfHeight, 0f, 1f, 1f, r, g, b, alpha, light, overlay);
        glowVertex(pose, consumer, halfWidth, halfHeight, 0f, 1f, 0f, r, g, b, alpha, light, overlay);
        glowVertex(pose, consumer, -halfWidth, halfHeight, 0f, 0f, 0f, r, g, b, alpha, light, overlay);
    }

    private void glowVertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
                            float u, float v, int r, int g, int b, int alpha, int light, int overlay) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                ;
                Mesh.applyNormal(consumer, pose, 0, 0, -1);
    }

    private void emitAt(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
                        int r, int g, int b, int packedLight, int packedOverlay) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, 255)
                .setUv(0.5f, 0.5f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                ;
                Mesh.applyNormal(consumer, pose, 0, 0, -1);
    }

    private void emit(PoseStack.Pose pose, VertexConsumer consumer, float x, float y,
                      int packedLight, int packedOverlay) {
        consumer.addVertex(pose.pose(), x, y, NEEDLE_Z)
                .setColor(NEEDLE_R, NEEDLE_G, NEEDLE_B, 255)
                .setUv(0.5f, 0.5f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                ;
                Mesh.applyNormal(consumer, pose, 0, 0, -1);
    }
}
