package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.TreeSet;

final class CabinetRenderer {
    private CabinetRenderer() {}

    private static final ResourceLocation FLAT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/led.png");

    record Layout(float x0, float y0, float z0, float x1, float y1, float z1,
                  float frame, float recess, float grilleDepth) {
        float baffleZ() { return z0 + recess; }

        float grilleFrontZ() { return z0 - grilleDepth; }
    }

    record Hole(float x, float y, float bound, float inner, boolean roundedSquare) {}

    private static final int BAFFLE_RGB = 0x1D1E20;
    private static final float OUTER_BEVEL = 0.30f;
    private static final float OPENING_BEVEL = 0.25f;
    private static final float GRILLE_INSET = 0.55f;
    private static final float GRILLE_BEVEL = 0.15f;
    private static final int HOLE_SEGMENTS = 32;

    static void render(Layout layout, SpeakerFinish finish, boolean grilleOn, Hole[] holes,
                       PoseStack poseStack, GeometrySink sink, int light, int overlay) {
        TextureAtlasSprite wood = sideSprite(finish.casingBlock());
        TextureAtlasSprite wool = sideSprite(finish.grilleBlock());
        boolean clear = finish.isTransparent();

        if (!clear || grilleOn) {
            sink.emit(AtlasSprites.CUTOUT, (pose, consumer) -> {
                if (!clear) {
                    casing(pose, consumer, layout, wood, light, overlay);
                }
                if (grilleOn) {
                    bevelledPanel(pose, consumer, wool,
                            layout.x0() + GRILLE_INSET, layout.y0() + GRILLE_INSET, layout.grilleFrontZ(),
                            layout.x1() - GRILLE_INSET, layout.y1() - GRILLE_INSET, layout.z0(), GRILLE_BEVEL,
                            light, overlay);
                }
            });
        }

        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, consumer) -> {
            baffle(pose, consumer, layout, holes, light, overlay);
            if (clear) {
                clearFrame(pose, consumer, layout, light, overlay);
            }
        }));
    }

    static void clearBoxEdges(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0, float x1,
                              float y1, float z1, float e, int light, int overlay) {
        for (int iy = 0; iy < 2; iy++) {
            float ya = iy == 0 ? y0 : y1 - e, yb = iy == 0 ? y0 + e : y1;
            for (int iz = 0; iz < 2; iz++) {
                float za = iz == 0 ? z0 : z1 - e, zb = iz == 0 ? z0 + e : z1;
                edgeBar(pose, c, x0, ya, za, x1, yb, zb, light, overlay);
            }
        }
        for (int ix = 0; ix < 2; ix++) {
            float xa = ix == 0 ? x0 : x1 - e, xb = ix == 0 ? x0 + e : x1;
            for (int iz = 0; iz < 2; iz++) {
                float za = iz == 0 ? z0 : z1 - e, zb = iz == 0 ? z0 + e : z1;
                edgeBar(pose, c, xa, y0 + e, za, xb, y1 - e, zb, light, overlay);
            }
            for (int iy = 0; iy < 2; iy++) {
                float ya = iy == 0 ? y0 : y1 - e, yb = iy == 0 ? y0 + e : y1;
                edgeBar(pose, c, xa, ya, z0 + e, xb, yb, z1 - e, light, overlay);
            }
        }
    }

    static void renderClearBoxPanes(SpeakerFinish finish, float x0, float y0, float z0, float x1, float y1, float z1,
                                    float e, boolean back, GeometrySink sink, int light, int overlay) {
        int rgb = paneRgb(finish);
        int alpha = paneAlpha(finish);
        sink.emit(SoundstageRenderTypes.glass(FLAT_TEXTURE), (pose, c) -> {
            float ix0 = x0 + e, ix1 = x1 - e, iy0 = y0 + e, iy1 = y1 - e, iz0 = z0 + e, iz1 = z1 - e;
            pane(pose, c, x0, iy0, iz0, x0, iy1, iz1, -1, 0, 0, rgb, alpha, light, overlay);
            pane(pose, c, x1, iy0, iz0, x1, iy1, iz1, 1, 0, 0, rgb, alpha, light, overlay);
            pane(pose, c, ix0, y1, iz0, ix1, y1, iz1, 0, 1, 0, rgb, alpha, light, overlay);
            pane(pose, c, ix0, y0, iz0, ix1, y0, iz1, 0, -1, 0, rgb, alpha, light, overlay);
            pane(pose, c, ix0, iy0, z0, ix1, iy1, z0, 0, 0, -1, rgb, alpha, light, overlay);
            if (back) pane(pose, c, ix0, iy0, z1, ix1, iy1, z1, 0, 0, 1, rgb, alpha, light, overlay);
        });
    }

    private static int paneRgb(SpeakerFinish finish) {
        int col = finish.casingBlock().defaultMapColor().col;
        return col == 0 ? CLEAR_PANE_RGB : col;
    }

    private static int paneAlpha(SpeakerFinish finish) {
        return finish.casingBlock().defaultMapColor().col == 0 ? CLEAR_PANE_ALPHA : TINTED_PANE_ALPHA;
    }

    private static final float CLEAR_EDGE = 0.6f;
    private static final int CLEAR_EDGE_RGB = 0x2A2D32;

    private static void clearFrame(PoseStack.Pose pose, VertexConsumer c, Layout l, int light, int overlay) {
        float e = CLEAR_EDGE;
        float x0 = l.x0(), y0 = l.y0(), z0 = l.z0(), x1 = l.x1(), y1 = l.y1(), z1 = l.z1();
        for (int iy = 0; iy < 2; iy++) {
            float ya = iy == 0 ? y0 : y1 - e, yb = iy == 0 ? y0 + e : y1;
            for (int iz = 0; iz < 2; iz++) {
                float za = iz == 0 ? z0 : z1 - e, zb = iz == 0 ? z0 + e : z1;
                edgeBar(pose, c, x0, ya, za, x1, yb, zb, light, overlay);
            }
        }
        for (int ix = 0; ix < 2; ix++) {
            float xa = ix == 0 ? x0 : x1 - e, xb = ix == 0 ? x0 + e : x1;
            for (int iz = 0; iz < 2; iz++) {
                float za = iz == 0 ? z0 : z1 - e, zb = iz == 0 ? z0 + e : z1;
                edgeBar(pose, c, xa, y0 + e, za, xb, y1 - e, zb, light, overlay);
            }
            for (int iy = 0; iy < 2; iy++) {
                float ya = iy == 0 ? y0 : y1 - e, yb = iy == 0 ? y0 + e : y1;
                edgeBar(pose, c, xa, ya, z0 + e, xb, yb, z1 - e, light, overlay);
            }
        }
    }

    private static void edgeBar(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0,
                                float x1, float y1, float z1, int light, int overlay) {
        Mesh.emitBox(pose, c, at(x0), at(y0), at(z0), at(x1), at(y1), at(z1), CLEAR_EDGE_RGB, light, overlay);
    }

    private static final int CLEAR_PANE_RGB = 0xB9D2DC;
    private static final int CLEAR_PANE_ALPHA = 58;
    private static final int TINTED_PANE_ALPHA = 118;

    private static void clearPanes(PoseStack.Pose pose, VertexConsumer c, Layout l,
                                   int rgb, int alpha, int light, int overlay) {
        float e = CLEAR_EDGE;
        float x0 = l.x0(), y0 = l.y0(), z0 = l.z0(), x1 = l.x1(), y1 = l.y1(), z1 = l.z1();
        float ix0 = x0 + e, ix1 = x1 - e, iy0 = y0 + e, iy1 = y1 - e, iz0 = z0 + e, iz1 = z1 - e;
        pane(pose, c, x0, iy0, iz0, x0, iy1, iz1, -1, 0, 0, rgb, alpha, light, overlay);
        pane(pose, c, x1, iy0, iz0, x1, iy1, iz1, 1, 0, 0, rgb, alpha, light, overlay);
        pane(pose, c, ix0, y1, iz0, ix1, y1, iz1, 0, 1, 0, rgb, alpha, light, overlay);
        pane(pose, c, ix0, y0, iz0, ix1, y0, iz1, 0, -1, 0, rgb, alpha, light, overlay);
        pane(pose, c, ix0, iy0, z1, ix1, iy1, z1, 0, 0, 1, rgb, alpha, light, overlay);
    }

    private static void pane(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0,
                             float x1, float y1, float z1, float nx, float ny, float nz,
                             int rgb, int alpha, int light, int overlay) {
        if (x0 == x1) {
            Mesh.emitQuad(pose, c, at(x0), at(y0), at(z0), at(x0), at(y1), at(z0),
                    at(x0), at(y1), at(z1), at(x0), at(y0), at(z1),
                    rgb, alpha, nx, ny, nz, light, overlay);
        } else if (y0 == y1) {
            Mesh.emitQuad(pose, c, at(x0), at(y0), at(z0), at(x1), at(y0), at(z0),
                    at(x1), at(y0), at(z1), at(x0), at(y0), at(z1),
                    rgb, alpha, nx, ny, nz, light, overlay);
        } else {
            Mesh.emitQuad(pose, c, at(x0), at(y0), at(z0), at(x1), at(y0), at(z0),
                    at(x1), at(y1), at(z0), at(x0), at(y1), at(z0),
                    rgb, alpha, nx, ny, nz, light, overlay);
        }
    }

    static void renderClearCasing(Layout layout, SpeakerFinish finish,
                                  GeometrySink sink, int light, int overlay) {
        if (!finish.isTransparent()) return;
        int rgb = paneRgb(finish);
        int alpha = paneAlpha(finish);
        sink.emit(SoundstageRenderTypes.glass(FLAT_TEXTURE), (pose, consumer) ->
                clearPanes(pose, consumer, layout, rgb, alpha, light, overlay));
    }

    private static void baffle(PoseStack.Pose pose, VertexConsumer c, Layout l, Hole[] holes,
                               int light, int overlay) {
        float bx0 = l.x0() + l.frame(), bx1 = l.x1() - l.frame();
        float by0 = l.y0() + l.frame(), by1 = l.y1() - l.frame();
        float bz = at(l.baffleZ());
        TreeSet<Float> xs = new TreeSet<>(List.of(bx0, bx1));
        TreeSet<Float> ys = new TreeSet<>(List.of(by0, by1));
        for (Hole h : holes) {
            xs.add(Mth.clamp(h.x() - h.bound(), bx0, bx1));
            xs.add(Mth.clamp(h.x() + h.bound(), bx0, bx1));
            ys.add(Mth.clamp(h.y() - h.bound(), by0, by1));
            ys.add(Mth.clamp(h.y() + h.bound(), by0, by1));
        }
        Float[] xa = xs.toArray(new Float[0]);
        Float[] ya = ys.toArray(new Float[0]);
        for (int i = 0; i < xa.length - 1; i++) {
            for (int j = 0; j < ya.length - 1; j++) {
                float mx = (xa[i] + xa[i + 1]) / 2f, my = (ya[j] + ya[j + 1]) / 2f;
                boolean covered = false;
                for (Hole h : holes) {
                    covered |= Math.abs(mx - h.x()) < h.bound() && Math.abs(my - h.y()) < h.bound();
                }
                if (covered) continue;
                Mesh.emitQuad(pose, c,
                        at(xa[i]), at(ya[j]), bz, at(xa[i]), at(ya[j + 1]), bz,
                        at(xa[i + 1]), at(ya[j + 1]), bz, at(xa[i + 1]), at(ya[j]), bz,
                        BAFFLE_RGB, 255, 0, 0, -1, light, overlay);
            }
        }
        for (Hole h : holes) {
            for (int s = 0; s < HOLE_SEGMENTS; s++) {
                float a0 = (float) (Math.PI * 2.0 * s / HOLE_SEGMENTS);
                float a1 = (float) (Math.PI * 2.0 * (s + 1) / HOLE_SEGMENTS);
                float[] i0 = holePoint(h, a0), i1 = holePoint(h, a1);
                float[] o0 = squarePoint(h, i0), o1 = squarePoint(h, i1);
                Mesh.emitQuad(pose, c,
                        at(h.x() + i0[0]), at(h.y() + i0[1]), bz, at(h.x() + o0[0]), at(h.y() + o0[1]), bz,
                        at(h.x() + o1[0]), at(h.y() + o1[1]), bz, at(h.x() + i1[0]), at(h.y() + i1[1]), bz,
                        BAFFLE_RGB, 255, 0, 0, -1, light, overlay);
            }
        }
    }

    private static float[] holePoint(Hole h, float angle) {
        if (h.roundedSquare()) {
            Mesh.Point2 p = Mesh.roundedPoint(angle, h.inner());
            return new float[]{p.x(), p.y()};
        }
        return new float[]{Mth.cos(angle) * h.inner(), Mth.sin(angle) * h.inner()};
    }

    private static float[] squarePoint(Hole h, float[] inner) {
        float m = Math.max(Math.abs(inner[0]), Math.abs(inner[1]));
        return new float[]{inner[0] / m * h.bound(), inner[1] / m * h.bound()};
    }

    static void renderWoodBox(SpeakerFinish finish, float x0, float y0, float z0, float x1, float y1, float z1,
                              GeometrySink sink, int light, int overlay) {
        TextureAtlasSprite wood = sideSprite(finish.casingBlock());
        RenderType type = finish.isTransparent()
                ? SoundstageRenderTypes.glass(TextureAtlas.LOCATION_BLOCKS)
                : AtlasSprites.CUTOUT;
        sink.emit(type, (pose, consumer) ->
                box(pose, consumer, wood, x0, y0, z0, x1, y1, z1, light, overlay));
    }

    private static void casing(PoseStack.Pose pose, VertexConsumer c, Layout l,
                               TextureAtlasSprite s, int light, int overlay) {
        float x0 = l.x0(), y0 = l.y0(), z0 = l.z0(), x1 = l.x1(), y1 = l.y1(), z1 = l.z1();
        float b = OUTER_BEVEL, zs = z0 + b;
        face(pose, c, s, x1, y0, zs, x1, y1, zs, x1, y1, z1, x1, y0, z1, zs, y0, z1, y1, 1, 0, 0, light, overlay);
        face(pose, c, s, x0, y0, z1, x0, y1, z1, x0, y1, zs, x0, y0, zs, zs, y0, z1, y1, -1, 0, 0, light, overlay);
        face(pose, c, s, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, x0, y0, x1, y1, 0, 0, 1, light, overlay);
        face(pose, c, s, x0, y1, zs, x0, y1, z1, x1, y1, z1, x1, y1, zs, x0, zs, x1, z1, 0, 1, 0, light, overlay);
        face(pose, c, s, x1, y0, zs, x1, y0, z1, x0, y0, z1, x0, y0, zs, x0, zs, x1, z1, 0, -1, 0, light, overlay);

        float f = l.frame();
        float ix0 = x0 + f, ix1 = x1 - f, iy0 = y0 + f, iy1 = y1 - f, bz = l.baffleZ();
        float o = OPENING_BEVEL;
        ring(pose, c, s, x0 + b, y0 + b, x1 - b, y1 - b, ix0 - o, iy0 - o, ix1 + o, iy1 + o, z0, z0, light, overlay);
        ring(pose, c, s, x0, y0, x1, y1, x0 + b, y0 + b, x1 - b, y1 - b, zs, z0, light, overlay);
        ring(pose, c, s, ix0 - o, iy0 - o, ix1 + o, iy1 + o, ix0, iy0, ix1, iy1, z0, z0 + o, light, overlay);
        ring(pose, c, s, ix0, iy0, ix1, iy1, ix0, iy0, ix1, iy1, z0 + o, bz, light, overlay);
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                             float ox0, float oy0, float ox1, float oy1,
                             float ix0, float iy0, float ix1, float iy1, float zo, float zi,
                             int light, int overlay) {
        float slope = Math.abs(zi - zo) > 0.001f ? 0.7f : 0f;
        float nz = zi == zo ? -1f : -0.7f;
        face(pose, c, s, ox0, oy0, zo, ox0, oy1, zo, ix0, iy1, zi, ix0, iy0, zi, 16 - ix0, 16 - oy1, 16 - ox0, 16 - oy0,
                zi < zo ? -slope : slope, 0, nz, light, overlay);
        face(pose, c, s, ix1, iy0, zi, ix1, iy1, zi, ox1, oy1, zo, ox1, oy0, zo, 16 - ox1, 16 - oy1, 16 - ix1, 16 - oy0,
                zi < zo ? slope : -slope, 0, nz, light, overlay);
        face(pose, c, s, ox0, oy1, zo, ox1, oy1, zo, ix1, iy1, zi, ix0, iy1, zi, 16 - ox1, 16 - oy1, 16 - ox0, 16 - iy1,
                0, zi < zo ? slope : -slope, nz, light, overlay);
        face(pose, c, s, ix0, iy0, zi, ix1, iy0, zi, ox1, oy0, zo, ox0, oy0, zo, 16 - ox1, 16 - iy0, 16 - ox0, 16 - oy0,
                0, zi < zo ? -slope : slope, nz, light, overlay);
    }

    private static void bevelledPanel(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                                      float x0, float y0, float z0, float x1, float y1, float z1, float bevel,
                                      int light, int overlay) {
        float zb = z0 + bevel;
        face(pose, c, s, x1 - bevel, y0 + bevel, z0, x1 - bevel, y1 - bevel, z0, x0 + bevel, y1 - bevel, z0,
                x0 + bevel, y0 + bevel, z0, 16 - x1, 16 - y1, 16 - x0, 16 - y0, 0, 0, -1, light, overlay);
        ring(pose, c, s, x0, y0, x1, y1, x0 + bevel, y0 + bevel, x1 - bevel, y1 - bevel, zb, z0, light, overlay);
        face(pose, c, s, x1, y0, zb, x1, y1, zb, x1, y1, z1, x1, y0, z1, zb, y0, z1, y1, 1, 0, 0, light, overlay);
        face(pose, c, s, x0, y0, z1, x0, y1, z1, x0, y1, zb, x0, y0, zb, zb, y0, z1, y1, -1, 0, 0, light, overlay);
        face(pose, c, s, x0, y1, zb, x0, y1, z1, x1, y1, z1, x1, y1, zb, x0, zb, x1, z1, 0, 1, 0, light, overlay);
        face(pose, c, s, x1, y0, zb, x1, y0, z1, x0, y0, z1, x0, y0, zb, x0, zb, x1, z1, 0, -1, 0, light, overlay);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            int light, int overlay) {
        face(pose, c, s, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 16 - x1, 16 - y1, 16 - x0, 16 - y0, 0, 0, -1,
                light, overlay);
        face(pose, c, s, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, 16 - y1, x1, 16 - y0, 0, 0, 1,
                light, overlay);
        face(pose, c, s, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, z0, y0, z1, y1, 1, 0, 0, light, overlay);
        face(pose, c, s, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, z0, y0, z1, y1, -1, 0, 0, light, overlay);
        face(pose, c, s, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1, 0, 1, 0, light, overlay);
        face(pose, c, s, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, y0, z0, x0, z0, x1, z1, 0, -1, 0, light, overlay);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1,
                             float nx, float ny, float nz, int light, int overlay) {
        //? if <1.21 {
        /*float su0 = s.getU(u0), su1 = s.getU(u1);
        float sv0 = s.getV(v0), sv1 = s.getV(v1);
        *///?} else {
        float su0 = s.getU(u0 / 16f), su1 = s.getU(u1 / 16f);
        float sv0 = s.getV(v0 / 16f), sv1 = s.getV(v1 / 16f);
        //?}
        vertex(pose, c, ax, ay, az, su0, sv1, nx, ny, nz, light, overlay);
        vertex(pose, c, bx, by, bz, su0, sv0, nx, ny, nz, light, overlay);
        vertex(pose, c, cx, cy, cz, su1, sv0, nx, ny, nz, light, overlay);
        vertex(pose, c, dx, dy, dz, su1, sv1, nx, ny, nz, light, overlay);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light, int overlay) {
        c.addVertex(pose.pose(), at(x), at(y), at(z))
                .setColor(255, 255, 255, 255)
                .setUv(u, v).setOverlay(overlay).setLight(light);
        Mesh.applyNormal(c, pose, nx, ny, nz);
    }

    private static TextureAtlasSprite sideSprite(Block block) {
        //? if >=26.1 {
        /*return Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .getParticleMaterial(block.defaultBlockState()).sprite();
        *///?} else {
        return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
                .getParticleIcon(block.defaultBlockState());
        //?}
    }

    static float at(float blockUnits) {
        float v = blockUnits / 16f - 0.5f;
        if (v <= -0.5f) return -0.5f + SURFACE_EPSILON;
        if (v >= 0.5f) return 0.5f - SURFACE_EPSILON;
        return v;
    }

    private static final float SURFACE_EPSILON = 0.001f;
}
