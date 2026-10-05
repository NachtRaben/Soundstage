package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.PartyBoxBlockEntity;
import com.nachtraben.soundstage.core.SpeakerGeometry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class PartyBoxRenderer {
    private PartyBoxRenderer() {}

    static final CabinetRenderer.Layout SLAB = new CabinetRenderer.Layout(2, 0, 2, 14, 16, 6, 1, 0.5f, 0.6f);
    private static final float BODY_X0 = 2, BODY_X1 = 14, BODY_Y1 = 14, BODY_Z0 = 6, BODY_Z1 = 14;

    private static final DriverRenderer.Driver TWEETER =
            new DriverRenderer.Driver(DriverRenderer.Kind.TWEETER, 8, 13.2f, 1.5f);
    private static final DriverRenderer.Driver WOOFER =
            new DriverRenderer.Driver(DriverRenderer.Kind.WOOFER, 8, 6.0f,
                    SpeakerGeometry.PARTY_BOX_WOOFER_WIDTH_PX);
    static final CabinetRenderer.Hole[] HOLES = {TWEETER.hole(), WOOFER.hole()};
    static final DriverRenderer.Driver[] DRIVERS = {TWEETER, WOOFER};

    private static final int PIPE_SEGMENTS = 64;
    private static final int PIPE_QUAD_STEPS = 2;
    private static final float PIPE_CENTER_X = 8f, PIPE_CENTER_Y = 6.0f;
    private static final float PIPE_HALF = 4.55f;
    private static final float PIPE_CORNER = 1.6f;
    private static final float PIPE_THICKNESS = 0.32f;
    private static final float PIPE_UNLIT_VALUE = 0.18f;
    private static final float BLOOM_HALF = PIPE_THICKNESS * 1.9f;
    private static final float BLOOM_GAIN = 0.85f;
    private static final float SPILL_HALF = 1.3f;
    private static final float SPILL_GAIN = 0.3f;
    private static final int GLOW_TAPER_PAIRS = 4;
    private static final float GLOW_FLOOR_Y = 1.0f;
    private static final ResourceLocation PIPE_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/pipe_glow.png");
    private static final float PIPE_STRAIGHT = PIPE_HALF - PIPE_CORNER;
    private static final float PIPE_CORNER_ARC = (float) (Math.PI * PIPE_CORNER / 2.0);
    private static final float PIPE_PERIMETER = 8f * PIPE_STRAIGHT + 4f * PIPE_CORNER_ARC;

    private static final float DECK_Y = BODY_Y1;
    private static final int PANEL_RGB = 0x1C1D20;
    private static final int KNOB_RGB = 0x2B2D31;
    private static final int KNOB_RIB_RGB = 0x1E2023;
    private static final int POINTER_RGB = 0xE8E0C2;
    private static final int METER_SEGMENTS = 12;

    private record BoxStaticKey(com.nachtraben.soundstage.block.speaker.SpeakerFinish finish, boolean grille,
                                int light, int overlay) {}

    private record BoxIdleKey(com.nachtraben.soundstage.block.speaker.SpeakerFinish finish, boolean grille,
                              int light, int overlay) {}

    private record BoxQuietKey(com.nachtraben.soundstage.block.speaker.SpeakerFinish finish, boolean grille,
                               float volume, int loopMode, boolean shuffle, boolean locked,
                               int light, int overlay) {}

    private static final StaticMesh DECK_MESH = new StaticMesh(64);
    private static final StaticMesh DECK_GLOW_MESH = new StaticMesh(64);
    private static final StaticMesh PIPE_MESH = new StaticMesh(96);
    private static final StaticMesh PIPE_GLOW_MESH = new StaticMesh(96);

    private record DeckKey(float volume, int meterLit, boolean playing, int loopMode, boolean shuffle,
                           boolean locked, int light, int overlay) {}

    private record DeckGlowKey(int meterLit, boolean playing, int loopMode, boolean shuffle, boolean locked,
                               int overlay) {}

    private record PipeKey(int litPairs, float z, int light, int overlay) {}

    private record PipeGlowKey(int litPairs, float z, int overlay) {}

    static void render(PartyBoxBlockEntity box, boolean grille, float excursion, float level, float peak,
                       PoseStack poseStack, GeometrySink sink, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        var finish = box.getFinish();
        float baffleZ = SLAB.baffleZ();
        float volume = box.getVolume() / (float) IAudioReceiver.MAX_VOLUME;
        int loopMode = box.getLoopMode();
        boolean shuffle = box.isShuffle();
        boolean locked = box.isLocked() && !com.nachtraben.soundstage.RigStaging.fault("lock-unread");
        Boolean previewPlaying = RenderPreview.partyPlaying;
        boolean playing = previewPlaying != null ? previewPlaying : box.isPlaying();
        boolean clear = finish.isTransparent();
        float pipeZ = grille ? SLAB.grilleFrontZ() - 0.04f : baffleZ - 0.10f;
        float pipeFill = Math.max(level, peak * 0.82f);
        boolean quiet = excursion == 0.0f && litPairs(pipeFill) == 0
                && meterLit(level) == 0 && !playing;
        if (quiet) {
            box.renderCache.render(
                    new BoxQuietKey(finish, grille, volume, loopMode, shuffle, locked, light, overlay),
                    poseStack, sink,
                    (scratch, idleSink) -> {
                        renderStatic(finish, grille, scratch, idleSink, light, overlay);
                        WooferRenderer.renderFrontDynamic(0.0f, WOOFER.size(), WOOFER.x(), WOOFER.y(),
                                baffleZ, clear, scratch, idleSink, light, overlay);
                        idleSink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) ->
                                renderDeck(pose, consumer, volume, 0f, false, loopMode, shuffle, locked, light, overlay)));
                        emitPipeStrip(0, pipeZ, idleSink, light, overlay);
                    });
            renderDeckGlow(0f, false, loopMode, shuffle, locked, poseStack, sink, overlay);
            if (clear) {
                CabinetRenderer.renderClearCasing(SLAB, finish, sink, light, overlay);
                CabinetRenderer.renderWoodBox(finish, BODY_X0, 0, BODY_Z0, BODY_X1, BODY_Y1, BODY_Z1,
                        sink, light, overlay);
            }
            poseStack.popPose();
            return;
        }

        int meterLit = meterLit(level);
        DECK_MESH.render(new DeckKey(volume, meterLit, playing, loopMode, shuffle, locked, light, overlay),
                poseStack, sink, (scratch, deckSink) ->
                        deckSink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) ->
                                renderDeck(pose, consumer, volume, level, playing, loopMode, shuffle,
                                        locked, light, overlay))));
        renderDeckGlow(level, playing, loopMode, shuffle, locked, poseStack, sink, overlay);
        if (excursion == 0.0f) {
            box.renderCache.render(new BoxIdleKey(finish, grille, light, overlay), poseStack, sink,
                    (scratch, idleSink) -> {
                        renderStatic(finish, grille, scratch, idleSink, light, overlay);
                        WooferRenderer.renderFrontDynamic(0.0f, WOOFER.size(), WOOFER.x(), WOOFER.y(),
                                baffleZ, clear, scratch, idleSink, light, overlay);
                    });
        } else {
            box.renderCache.render(new BoxStaticKey(finish, grille, light, overlay), poseStack, sink,
                    (scratch, staticSink) -> renderStatic(finish, grille, scratch, staticSink, light, overlay));
            WooferRenderer.renderFrontDynamic(excursion, WOOFER.size(), WOOFER.x(), WOOFER.y(), baffleZ,
                    clear, poseStack, sink, light, overlay);
        }

        if (clear) {
            CabinetRenderer.renderClearCasing(SLAB, finish, sink, light, overlay);
            CabinetRenderer.renderWoodBox(finish, BODY_X0, 0, BODY_Z0, BODY_X1, BODY_Y1, BODY_Z1,
                    sink, light, overlay);
        }

        renderPipe(pipeFill, pipeZ, poseStack, sink, light, overlay);

        poseStack.popPose();
    }

    private static void renderStatic(com.nachtraben.soundstage.block.speaker.SpeakerFinish finish, boolean grille,
                                     PoseStack poseStack, GeometrySink sink, int light, int overlay) {
        CabinetRenderer.render(SLAB, finish, grille, HOLES, poseStack, sink, light, overlay);
        if (!finish.isTransparent()) {
            CabinetRenderer.renderWoodBox(finish, BODY_X0, 0, BODY_Z0, BODY_X1, BODY_Y1, BODY_Z1,
                    sink, light, overlay);
        }

        float baffleZ = SLAB.baffleZ();
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) -> {
            DriverRenderer.tweeter(TWEETER, baffleZ, pose, consumer, light, overlay);
            renderHandles(pose, consumer, light, overlay);
        }));
        WooferRenderer.renderFrontStatic(WOOFER.size(), WOOFER.x(), WOOFER.y(), baffleZ,
                finish.isTransparent(), poseStack, sink, light, overlay);
    }

    private static void deckKey(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, boolean latched,
                                float top, int light, int overlay) {
        float hx = 0.85f, hz = 0.6f;
        float rise = latched ? 0.10f : 0.22f;
        box(pose, c, cx - hx - 0.14f, top, cz - hz - 0.14f, cx + hx + 0.14f, top + 0.02f, cz + hz + 0.14f,
                0x151618, light, overlay);
        box(pose, c, cx - hx, top, cz - hz, cx + hx, top + rise, cz + hz,
                latched ? 0x5E6368 : 0x8C9197, light, overlay);
        box(pose, c, cx - hx + 0.16f, top + rise, cz - hz + 0.12f, cx + hx - 0.16f, top + rise + 0.02f,
                cz + hz - 0.12f, latched ? 0x9A9FA4 : 0xD6DADE, light, overlay);
        box(pose, c, cx - 0.36f, top + rise + 0.02f, cz + hz - 0.34f, cx + 0.36f, top + rise + 0.04f,
                cz + hz - 0.14f, latched ? 0x6CFF7A : 0x24402A,
                latched ? RenderLight.FULL_BRIGHT : light, overlay);
    }

    private static int meterLit(float level) {
        return Math.round(Mth.clamp(level, 0f, 1f) * METER_SEGMENTS);
    }

    private static int litPairs(float fill) {
        return Math.round(Mth.clamp(fill, 0f, 1f) * (PIPE_SEGMENTS / 2));
    }

    private static void renderPipe(float fill, float z, PoseStack poseStack, GeometrySink sink,
                                   int light, int overlay) {
        renderPipeStrip(fill, z, poseStack, sink, light, overlay);
        renderPipeGlow(fill, z, poseStack, sink, overlay);
    }

    private static void renderPipeStrip(float fill, float z, PoseStack poseStack, GeometrySink sink,
                                        int light, int overlay) {
        int litPairs = litPairs(fill);
        PIPE_MESH.render(new PipeKey(litPairs, z, light, overlay), poseStack, sink,
                (scratch, pipeSink) -> emitPipeStrip(litPairs, z, pipeSink, light, overlay));
    }

    private static void emitPipeStrip(int litPairs, float z, GeometrySink sink, int light, int overlay) {
        float span = PIPE_PERIMETER / PIPE_SEGMENTS;
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) -> {
            for (int segment = 0; segment < PIPE_SEGMENTS; segment++) {
                boolean on = pairIndex(segment) < litPairs;
                pipeQuad(consumer, pose, segment * span, (segment + 1) * span, PIPE_THICKNESS / 2f,
                        CabinetRenderer.at(z), pipeColor(pairIndex(segment), on ? 1f : PIPE_UNLIT_VALUE), 255,
                        on ? RenderLight.FULL_BRIGHT : light, overlay);
            }
        }));
    }

    private static void renderPipeGlow(float fill, float z, PoseStack poseStack, GeometrySink sink, int overlay) {
        if (RenderPreview.suppressBlendedPasses) return;
        int litPairs = litPairs(fill);
        if (litPairs == 0) return;
        PIPE_GLOW_MESH.renderBlended(new PipeGlowKey(litPairs, z, overlay), poseStack, sink,
                (scratch, glowSink) -> emitPipeGlow(litPairs, z, glowSink, overlay));
    }

    private static void emitPipeGlow(int litPairs, float z, GeometrySink sink, int overlay) {
        float span = PIPE_PERIMETER / PIPE_SEGMENTS;
        sink.emit(SoundstageRenderTypes.eyes(PIPE_GLOW_TEXTURE), (pose, consumer) -> {
            for (int segment = 0; segment < PIPE_SEGMENTS; segment++) {
                if (pairIndex(segment) < litPairs) {
                    int rgb = pipeColor(pairIndex(segment), 1f);
                    float taper = Mth.clamp((litPairs - pairIndex(segment)) / (float) GLOW_TAPER_PAIRS, 0f, 1f);
                    glowQuad(consumer, pose, segment * span, (segment + 1) * span, SPILL_HALF,
                            CabinetRenderer.at(z - 0.05f), scaled(rgb, SPILL_GAIN * taper), overlay);
                    glowQuad(consumer, pose, segment * span, (segment + 1) * span, BLOOM_HALF,
                            CabinetRenderer.at(z - 0.06f), scaled(rgb, BLOOM_GAIN * taper), overlay);
                }
            }
        });
    }

    private static void glowQuad(VertexConsumer c, PoseStack.Pose pose, float from, float to, float half,
                                 float z, int rgb, int overlay) {
        for (int step = 0; step < PIPE_QUAD_STEPS; step++) {
            float t0 = (float) step / PIPE_QUAD_STEPS, t1 = (float) (step + 1) / PIPE_QUAD_STEPS;
            float[] a = pipePoint(Mth.lerp(t0, from, to));
            float[] b = pipePoint(Mth.lerp(t1, from, to));
            float outA = outerHalf(a, half), outB = outerHalf(b, half);
            vertex(c, pose, a[0] - a[2] * half, a[1] - a[3] * half, z, rgb, 255, 0, 1 - t0, RenderLight.FULL_BRIGHT, overlay);
            vertex(c, pose, b[0] - b[2] * half, b[1] - b[3] * half, z, rgb, 255, 0, 1 - t1, RenderLight.FULL_BRIGHT, overlay);
            vertex(c, pose, b[0] + b[2] * outB, b[1] + b[3] * outB, z, rgb, 255, 1, 1 - t1, RenderLight.FULL_BRIGHT, overlay);
            vertex(c, pose, a[0] + a[2] * outA, a[1] + a[3] * outA, z, rgb, 255, 1, 1 - t0, RenderLight.FULL_BRIGHT, overlay);
        }
    }

    private static float outerHalf(float[] point, float half) {
        if (point[3] >= 0f) return half;
        return Math.max(0f, Math.min(half, (point[1] - GLOW_FLOOR_Y) / -point[3]));
    }

    private static int scaled(int rgb, float gain) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * gain));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * gain));
        int b = Math.min(255, (int) ((rgb & 0xFF) * gain));
        return (r << 16) | (g << 8) | b;
    }

    private static int pairIndex(int segment) {
        return Math.min(segment, PIPE_SEGMENTS - 1 - segment);
    }

    private static int pipeColor(int pair, float value) {
        float toTop = (pair + 0.5f) / (PIPE_SEGMENTS / 2f);
        float hue = (190.0f * (1.0f - toTop)) / 360.0f;
        float saturation = 1.0f - 0.55f * Mth.clamp((toTop - 0.85f) / 0.15f, 0.0f, 1.0f);
        return Mth.hsvToRgb(hue, saturation, value);
    }

    private static void pipeQuad(VertexConsumer c, PoseStack.Pose pose, float from, float to, float half,
                                 float z, int rgb, int alpha, int light, int overlay) {
        for (int step = 0; step < PIPE_QUAD_STEPS; step++) {
            float t0 = (float) step / PIPE_QUAD_STEPS, t1 = (float) (step + 1) / PIPE_QUAD_STEPS;
            float[] a = pipePoint(Mth.lerp(t0, from, to));
            float[] b = pipePoint(Mth.lerp(t1, from, to));
            vertex(c, pose, a[0] - a[2] * half, a[1] - a[3] * half, z, rgb, alpha, 0, 1 - t0, light, overlay);
            vertex(c, pose, b[0] - b[2] * half, b[1] - b[3] * half, z, rgb, alpha, 0, 1 - t1, light, overlay);
            vertex(c, pose, b[0] + b[2] * half, b[1] + b[3] * half, z, rgb, alpha, 1, 1 - t1, light, overlay);
            vertex(c, pose, a[0] + a[2] * half, a[1] + a[3] * half, z, rgb, alpha, 1, 1 - t0, light, overlay);
        }
    }

    private static float[] pipePoint(float distance) {
        float t = distance % PIPE_PERIMETER;
        if (t < 0) t += PIPE_PERIMETER;
        float h = PIPE_HALF, s = PIPE_STRAIGHT;
        if (t < s) return straight(t, -h, 0, -1);
        t -= s;
        if (t < PIPE_CORNER_ARC) return corner(s, -s, -90, t);
        t -= PIPE_CORNER_ARC;
        if (t < 2 * s) return straight(h, -s + t, 1, 0);
        t -= 2 * s;
        if (t < PIPE_CORNER_ARC) return corner(s, s, 0, t);
        t -= PIPE_CORNER_ARC;
        if (t < 2 * s) return straight(s - t, h, 0, 1);
        t -= 2 * s;
        if (t < PIPE_CORNER_ARC) return corner(-s, s, 90, t);
        t -= PIPE_CORNER_ARC;
        if (t < 2 * s) return straight(-h, s - t, -1, 0);
        t -= 2 * s;
        if (t < PIPE_CORNER_ARC) return corner(-s, -s, 180, t);
        t -= PIPE_CORNER_ARC;
        return straight(-s + t, -h, 0, -1);
    }

    private static float[] straight(float x, float y, float nx, float ny) {
        return new float[]{PIPE_CENTER_X + x, PIPE_CENTER_Y + y, nx, ny};
    }

    private static float[] corner(float cx, float cy, float startDegrees, float along) {
        double angle = Math.toRadians(startDegrees) + along / PIPE_CORNER * 1.0;
        float nx = (float) Math.cos(angle), ny = (float) Math.sin(angle);
        return new float[]{PIPE_CENTER_X + cx + nx * PIPE_CORNER, PIPE_CENTER_Y + cy + ny * PIPE_CORNER, nx, ny};
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, int rgb,
                               int alpha, float u, float v, int light, int overlay) {
        c.addVertex(pose.pose(), CabinetRenderer.at(x), CabinetRenderer.at(y), z)
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .setUv(u, v).setOverlay(overlay).setLight(light);
        Mesh.applyNormal(c, pose, 0, 0, -1);
    }

    private static void renderDeckGlow(float level, boolean playing, int loopMode, boolean shuffle,
                                       boolean locked, PoseStack poseStack, GeometrySink sink, int overlay) {
        if (RenderPreview.suppressBlendedPasses) return;
        int lit = meterLit(level);
        boolean loopLit = loopMode != 0;
        if (!loopLit && !shuffle && lit == 0 && !playing && !locked) return;
        DECK_GLOW_MESH.renderBlended(new DeckGlowKey(lit, playing, loopMode, shuffle, locked, overlay),
                poseStack, sink, (scratch, glowSink) -> {
            if (loopLit || shuffle) {
                float keyY = CabinetRenderer.at(DECK_Y + 0.25f + 0.28f);
                glowSink.emit(SoundstageRenderTypes.eyes(LAMP_GLOW_TEXTURE), (pose, c) -> {
                    if (loopLit) {
                        flatGlow(pose, c, 3.6f, 7.7f, 5.2f, 9.1f, keyY, scaled(0x6CFF7A, DECK_GLOW_GAIN), overlay);
                    }
                    if (shuffle) {
                        flatGlow(pose, c, 10.8f, 7.7f, 12.4f, 9.1f, keyY, scaled(0x6CFF7A, DECK_GLOW_GAIN), overlay);
                    }
                });
            }
            float y = CabinetRenderer.at(DECK_Y + 0.25f + 0.13f);
            if (locked) {
                glowSink.emit(SoundstageRenderTypes.eyes(LAMP_GLOW_TEXTURE), (pose, c) ->
                        flatGlow(pose, c, 2.6f, 11.4f, 4.5f, 13.6f, y, scaled(LOCK_RGB, DECK_GLOW_GAIN), overlay));
            }
            if (lit == 0 && !playing) return;
            if (lit > 0) {
                glowSink.emit(SoundstageRenderTypes.eyes(PIPE_GLOW_TEXTURE), (pose, c) -> {
                    float barX0 = 3.6f, barX1 = 12.4f, pitch = (barX1 - barX0) / METER_SEGMENTS;
                    for (int i = 0; i < lit; i++) {
                        float t = (i + 0.5f) / METER_SEGMENTS;
                        int rgb = t < 0.6f ? 0x3CE06A : t < 0.85f ? 0xF2B233 : 0xF0453A;
                        flatGlow(pose, c, barX0 + i * pitch, 9.1f, barX0 + (i + 1) * pitch, 12.3f, y,
                                scaled(rgb, DECK_GLOW_GAIN), overlay);
                    }
                });
            }
            if (playing) {
                glowSink.emit(SoundstageRenderTypes.eyes(LAMP_GLOW_TEXTURE), (pose, c) ->
                        flatGlow(pose, c, 11.5f, 11.4f, 13.4f, 13.6f, y, scaled(0x55F07A, DECK_GLOW_GAIN), overlay));
            }
        });
    }

    private static final float DECK_GLOW_GAIN = 0.8f;
    private static final int LOCK_RGB = 0xF2B233;
    private static final ResourceLocation LAMP_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/tube_halo.png");

    private static void flatGlow(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1,
                                 float y, int rgb, int overlay) {
        float ax0 = CabinetRenderer.at(x0), ax1 = CabinetRenderer.at(x1);
        float az0 = CabinetRenderer.at(z0), az1 = CabinetRenderer.at(z1);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        c.addVertex(pose.pose(), ax0, y, az0).setColor(r, g, b, 255).setUv(0, 0).setOverlay(overlay)
                .setLight(RenderLight.FULL_BRIGHT);
                Mesh.applyNormal(c, pose, 0, 1, 0);
        c.addVertex(pose.pose(), ax0, y, az1).setColor(r, g, b, 255).setUv(1, 0).setOverlay(overlay)
                .setLight(RenderLight.FULL_BRIGHT);
                Mesh.applyNormal(c, pose, 0, 1, 0);
        c.addVertex(pose.pose(), ax1, y, az1).setColor(r, g, b, 255).setUv(1, 1).setOverlay(overlay)
                .setLight(RenderLight.FULL_BRIGHT);
                Mesh.applyNormal(c, pose, 0, 1, 0);
        c.addVertex(pose.pose(), ax1, y, az0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay)
                .setLight(RenderLight.FULL_BRIGHT);
                Mesh.applyNormal(c, pose, 0, 1, 0);
    }

    private static void renderDeck(PoseStack.Pose pose, VertexConsumer c, float volume, float level,
                                   boolean playing, int loopMode, boolean shuffle, boolean locked,
                                   int light, int overlay) {
        box(pose, c, 2.6f, DECK_Y, 6.4f, 13.4f, DECK_Y + 0.25f, 13.6f, PANEL_RGB, light, overlay);
        float top = DECK_Y + 0.25f;

        knob(pose, c, 8f, 8.4f, 1.25f, top, 1.0f, volume, light, overlay);
        deckKey(pose, c, 4.4f, 8.4f, loopMode != 0, top, light, overlay);
        deckKey(pose, c, 11.6f, 8.4f, shuffle, top, light, overlay);

        int lit = meterLit(level);
        float barX0 = 3.6f, barX1 = 12.4f, pitch = (barX1 - barX0) / METER_SEGMENTS;
        for (int i = 0; i < METER_SEGMENTS; i++) {
            float t = (i + 0.5f) / METER_SEGMENTS;
            int rgb = t < 0.6f ? 0x3CE06A : t < 0.85f ? 0xF2B233 : 0xF0453A;
            boolean on = i < lit;
            box(pose, c, barX0 + i * pitch + 0.08f, top, 10.3f, barX0 + (i + 1) * pitch - 0.08f, top + 0.12f, 11.1f,
                    on ? rgb : dim(rgb), on ? RenderLight.FULL_BRIGHT : light, overlay);
        }

        box(pose, c, 12.4f, top, 12.3f, 13.0f, top + 0.18f, 12.9f,
                playing ? 0x55F07A : 0x1F3A26, playing ? RenderLight.FULL_BRIGHT : light, overlay);

        box(pose, c, 3.0f, top, 12.3f, 3.6f, top + 0.18f, 12.9f,
                locked ? LOCK_RGB : dim(LOCK_RGB), locked ? RenderLight.FULL_BRIGHT : light, overlay);

        for (float mx : new float[]{4.6f, 11.4f}) {
            box(pose, c, mx - 1.0f, top, 11.9f, mx + 1.0f, top + 0.35f, 13.4f, 0x141517, light, overlay);
            float axisY = top + 0.75f;
            Mesh.emitOffsetCylinderShell(pose, c, CabinetRenderer.at(mx), CabinetRenderer.at(axisY), 0.33f / 16f,
                    CabinetRenderer.at(12.0f), CabinetRenderer.at(13.9f), 0x26282C, light, overlay);
            Mesh.emitOffsetDisc(pose, c, CabinetRenderer.at(mx), CabinetRenderer.at(axisY), 0.33f / 16f,
                    CabinetRenderer.at(13.9f), 0x1A1B1E, 1, light, overlay);
            Mesh.emitOffsetCylinderShell(pose, c, CabinetRenderer.at(mx), CabinetRenderer.at(axisY), 0.58f / 16f,
                    CabinetRenderer.at(11.0f), CabinetRenderer.at(12.0f), 0x9EA3AA, light, overlay);
            Mesh.emitOffsetDisc(pose, c, CabinetRenderer.at(mx), CabinetRenderer.at(axisY), 0.58f / 16f,
                    CabinetRenderer.at(11.0f), 0xB7BCC2, -1, light, overlay);
            Mesh.emitOffsetDisc(pose, c, CabinetRenderer.at(mx), CabinetRenderer.at(axisY), 0.58f / 16f,
                    CabinetRenderer.at(12.0f), 0x7E838A, 1, light, overlay);
        }
    }

    private static void knob(PoseStack.Pose pose, VertexConsumer c, float x, float z, float radius, float base,
                             float height, float value, int light, int overlay) {
        int ribs = 20;
        float top = base + height;
        for (int i = 0; i < ribs; i++) {
            float a0 = (float) (Math.PI * 2 * i / ribs), a1 = (float) (Math.PI * 2 * (i + 1) / ribs);
            float mid = (a0 + a1) / 2f;
            Mesh.emitQuad(pose, c,
                    at(x + Mth.cos(a0) * radius), at(base), at(z + Mth.sin(a0) * radius),
                    at(x + Mth.cos(a0) * radius), at(top), at(z + Mth.sin(a0) * radius),
                    at(x + Mth.cos(a1) * radius), at(top), at(z + Mth.sin(a1) * radius),
                    at(x + Mth.cos(a1) * radius), at(base), at(z + Mth.sin(a1) * radius),
                    (i & 1) == 0 ? KNOB_RGB : KNOB_RIB_RGB, 255, Mth.cos(mid), 0, Mth.sin(mid), light, overlay);
            Mesh.emitQuad(pose, c,
                    at(x), at(top), at(z),
                    at(x + Mth.cos(a0) * radius), at(top), at(z + Mth.sin(a0) * radius),
                    at(x + Mth.cos(a1) * radius), at(top), at(z + Mth.sin(a1) * radius),
                    at(x), at(top), at(z),
                    0x34373C, 255, 0, 1, 0, light, overlay);
        }
        float[] sweep = com.nachtraben.soundstage.core.KnobSweep.direction(value);
        float dx = -sweep[0], dz = sweep[1];
        float px = -dz * 0.14f, pz = dx * 0.14f;
        float r0 = radius * 0.2f, r1 = radius * 0.9f, y = top + 0.01f;
        Mesh.emitQuad(pose, c,
                at(x + dx * r0 + px), at(y), at(z + dz * r0 + pz),
                at(x + dx * r1 + px), at(y), at(z + dz * r1 + pz),
                at(x + dx * r1 - px), at(y), at(z + dz * r1 - pz),
                at(x + dx * r0 - px), at(y), at(z + dz * r0 - pz),
                POINTER_RGB, 255, 0, 1, 0, light, overlay);
    }

    private static void renderHandles(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        for (float[] side : new float[][]{{1.2f, 2.0f}, {14.0f, 14.8f}}) {
            box(pose, c, side[0], 11.4f, 7.0f, side[1], 13.1f, 7.6f, 0x151618, light, overlay);
            box(pose, c, side[0], 11.4f, 10.4f, side[1], 13.1f, 11.0f, 0x151618, light, overlay);
            float gripX0 = side[0] < 8 ? side[0] - 0.3f : side[1] - 0.5f;
            box(pose, c, gripX0, 11.6f, 7.0f, gripX0 + 0.8f, 12.9f, 11.0f, 0x202225, light, overlay);
        }
    }

    private static int dim(int rgb) {
        return (((rgb >> 16) & 0xFF) / 6 << 16) | (((rgb >> 8) & 0xFF) / 6 << 8) | ((rgb & 0xFF) / 6);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0,
                            float x1, float y1, float z1, int rgb, int light, int overlay) {
        Mesh.emitBox(pose, c, at(x0), at(y0), at(z0), at(x1), at(y1), at(z1), rgb, light, overlay);
    }

    private static float at(float blockUnits) {
        return CabinetRenderer.at(blockUnits);
    }
}
