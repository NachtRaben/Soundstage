package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class AmplifierChassisRenderer {
    private AmplifierChassisRenderer() {}

    static final float[] TUBE_CENTERS_X = {4.0f, 7.0f, 10.0f, 13.0f};
    static final float TUBE_CENTER_Z = 8.0f;

    private static final float X0 = 1f, X1 = 15f, TOP = 6f, FRONT = 1f, BODY_BACK = 12.5f;

    private static final float HOLE_RADIUS = 1.45f;
    private static final float HOLE_BOUND = 1.5f;
    private static final float COLLAR_INNER = 1.1f;
    private static final float WELL_FLOOR = 5.2f;
    private static final float BASE_RADIUS = 1.05f;
    static final float GLASS_BOTTOM = 7.0f;
    static final float GLASS_RADIUS = 1.12f;
    private static final float GLASS_BODY_TOP = 11.3f;
    private static final float GLASS_SHOULDER_TOP = 11.9f;
    private static final float GLASS_SHOULDER_RADIUS = 0.72f;
    private static final float GLASS_CROWN = 12.25f;
    private static final float GLASS_INSET = 0.02f;

    private static final float TOP_FIN_HEIGHT = 0.7f;
    private static final float FIN_THICKNESS = 0.3f;
    private static final float FIN_PITCH = 0.85f;
    private static final float BACK_FIN_DEPTH = 2.2f;

    private static final int PLATE_RGB = 0x8F969D;
    private static final int FIN_RGB = 0xA9B0B7;
    private static final int TERMINAL_PLATE_RGB = 0x17181A;
    private static final int POST_NUT_RGB = 0xC9A64B;
    private static final int RED_RGB = 0xB3261E;
    private static final int BLACK_RGB = 0x1B1B1D;

    static void render(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        topPlate(pose, c, light, overlay);
        for (float x : TUBE_CENTERS_X) {
            socket(pose, c, x, light, overlay);
        }
        topFins(pose, c, light, overlay);
        backFins(pose, c, light, overlay);
        terminals(pose, c, light, overlay);
    }

    private static void topPlate(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float hz0 = TUBE_CENTER_Z - HOLE_BOUND, hz1 = TUBE_CENTER_Z + HOLE_BOUND;
        float hx0 = TUBE_CENTERS_X[0] - HOLE_BOUND, hx1 = TUBE_CENTERS_X[TUBE_CENTERS_X.length - 1] + HOLE_BOUND;
        horizontal(pose, c, X0, FRONT, X1, hz0, TOP, PLATE_RGB, light, overlay);
        horizontal(pose, c, X0, hz1, X1, BODY_BACK, TOP, PLATE_RGB, light, overlay);
        horizontal(pose, c, X0, hz0, hx0, hz1, TOP, PLATE_RGB, light, overlay);
        horizontal(pose, c, hx1, hz0, X1, hz1, TOP, PLATE_RGB, light, overlay);
        int segments = 32;
        for (float cx : TUBE_CENTERS_X) {
            for (int s = 0; s < segments; s++) {
                float a0 = (float) (Math.PI * 2 * s / segments), a1 = (float) (Math.PI * 2 * (s + 1) / segments);
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                float m0 = Math.max(Math.abs(c0), Math.abs(s0)), m1 = Math.max(Math.abs(c1), Math.abs(s1));
                quad(pose, c,
                        cx + c0 * HOLE_RADIUS, TOP, TUBE_CENTER_Z + s0 * HOLE_RADIUS,
                        cx + c0 / m0 * HOLE_BOUND, TOP, TUBE_CENTER_Z + s0 / m0 * HOLE_BOUND,
                        cx + c1 / m1 * HOLE_BOUND, TOP, TUBE_CENTER_Z + s1 / m1 * HOLE_BOUND,
                        cx + c1 * HOLE_RADIUS, TOP, TUBE_CENTER_Z + s1 * HOLE_RADIUS,
                        PLATE_RGB, 0, 1, 0, light, overlay);
            }
        }
    }

    private static void socket(PoseStack.Pose pose, VertexConsumer c, float cx, int light, int overlay) {
        float x = at(cx), z = at(TUBE_CENTER_Z);
        verticalCylinder(pose, c, x, z, HOLE_RADIUS / 16f, WELL_FLOOR, TOP, 0xCFCAC0, true, light, overlay);
        annulusY(pose, c, cx, COLLAR_INNER, HOLE_RADIUS, TOP + 0.02f, 0xE9E5DB, light, overlay);
        verticalCylinder(pose, c, x, z, COLLAR_INNER / 16f, WELL_FLOOR, TOP + 0.02f, 0xCFCAC0, false, light, overlay);
        verticalCylinder(pose, c, x, z, BASE_RADIUS / 16f, TOP, GLASS_BOTTOM, 0x1C1A19, false, light, overlay);
        disc(pose, c, x, z, BASE_RADIUS / 16f, GLASS_BOTTOM, 0x1C1A19, light, overlay);
    }

    static void glass(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float[] radii = {GLASS_RADIUS, GLASS_RADIUS, GLASS_SHOULDER_RADIUS, GLASS_SHOULDER_RADIUS * 0.75f, 0.12f, 0f};
        float[] heights = {GLASS_BOTTOM, GLASS_BODY_TOP, GLASS_SHOULDER_TOP, GLASS_CROWN - 0.12f, GLASS_CROWN,
                GLASS_CROWN + 0.05f};
        for (float cx : TUBE_CENTERS_X) {
            profile(pose, c, cx, radii, heights, 0f, 255, 255, 255, 255, light, overlay);
        }
    }

    static void backlight(PoseStack.Pose pose, VertexConsumer c, float bottom, float top, float radius,
                          int r, int g, int b, int light, int overlay) {
        for (float cx : TUBE_CENTERS_X) {
            profile(pose, c, cx, new float[]{0f, radius, radius, 0f}, new float[]{bottom, bottom, top, top},
                    0f, r, g, b, 255, light, overlay, true);
        }
    }

    private static void profile(PoseStack.Pose pose, VertexConsumer c, float cx, float[] radii, float[] heights,
                                float inset, int r, int g, int b, int a, int light, int overlay) {
        profile(pose, c, cx, radii, heights, inset, r, g, b, a, light, overlay, false);
    }

    private static final int PROFILE_SEGMENTS = 24;
    private static final float[] SEGMENT_NX = new float[PROFILE_SEGMENTS];
    private static final float[] SEGMENT_NY = new float[PROFILE_SEGMENTS];
    private static final float[] SEGMENT_NZ = new float[PROFILE_SEGMENTS];
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();

    private static void profile(PoseStack.Pose pose, VertexConsumer c, float cx, float[] radii, float[] heights,
                                float inset, int r, int g, int b, int a, int light, int overlay, boolean inward) {
        int segments = PROFILE_SEGMENTS;
        float x = at(cx), z = at(TUBE_CENTER_Z);
        float span = heights[heights.length - 1] - heights[0];
        for (int i = 0; i < segments; i++) {
            float mid = (float) (Math.PI * 2 * (i + 0.5) / segments);
            pose.transformNormal(Mth.cos(mid), 0, Mth.sin(mid), NORMAL_SCRATCH);
            SEGMENT_NX[i] = NORMAL_SCRATCH.x();
            SEGMENT_NY[i] = NORMAL_SCRATCH.y();
            SEGMENT_NZ[i] = NORMAL_SCRATCH.z();
        }
        for (int band = 0; band < radii.length - 1; band++) {
            float r0 = Math.max(0, radii[band] - inset) / 16f, r1 = Math.max(0, radii[band + 1] - inset) / 16f;
            float y0 = at(heights[band]), y1 = at(heights[band + 1]);
            float v0 = 1 - (heights[band] - heights[0]) / span, v1 = 1 - (heights[band + 1] - heights[0]) / span;
            for (int i = 0; i < segments; i++) {
                float a0 = (float) (Math.PI * 2 * i / segments), a1 = (float) (Math.PI * 2 * (i + 1) / segments);
                if (inward) {
                    float swap = a0;
                    a0 = a1;
                    a1 = swap;
                }
                float u0 = 4f * i / segments;
                u0 -= Mth.floor(u0);
                float u1 = u0 + 4f / segments;
                float nx = SEGMENT_NX[i], ny = SEGMENT_NY[i], nz = SEGMENT_NZ[i];
                vertex(c, pose, x + Mth.cos(a0) * r0, y0, z + Mth.sin(a0) * r0, u0, v0, r, g, b, a, nx, ny, nz,
                        light, overlay);
                vertex(c, pose, x + Mth.cos(a0) * r1, y1, z + Mth.sin(a0) * r1, u0, v1, r, g, b, a, nx, ny, nz,
                        light, overlay);
                vertex(c, pose, x + Mth.cos(a1) * r1, y1, z + Mth.sin(a1) * r1, u1, v1, r, g, b, a, nx, ny, nz,
                        light, overlay);
                vertex(c, pose, x + Mth.cos(a1) * r0, y0, z + Mth.sin(a1) * r0, u1, v0, r, g, b, a, nx, ny, nz,
                        light, overlay);
            }
        }
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz, int light, int overlay) {
        c.addVertex(pose.pose(), x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }

    private static void annulusY(PoseStack.Pose pose, VertexConsumer c, float cx, float inner, float outer, float y,
                                 int rgb, int light, int overlay) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2 * i / segments), a1 = (float) (Math.PI * 2 * (i + 1) / segments);
            quad(pose, c,
                    cx + Mth.cos(a0) * inner, y, TUBE_CENTER_Z + Mth.sin(a0) * inner,
                    cx + Mth.cos(a0) * outer, y, TUBE_CENTER_Z + Mth.sin(a0) * outer,
                    cx + Mth.cos(a1) * outer, y, TUBE_CENTER_Z + Mth.sin(a1) * outer,
                    cx + Mth.cos(a1) * inner, y, TUBE_CENTER_Z + Mth.sin(a1) * inner,
                    rgb, 0, 1, 0, light, overlay);
        }
    }

    private static void topFins(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float clearFront = TUBE_CENTER_Z - HOLE_BOUND - 0.2f;
        float clearBack = TUBE_CENTER_Z + HOLE_BOUND + 0.2f;
        for (float z = FRONT + 0.35f; z + FIN_THICKNESS <= BODY_BACK - 0.2f; z += FIN_PITCH) {
            if (z + FIN_THICKNESS > clearFront && z < clearBack) continue;
            fin(pose, c, X0 + 0.15f, TOP, z, X1 - 0.15f, TOP + TOP_FIN_HEIGHT, z + FIN_THICKNESS, light, overlay);
        }
    }

    private static void backFins(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        for (float y = 0.45f; y + FIN_THICKNESS <= TOP - 0.2f; y += FIN_PITCH) {
            fin(pose, c, X0 + 0.15f, y, BODY_BACK, 8.6f, y + FIN_THICKNESS, BODY_BACK + BACK_FIN_DEPTH, light, overlay);
        }
    }

    private static void terminals(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        box(pose, c, 9.0f, 0.6f, BODY_BACK, 14.7f, 5.4f, BODY_BACK + 0.2f, TERMINAL_PLATE_RGB, light, overlay);
        for (float px : new float[]{10.4f, 13.3f}) {
            post(pose, c, px, 4.0f, RED_RGB, light, overlay);
            post(pose, c, px, 2.0f, BLACK_RGB, light, overlay);
        }
    }

    private static void post(PoseStack.Pose pose, VertexConsumer c, float px, float py, int rgb, int light,
                             int overlay) {
        float x = at(px), y = at(py), plate = BODY_BACK + 0.2f;
        axialCylinder(pose, c, x, y, 0.50f, plate, plate + 0.3f, POST_NUT_RGB, 6, true, light, overlay);
        axialCylinder(pose, c, x, y, 0.36f, plate + 0.3f, plate + 1.1f, rgb, 16, false, light, overlay);
        axialCylinder(pose, c, x, y, 0.46f, plate + 1.1f, plate + 1.6f, rgb, 12, false, light, overlay);
        Mesh.emitOffsetDisc(pose, c, x, y, 0.46f / 16f, at(plate + 1.6f), rgb, 1, light, overlay);
        Mesh.emitOffsetDisc(pose, c, x, y, 0.16f / 16f, at(plate + 1.61f), 0x050506, 1, light, overlay);
    }

    private static void fin(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0,
                            float x1, float y1, float z1, int light, int overlay) {
        box(pose, c, x0, y0, z0, x1, y1, z1, FIN_RGB, light, overlay);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0,
                            float x1, float y1, float z1, int rgb, int light, int overlay) {
        Mesh.emitBox(pose, c, at(x0), at(y0), at(z0), at(x1), at(y1), at(z1), rgb, light, overlay);
    }

    private static void horizontal(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1,
                                   float y, int rgb, int light, int overlay) {
        quad(pose, c, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, rgb, 0, 1, 0, light, overlay);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer c,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int rgb, float nx, float ny, float nz, int light, int overlay) {
        Mesh.emitQuad(pose, c, at(ax), at(ay), at(az), at(bx), at(by), at(bz),
                at(cx), at(cy), at(cz), at(dx), at(dy), at(dz), rgb, 255, nx, ny, nz, light, overlay);
    }

    private static void verticalCylinder(PoseStack.Pose pose, VertexConsumer c, float x, float z, float r,
                                         float y0, float y1, int rgb, boolean inward, int light, int overlay) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2 * i / segments), a1 = (float) (Math.PI * 2 * (i + 1) / segments);
            float mid = (a0 + a1) / 2, sign = inward ? -1 : 1;
            Mesh.emitQuad(pose, c,
                    x + Mth.cos(a0) * r, at(y0), z + Mth.sin(a0) * r,
                    x + Mth.cos(a0) * r, at(y1), z + Mth.sin(a0) * r,
                    x + Mth.cos(a1) * r, at(y1), z + Mth.sin(a1) * r,
                    x + Mth.cos(a1) * r, at(y0), z + Mth.sin(a1) * r,
                    rgb, 255, sign * Mth.cos(mid), 0, sign * Mth.sin(mid), light, overlay);
        }
    }

    private static void disc(PoseStack.Pose pose, VertexConsumer c, float x, float z, float r, float y,
                             int rgb, int light, int overlay) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2 * i / segments), a1 = (float) (Math.PI * 2 * (i + 1) / segments);
            Mesh.emitQuad(pose, c, x, at(y), z,
                    x + Mth.cos(a0) * r, at(y), z + Mth.sin(a0) * r,
                    x + Mth.cos(a1) * r, at(y), z + Mth.sin(a1) * r,
                    x, at(y), z, rgb, 255, 0, 1, 0, light, overlay);
        }
    }

    private static void axialCylinder(PoseStack.Pose pose, VertexConsumer c, float x, float y, float radiusUnits,
                                      float z0, float z1, int rgb, int sides, boolean capFar, int light, int overlay) {
        float r = radiusUnits / 16f;
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2 * i / sides), a1 = (float) (Math.PI * 2 * (i + 1) / sides);
            float mid = (a0 + a1) / 2;
            Mesh.emitQuad(pose, c,
                    x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, at(z0),
                    x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, at(z1),
                    x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, at(z1),
                    x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, at(z0),
                    (i & 1) == 0 ? rgb : shade(rgb), 255, Mth.cos(mid), Mth.sin(mid), 0, light, overlay);
        }
        if (!capFar) return;
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2 * i / sides), a1 = (float) (Math.PI * 2 * (i + 1) / sides);
            Mesh.emitQuad(pose, c, x, y, at(z1),
                    x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, at(z1),
                    x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, at(z1),
                    x, y, at(z1), rgb, 255, 0, 0, 1, light, overlay);
        }
    }

    private static int shade(int rgb) {
        return (((rgb >> 16) & 0xFF) * 4 / 5 << 16) | (((rgb >> 8) & 0xFF) * 4 / 5 << 8) | ((rgb & 0xFF) * 4 / 5);
    }

    private static float at(float blockUnits) {
        return CabinetRenderer.at(blockUnits);
    }
}
