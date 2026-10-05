package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nachtraben.soundstage.core.WooferKinematics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;

final class WooferRenderer {
    private WooferRenderer() {}

    private static final float FLANGE_BACK_Z = -0.372f;

    private static final int PLATE_RGB = 0x4A5159;
    private static final int FERRITE_RGB = 0x2A2C30;
    private static final int BOOT_RGB = 0x141517;
    private static final int BADGE_RGB = 0xB8BDC3;
    private static final int BASKET_RGB = 0x343B43;
    private static final int SPIDER_LIT_RGB = 0xD2A673;
    private static final int SPIDER_SHADE_RGB = 0x86592F;
    private static final int SPIDER_BACK_LIT_RGB = 0xB98B5A;
    private static final int SPIDER_BACK_SHADE_RGB = 0x5E3D24;
    private static final int COPPER_RGB = 0xC66B2D;
    private static final int TINSEL_RGB = 0xD89A5A;

    static float driverScale(float widthUnits) {
        return (widthUnits / 32f) / WooferKinematics.FLANGE_OUTER_HALF;
    }

    static float openingHalfUnits(float widthUnits) {
        return widthUnits / 2f * WooferKinematics.FRAME_OPENING_HALF / WooferKinematics.FLANGE_OUTER_HALF;
    }

    static float coneReachUnits(float widthUnits) {
        float frontmost = WooferKinematics.atExcursion(1.0f).coneOuterZ();
        return (FLANGE_BACK_Z - frontmost) * 16f * driverScale(widthUnits);
    }

    private static final WooferKinematics.Pose AT_REST = WooferKinematics.atExcursion(0.0f);

    static void renderFrontStatic(float widthUnits, float centerX, float centerY, float baffleZ,
                                  boolean interior, PoseStack poseStack, GeometrySink sink,
                                  int light, int overlay) {
        float scale = driverScale(widthUnits);
        poseStack.pushPose();
        poseStack.translate(CabinetRenderer.at(centerX), CabinetRenderer.at(centerY),
                CabinetRenderer.at(baffleZ) - FLANGE_BACK_Z * scale);
        poseStack.scale(scale, scale, scale);
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) -> {
            renderMountingRing(pose, consumer, AT_REST.frameZ(), light, overlay);
            if (interior) {
                renderBasket(pose, consumer, light, overlay);
                renderMotor(pose, consumer, AT_REST, light, overlay);
            }
        }));
        poseStack.popPose();
    }

    static void renderFrontDynamic(float excursion, float widthUnits, float centerX, float centerY,
                                   float baffleZ, boolean interior, PoseStack poseStack,
                                   GeometrySink sink, int light, int overlay) {
        WooferKinematics.Pose woofer = WooferKinematics.atExcursion(excursion);
        float scale = driverScale(widthUnits);
        poseStack.pushPose();
        poseStack.translate(CabinetRenderer.at(centerX), CabinetRenderer.at(centerY),
                CabinetRenderer.at(baffleZ) - FLANGE_BACK_Z * scale);
        poseStack.scale(scale, scale, scale);
        emitMovingParts(poseStack, sink, woofer, interior, light, overlay);
        poseStack.popPose();
    }

    static void renderMountingRing(PoseStack.Pose pose, VertexConsumer consumer, float z,
                                   int light, int overlay) {
        int segments = 32;
        float back = FLANGE_BACK_Z;
        float outerHalf = WooferKinematics.FLANGE_OUTER_HALF;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segments);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
            Mesh.Point2 outer0 = Mesh.roundedPoint(a0, outerHalf);
            Mesh.Point2 outer1 = Mesh.roundedPoint(a1, outerHalf);
            Mesh.Point2 inner0 = Mesh.roundedPoint(a0, WooferKinematics.FRAME_OPENING_HALF);
            Mesh.Point2 inner1 = Mesh.roundedPoint(a1, WooferKinematics.FRAME_OPENING_HALF);
            Mesh.emitQuad(pose, consumer,
                    inner0.x(), inner0.y(), z,
                    outer0.x(), outer0.y(), z,
                    outer1.x(), outer1.y(), z,
                    inner1.x(), inner1.y(), z,
                    0x30363D, 255, 0, 0, -1, light, overlay);
            Mesh.emitQuad(pose, consumer,
                    outer0.x(), outer0.y(), back,
                    inner0.x(), inner0.y(), back,
                    inner1.x(), inner1.y(), back,
                    outer1.x(), outer1.y(), back,
                    0x252A30, 255, 0, 0, 1, light, overlay);
            Mesh.emitQuad(pose, consumer,
                    outer0.x(), outer0.y(), z,
                    outer0.x(), outer0.y(), back,
                    outer1.x(), outer1.y(), back,
                    outer1.x(), outer1.y(), z,
                    0x3B424A, 255, 0, 0, -1, light, overlay);
            Mesh.emitQuad(pose, consumer,
                    inner1.x(), inner1.y(), z,
                    inner1.x(), inner1.y(), back,
                    inner0.x(), inner0.y(), back,
                    inner0.x(), inner0.y(), z,
                    0x1E2328, 255, 0, 0, 1, light, overlay);
        }
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.PI * 2.0 * (i + 0.5) / 8.0);
            Mesh.Point2 screw = Mesh.roundedPoint(angle, 0.458f);
            Mesh.emitOffsetCylinderShell(pose, consumer, screw.x(), screw.y(), 0.014f,
                    z - 0.010f, z - 0.002f, 0x8B949E, light, overlay);
            Mesh.emitOffsetDisc(pose, consumer, screw.x(), screw.y(), 0.014f,
                    z - 0.011f, 0x1C2025, -1, light, overlay);
        }
    }

    static void renderBasket(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay) {
        WooferKinematics.Pose rest = WooferKinematics.atExcursion(0);
        float landingZ = rest.spiderOuterZ() - 0.002f;
        float plateFront = rest.magnetFrontZ() + WooferKinematics.TOP_PLATE_FRONT_OFFSET;
        float landingDiagonal = (WooferKinematics.SPIDER_OUTER_RADIUS + 0.010f) * Mth.SQRT_OF_TWO / 2f;
        float plateDiagonal = (WooferKinematics.PLATE_RADIUS - 0.012f) * Mth.SQRT_OF_TWO / 2f;
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                float rx = sx * Mth.SQRT_OF_TWO / 2f, ry = sy * Mth.SQRT_OF_TWO / 2f;
                Mesh.emitTaperedBeam(pose, consumer,
                        sx * 0.345f, sy * 0.345f, FLANGE_BACK_Z, 0.090f, 0.030f,
                        sx * landingDiagonal, sy * landingDiagonal, landingZ, 0.072f, 0.034f,
                        rx, ry, BASKET_RGB, light, overlay);
                Mesh.emitTaperedBeam(pose, consumer,
                        sx * landingDiagonal, sy * landingDiagonal, landingZ, 0.072f, 0.034f,
                        sx * plateDiagonal, sy * plateDiagonal, plateFront + 0.004f, 0.060f, 0.034f,
                        rx, ry, BASKET_RGB, light, overlay);
            }
        }
        Mesh.renderRoundedAnnulus(pose, consumer, 0.400f, 0.430f, FLANGE_BACK_Z + 0.001f,
                BASKET_RGB, 1, light, overlay);

        float landingOuter = WooferKinematics.SPIDER_OUTER_RADIUS + 0.018f;
        Mesh.emitCylinderShell(pose, consumer, landingOuter, landingZ - 0.014f, landingZ + 0.016f,
                BASKET_RGB, light, overlay);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.SPIDER_LANDING_INNER_RADIUS, landingOuter,
                landingZ - 0.014f, BASKET_RGB, -1, light, overlay);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.SPIDER_LANDING_INNER_RADIUS, landingOuter,
                landingZ + 0.016f, BASKET_RGB, 1, light, overlay);
    }

    static void renderMotor(PoseStack.Pose pose, VertexConsumer consumer,
                            WooferKinematics.Pose woofer, int light, int overlay) {
        float plate = WooferKinematics.PLATE_RADIUS;
        float magnet = WooferKinematics.MAGNET_RADIUS;
        float topFront = woofer.magnetFrontZ() + WooferKinematics.TOP_PLATE_FRONT_OFFSET;
        float topBack = woofer.magnetFrontZ() + WooferKinematics.TOP_PLATE_BACK_OFFSET;
        float rear = woofer.magnetBackZ();

        Mesh.emitCylinderShell(pose, consumer, plate, topFront, topBack, PLATE_RGB, light, overlay);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.TOP_PLATE_GAP_RADIUS, plate, topFront,
                PLATE_RGB, -1, light, overlay);
        Mesh.emitCylinderShell(pose, consumer, WooferKinematics.TOP_PLATE_GAP_RADIUS, topFront, topBack,
                0x23272C, light, overlay);
        Mesh.emitAnnulus(pose, consumer, magnet, plate, topBack, PLATE_RGB, 1, light, overlay);

        float spacerFront = topBack + 0.090f, spacerBack = spacerFront + 0.025f;
        float backPlateFront = spacerBack + 0.090f, backPlateBack = backPlateFront + 0.030f;
        Mesh.emitCylinderShell(pose, consumer, magnet, topBack, spacerFront, FERRITE_RGB, light, overlay);
        Mesh.emitCylinderShell(pose, consumer, plate, spacerFront, spacerBack, PLATE_RGB, light, overlay);
        Mesh.emitAnnulus(pose, consumer, magnet, plate, spacerFront, PLATE_RGB, -1, light, overlay);
        Mesh.emitAnnulus(pose, consumer, magnet, plate, spacerBack, PLATE_RGB, 1, light, overlay);
        Mesh.emitCylinderShell(pose, consumer, magnet, spacerBack, backPlateFront, FERRITE_RGB, light, overlay);
        Mesh.emitCylinderShell(pose, consumer, plate, backPlateFront, backPlateBack, PLATE_RGB, light, overlay);
        Mesh.emitAnnulus(pose, consumer, magnet, plate, backPlateFront, PLATE_RGB, -1, light, overlay);
        float boot = plate - 0.030f;
        Mesh.emitAnnulus(pose, consumer, boot, plate, backPlateBack, PLATE_RGB, 1, light, overlay);

        Mesh.emitCylinderShell(pose, consumer, boot, backPlateBack - 0.004f, rear, BOOT_RGB, light, overlay);
        Mesh.emitAnnulus(pose, consumer, 0.070f, boot, rear, BOOT_RGB, 1, light, overlay);
        Mesh.emitAnnulus(pose, consumer, 0.200f, 0.235f, rear + 0.002f, BADGE_RGB, 1, light, overlay);
        Mesh.emitOffsetCylinderShell(pose, consumer, 0, 0, 0.070f, rear - 0.040f, rear, 0x050607, light, overlay);
        Mesh.emitOffsetDisc(pose, consumer, 0, 0, 0.070f, rear - 0.040f, 0x050607, 1, light, overlay);

        float poleFront = topFront - 0.008f;
        Mesh.emitCylinderShell(pose, consumer, WooferKinematics.POLE_RADIUS, poleFront, topBack,
                0x626A73, light, overlay);
        Mesh.emitAnnulus(pose, consumer, 0.045f, WooferKinematics.POLE_RADIUS, poleFront,
                0x626A73, -1, light, overlay);
        Mesh.emitOffsetDisc(pose, consumer, 0, 0, 0.045f, poleFront + 0.030f, 0x050607, -1, light, overlay);
        Mesh.emitOffsetCylinderShell(pose, consumer, 0, 0, 0.045f, poleFront, poleFront + 0.030f,
                0x050607, light, overlay);
    }

    private record AssemblyKey(boolean interior, boolean sealed, int light, int overlay) {}

    private static final float[] DIAPHRAGM_WEIGHTS = diaphragmWeights();

    private static float[] diaphragmWeights() {
        float[] weights = new float[wooferProfileRadii().length];
        weights[0] = 0f;
        weights[1] = 0.33f;
        weights[2] = 0.67f;
        java.util.Arrays.fill(weights, RIGID_PROFILE_START, weights.length, 1f);
        return weights;
    }

    private static final StaticMesh ASSEMBLY_MESH = new StaticMesh(16);

    private static void emitMovingAssembly(PoseStack poseStack, GeometrySink sink,
                                           WooferKinematics.Pose woofer, boolean interior,
                                           int light, int overlay) {
        ASSEMBLY_MESH.renderDisplaced(new AssemblyKey(interior, !interior, light, overlay),
                woofer.displacement(), poseStack, sink,
                (scratch, rest) -> emitAssembly(rest, AT_REST, interior, true, light, overlay),
                (scratch, live) -> emitAssembly(live, woofer, interior, false, light, overlay));
    }

    private static void emitAssembly(GeometrySink sink, WooferKinematics.Pose woofer, boolean interior,
                                     boolean weighted, int light, int overlay) {
        RenderType type = weighted ? SoundstageRenderTypes.spider(TextureAtlas.LOCATION_BLOCKS)
                : AtlasSprites.CUTOUT;
        float[] weights = weighted ? DIAPHRAGM_WEIGHTS : null;
        sink.emit(type, AtlasSprites.cone((pose, consumer) ->
                renderWooferDiaphragm(pose, consumer, woofer, weights, light, overlay)));
        sink.emit(type, AtlasSprites.white((pose, consumer) -> {
            renderWooferBacking(pose, consumer, woofer, !interior, weights, light, overlay);
            if (interior) {
                renderSpider(pose, consumer, woofer, weighted, light, overlay);
                renderRigidMechanism(pose, consumer, woofer, weighted, light, overlay);
            }
        }));
    }

    private static void renderRigidMechanism(PoseStack.Pose pose, VertexConsumer consumer,
                                             WooferKinematics.Pose woofer, boolean weighted,
                                             int light, int overlay) {
        int ov = weighted ? Mesh.weightOverlay(1f) : overlay;
        Mesh.emitCylinderShell(pose, consumer, WooferKinematics.FORMER_RADIUS, woofer.formerFrontZ(),
                woofer.formerBackZ(), 0x22262B, light, ov);
        Mesh.emitCylinderShell(pose, consumer, WooferKinematics.COIL_RADIUS, woofer.coilFrontZ(),
                woofer.coilBackZ(), COPPER_RGB, light, ov);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.FORMER_RADIUS, WooferKinematics.COIL_RADIUS,
                woofer.coilFrontZ(), COPPER_RGB, -1, light, ov);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.FORMER_RADIUS, WooferKinematics.COIL_RADIUS,
                woofer.coilBackZ(), COPPER_RGB, 1, light, ov);
        renderMovingJoints(pose, consumer, woofer, light, ov);
    }

    private static void emitMovingParts(PoseStack poseStack, GeometrySink sink,
                                        WooferKinematics.Pose woofer, boolean interior,
                                        int light, int overlay) {
        if (interior) {
            sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) ->
                    renderTinselLeads(pose, consumer, woofer, light, overlay)));
        }
        emitMovingAssembly(poseStack, sink, woofer, interior, light, overlay);
    }

    static void renderSpider(PoseStack.Pose pose, VertexConsumer consumer,
                             WooferKinematics.Pose woofer, boolean weighted, int light, int overlay) {
        int rings = 48;
        float folds = WooferKinematics.SPIDER_FOLDS;
        float amplitude = WooferKinematics.SPIDER_FOLD_AMPLITUDE;
        float[] radii = new float[rings + 1];
        float[] depths = new float[rings + 1];
        for (int i = 0; i <= rings; i++) {
            float t = (float) i / rings;
            radii[i] = Mth.lerp(t, WooferKinematics.SPIDER_INNER_RADIUS, WooferKinematics.SPIDER_OUTER_RADIUS);
            float envelope = Mth.sqrt(Mth.sin(t * (float) Math.PI));
            depths[i] = Mth.lerp(t, woofer.spiderInnerZ(), woofer.spiderOuterZ())
                    + Mth.sin(t * (float) Math.PI * 2.0f * folds) * amplitude * envelope;
        }
        for (int i = 0; i < rings; i++) {
            boolean facesViewer = depths[i + 1] >= depths[i];
            float[] r = {radii[i], radii[i + 1]};
            int ovA = weighted ? Mesh.weightOverlay(1f - (float) i / rings) : overlay;
            int ovB = weighted ? Mesh.weightOverlay(1f - (float) (i + 1) / rings) : overlay;
            Mesh.emitCircularSurface(pose, consumer, r, new float[]{depths[i], depths[i + 1]},
                    facesViewer ? SPIDER_LIT_RGB : SPIDER_SHADE_RGB, 255, light, ovA, ovB);
            Mesh.emitCircularSurface(pose, consumer, r, new float[]{depths[i] + 0.004f, depths[i + 1] + 0.004f},
                    facesViewer ? SPIDER_BACK_SHADE_RGB : SPIDER_BACK_LIT_RGB, 255, light, ovA, ovB);
        }
    }

    static void renderMovingJoints(PoseStack.Pose pose, VertexConsumer consumer,
                                   WooferKinematics.Pose woofer, int light, int overlay) {
        float collar = WooferKinematics.SPIDER_COLLAR_OUTER_RADIUS;
        Mesh.emitCylinderShell(pose, consumer, collar,
                woofer.spiderInnerZ() - 0.014f, woofer.spiderInnerZ() + 0.014f,
                0x35261C, light, overlay);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.FORMER_RADIUS, collar,
                woofer.spiderInnerZ() - 0.014f, 0x5B3B25, -1, light, overlay);
        Mesh.emitAnnulus(pose, consumer, WooferKinematics.FORMER_RADIUS, collar,
                woofer.spiderInnerZ() + 0.014f, 0x35261C, 1, light, overlay);

        Mesh.emitCircularSurface(pose, consumer,
                new float[]{WooferKinematics.CONE_THROAT_RADIUS, 0.142f, WooferKinematics.FORMER_RADIUS},
                new float[]{woofer.coneInnerZ(), woofer.formerFrontZ() - 0.004f, woofer.formerFrontZ() + 0.012f},
                0x252A2E, 255, light, overlay);
    }

    static void renderTinselLeads(PoseStack.Pose pose, VertexConsumer consumer,
                                  WooferKinematics.Pose woofer, int light, int overlay) {
        for (float degrees : new float[]{-62f, -118f}) {
            float a = (float) Math.toRadians(degrees);
            float cx = Mth.cos(a), sy = Mth.sin(a);
            float inner = WooferKinematics.COIL_RADIUS + 0.004f;
            float outer = WooferKinematics.SPIDER_OUTER_RADIUS + 0.010f;
            float startZ = woofer.spiderInnerZ() - 0.020f;
            float endZ = woofer.spiderOuterZ() - 0.030f;
            float midR = Mth.lerp(0.5f, inner, outer);
            float midZ = Math.min(startZ, endZ) - 0.028f;
            Mesh.emitTaperedBeam(pose, consumer, cx * inner, sy * inner, startZ, 0.005f, 0.004f,
                    cx * midR, sy * midR, midZ, 0.005f, 0.004f, cx, sy, TINSEL_RGB, light, overlay);
            Mesh.emitTaperedBeam(pose, consumer, cx * midR, sy * midR, midZ, 0.005f, 0.004f,
                    cx * outer, sy * outer, endZ, 0.005f, 0.004f, cx, sy, TINSEL_RGB, light, overlay);
            Mesh.emitBox(pose, consumer, cx * outer - 0.016f, sy * outer - 0.016f, endZ - 0.006f,
                    cx * outer + 0.016f, sy * outer + 0.016f, endZ + 0.010f, 0xC9A64B, light, overlay);
        }
    }

    private static final int RIGID_PROFILE_START = 3;

    static void renderWooferDiaphragm(PoseStack.Pose pose, VertexConsumer consumer,
                                      WooferKinematics.Pose woofer, float[] weights,
                                      int light, int overlay) {
        emitRoundedSurface(pose, consumer, wooferProfileRadii(), wooferProfileDepths(woofer, 0),
                0xFFFFFF, 255, true, 0, light, overlay, weights);
    }

    static void renderWooferBacking(PoseStack.Pose pose, VertexConsumer consumer,
                                    WooferKinematics.Pose woofer, boolean sealed, float[] weights,
                                    int light, int overlay) {
        float[] radii = wooferProfileRadii();
        float[] depths = wooferProfileDepths(woofer, WooferKinematics.DIAPHRAGM_THICKNESS);
        int last = sealed ? 1 : radii.length - 1;
        emitRoundedSurface(pose, consumer,
                java.util.Arrays.copyOfRange(radii, 0, last + 1),
                java.util.Arrays.copyOfRange(depths, 0, last + 1),
                0x12161A, 255, false, 0, light, overlay,
                weights == null ? null : java.util.Arrays.copyOfRange(weights, 0, last + 1));
    }

    static float[] wooferProfileRadii() {
        return new float[]{WooferKinematics.SURROUND_OUTER_HALF, 0.408f, 0.375f, WooferKinematics.CONE_OUTER_RADIUS,
                0.292f, 0.235f, WooferKinematics.CONE_THROAT_RADIUS,
                0.130f, 0.085f, 0.035f, 0.0f};
    }

    static float[] wooferProfileDepths(WooferKinematics.Pose woofer, float offset) {
        float surroundMid1 = Mth.lerp(0.33f, woofer.surroundOuterZ(), woofer.surroundInnerZ()) - 0.025f;
        float surroundMid2 = Mth.lerp(0.67f, woofer.surroundOuterZ(), woofer.surroundInnerZ()) - 0.025f;
        return new float[]{woofer.surroundOuterZ() + offset, surroundMid1 + offset,
                surroundMid2 + offset, woofer.coneOuterZ() + offset,
                Mth.lerp(0.33f, woofer.coneOuterZ(), woofer.coneInnerZ()) + offset,
                Mth.lerp(0.67f, woofer.coneOuterZ(), woofer.coneInnerZ()) + offset,
                woofer.coneInnerZ() + offset,
                Mth.lerp(0.35f, woofer.coneInnerZ(), woofer.dustCenterZ()) + offset,
                Mth.lerp(0.68f, woofer.coneInnerZ(), woofer.dustCenterZ()) + offset,
                Mth.lerp(0.90f, woofer.coneInnerZ(), woofer.dustCenterZ()) + offset,
                woofer.dustCenterZ() + offset};
    }

    static void emitRoundedSurface(PoseStack.Pose pose, VertexConsumer consumer,
                                   float[] radii, float[] depths, int rgb, int alpha,
                                   boolean cornerRibs, int light, int overlay) {
        emitRoundedSurface(pose, consumer, radii, depths, rgb, alpha, cornerRibs, 0, light, overlay);
    }

    static void emitRoundedSurface(PoseStack.Pose pose, VertexConsumer consumer,
                                   float[] radii, float[] depths, int rgb, int alpha,
                                   boolean cornerRibs, int ringOffset, int light, int overlay) {
        emitRoundedSurface(pose, consumer, radii, depths, rgb, alpha, cornerRibs, ringOffset,
                light, overlay, null);
    }

    static void emitRoundedSurface(PoseStack.Pose pose, VertexConsumer consumer,
                                   float[] radii, float[] depths, int rgb, int alpha,
                                   boolean cornerRibs, int ringOffset, int light, int overlay,
                                   float[] ringWeights) {
        int segments = 128;
        Mesh.Point2[] outline = new Mesh.Point2[segments + 1];
        for (int i = 0; i <= segments; i++) {
            outline[i] = Mesh.unitRoundedPoint((float) (Math.PI * 2.0 * i / segments));
        }
        org.joml.Vector3f normal = new org.joml.Vector3f();
        for (int ring = 0; ring < radii.length - 1; ring++) {
            float r0 = radii[ring], r1 = radii[ring + 1];
            float t0 = textureRadius(r0);
            float t1 = textureRadius(r1);
            for (int segment = 0; segment < segments; segment++) {
                float a0 = (float) (Math.PI * 2.0 * segment / segments);
                float a1 = (float) (Math.PI * 2.0 * (segment + 1) / segments);
                Mesh.Point2 o0 = outline[segment], o1 = outline[segment + 1];
                float o0x = o0.x(), o0y = o0.y(), o1x = o1.x(), o1y = o1.y();
                int outer = ring + ringOffset;
                float z00 = depths[ring] + (cornerRibs ? cornerRibOffset(a0, outer) : 0);
                float z01 = depths[ring] + (cornerRibs ? cornerRibOffset(a1, outer) : 0);
                float z10 = depths[ring + 1] + (cornerRibs ? cornerRibOffset(a0, outer + 1) : 0);
                float z11 = depths[ring + 1] + (cornerRibs ? cornerRibOffset(a1, outer + 1) : 0);
                float mid = (a0 + a1) * 0.5f;
                pose.transformNormal(Mth.cos(mid) * 0.35f, Mth.sin(mid) * 0.35f, -0.94f, normal);
                float nx = normal.x(), ny = normal.y(), nz = normal.z();
                int ovA = ringWeights == null ? overlay : Mesh.weightOverlay(ringWeights[ring]);
                int ovB = ringWeights == null ? overlay : Mesh.weightOverlay(ringWeights[ring + 1]);
                Mesh.addTintedVertex(consumer, pose, o0x * r0, o0y * r0, z00,
                        0.5f + o0x * t0, 0.5f - o0y * t0, rgb, alpha, nx, ny, nz, light, ovA);
                Mesh.addTintedVertex(consumer, pose, o0x * r1, o0y * r1, z10,
                        0.5f + o0x * t1, 0.5f - o0y * t1, rgb, alpha, nx, ny, nz, light, ovB);
                Mesh.addTintedVertex(consumer, pose, o1x * r1, o1y * r1, z11,
                        0.5f + o1x * t1, 0.5f - o1y * t1, rgb, alpha, nx, ny, nz, light, ovB);
                Mesh.addTintedVertex(consumer, pose, o1x * r0, o1y * r0, z01,
                        0.5f + o1x * t0, 0.5f - o1y * t0, rgb, alpha, nx, ny, nz, light, ovA);
            }
        }
    }

    private static float textureRadius(float radius) {
        float throat = WooferKinematics.CONE_THROAT_RADIUS;
        if (radius <= throat) {
            return radius / throat * 0.25f;
        }
        return 0.25f + (radius - throat) / (WooferKinematics.SURROUND_OUTER_HALF - throat) * 0.1875f;
    }

    static float cornerRibOffset(float angle, int ring) {
        if (ring > 3) return 0;
        float normalized = angle / ((float) Math.PI * 2.0f);
        normalized -= Mth.floor(normalized);
        float withinQuadrant = normalized * 4.0f;
        withinQuadrant -= Mth.floor(withinQuadrant);
        float halfCorner = 0.10f;
        float fromCornerStart = withinQuadrant - (0.5f - halfCorner);
        if (fromCornerStart < 0 || fromCornerStart > 2 * halfCorner) return 0;
        float cornerProgress = fromCornerStart / (2 * halfCorner);
        float ridge = Math.max(0, Mth.sin(cornerProgress * (float) Math.PI * 10.0f));
        float radialEnvelope = (ring == 1 || ring == 2) ? 1.0f : 0.25f;
        return -0.009f * ridge * radialEnvelope;
    }
}
