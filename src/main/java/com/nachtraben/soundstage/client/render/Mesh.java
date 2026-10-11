package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class Mesh {
    private Mesh() {}

    record Point2(float x, float y) {}

    private static final float CORNER_FRACTION = 0.24f;
    private static final float STRAIGHT_FRACTION = 1.0f - CORNER_FRACTION;
    private static final float CORNER_ARC_FRACTION = (float) (Math.PI * CORNER_FRACTION / 2.0);
    private static final float QUARTER_FRACTION = 2.0f * STRAIGHT_FRACTION + CORNER_ARC_FRACTION;

    static Point2 roundedPoint(float angle, float halfExtent) {
        return scale(unitRoundedPoint(angle), halfExtent);
    }

    static Point2 scale(Point2 point, float halfExtent) {
        return new Point2(point.x() * halfExtent, point.y() * halfExtent);
    }

    static Point2 unitRoundedPoint(float angle) {
        float normalized = angle / ((float) Math.PI * 2.0f);
        normalized -= Mth.floor(normalized);

        float alongPerimeter = normalized * 4.0f * QUARTER_FRACTION;
        int quarter = (int) (alongPerimeter / QUARTER_FRACTION);
        if (quarter > 3) quarter = 3;
        float along = alongPerimeter - quarter * QUARTER_FRACTION;

        float x, y;
        if (along < STRAIGHT_FRACTION) {
            x = 1.0f;
            y = along;
        } else if (along < STRAIGHT_FRACTION + CORNER_ARC_FRACTION) {
            float a = (along - STRAIGHT_FRACTION) / CORNER_FRACTION;
            x = STRAIGHT_FRACTION + Mth.cos(a) * CORNER_FRACTION;
            y = STRAIGHT_FRACTION + Mth.sin(a) * CORNER_FRACTION;
        } else {
            x = STRAIGHT_FRACTION - (along - STRAIGHT_FRACTION - CORNER_ARC_FRACTION);
            y = 1.0f;
        }

        for (int i = 0; i < quarter; i++) {
            float swap = x;
            x = -y;
            y = swap;
        }
        return new Point2(x, y);
    }

    static void emitCircularSurface(PoseStack.Pose pose, VertexConsumer consumer,
                                            float[] radii, float[] depths, int rgb, int alpha,
                                            int light, int overlay) {
        emitCircularSurface(pose, consumer, radii, depths, rgb, alpha, light, overlay, overlay);
    }

    static void emitCircularSurface(PoseStack.Pose pose, VertexConsumer consumer,
                                            float[] radii, float[] depths, int rgb, int alpha,
                                            int light, int overlayA, int overlayB) {
        int segments = 32;
        for (int ring = 0; ring < radii.length - 1; ring++) {
            for (int segment = 0; segment < segments; segment++) {
                float a0 = (float) (Math.PI * 2.0 * segment / segments);
                float a1 = (float) (Math.PI * 2.0 * (segment + 1) / segments);
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                float mid = (a0 + a1) * 0.5f;
                emitQuad(pose, consumer,
                        c0 * radii[ring], s0 * radii[ring], depths[ring],
                        c0 * radii[ring + 1], s0 * radii[ring + 1], depths[ring + 1],
                        c1 * radii[ring + 1], s1 * radii[ring + 1], depths[ring + 1],
                        c1 * radii[ring], s1 * radii[ring], depths[ring],
                        rgb, alpha, Mth.cos(mid) * 0.25f, Mth.sin(mid) * 0.25f, -0.97f,
                        light, overlayA, overlayB);
            }
        }
    }

    static void emitCylinderShell(PoseStack.Pose pose, VertexConsumer consumer, float radius,
                                          float front, float back, int rgb, int light, int overlay) {
        emitOffsetCylinderShell(pose, consumer, 0, 0, radius, front, back, rgb, light, overlay);
    }

    static void emitOffsetCylinderShell(PoseStack.Pose pose, VertexConsumer consumer,
                                                float centerX, float centerY, float radius,
                                                float front, float back, int rgb,
                                                int light, int overlay) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segments);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float mid = (a0 + a1) * 0.5f;
            emitQuad(pose, consumer,
                    centerX + c0 * radius, centerY + s0 * radius, front,
                    centerX + c0 * radius, centerY + s0 * radius, back,
                    centerX + c1 * radius, centerY + s1 * radius, back,
                    centerX + c1 * radius, centerY + s1 * radius, front,
                    rgb, 255, Mth.cos(mid), Mth.sin(mid), 0, light, overlay);
        }
    }

    static void emitOffsetDisc(PoseStack.Pose pose, VertexConsumer consumer,
                                       float centerX, float centerY, float radius, float z,
                                       int rgb, float normalZ, int light, int overlay) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segments);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
            float x0 = centerX + Mth.cos(a0) * radius;
            float y0 = centerY + Mth.sin(a0) * radius;
            float x1 = centerX + Mth.cos(a1) * radius;
            float y1 = centerY + Mth.sin(a1) * radius;
            emitQuad(pose, consumer,
                    centerX, centerY, z, x1, y1, z, x0, y0, z, centerX, centerY, z,
                    rgb, 255, 0, 0, normalZ, light, overlay);
        }
    }

    static void emitAnnulus(PoseStack.Pose pose, VertexConsumer consumer,
                                    float inner, float outer, float z, int rgb, float normalZ,
                                    int light, int overlay) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segments);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
            emitAnnularSector(pose, consumer, inner, outer, a0, a1, z,
                    rgb, normalZ, light, overlay);
        }
    }

    static void renderRoundedAnnulus(PoseStack.Pose pose, VertexConsumer consumer,
                                             float inner, float outer, float z, int rgb,
                                             float normalZ, int light, int overlay) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segments);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
            Point2 inner0 = roundedPoint(a0, inner);
            Point2 outer0 = roundedPoint(a0, outer);
            Point2 outer1 = roundedPoint(a1, outer);
            Point2 inner1 = roundedPoint(a1, inner);
            emitQuad(pose, consumer,
                    inner0.x(), inner0.y(), z,
                    outer0.x(), outer0.y(), z,
                    outer1.x(), outer1.y(), z,
                    inner1.x(), inner1.y(), z,
                    rgb, 255, 0, 0, normalZ, light, overlay);
        }
    }

    static void emitAnnularSector(PoseStack.Pose pose, VertexConsumer consumer,
                                          float inner, float outer, float a0, float a1, float z,
                                          int rgb, int light, int overlay) {
        emitAnnularSector(pose, consumer, inner, outer, a0, a1, z, rgb, 1, light, overlay);
    }

    static void emitAnnularSector(PoseStack.Pose pose, VertexConsumer consumer,
                                          float inner, float outer, float a0, float a1, float z,
                                          int rgb, float normalZ, int light, int overlay) {
        float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
        float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
        emitQuad(pose, consumer,
                c0 * inner, s0 * inner, z,
                c0 * outer, s0 * outer, z,
                c1 * outer, s1 * outer, z,
                c1 * inner, s1 * inner, z,
                rgb, 255, 0, 0, normalZ, light, overlay);
    }

    static void emitTaperedBeam(PoseStack.Pose pose, VertexConsumer consumer,
                                        float ax, float ay, float az, float aTangent, float aRadial,
                                        float bx, float by, float bz, float bTangent, float bRadial,
                                        float radialX, float radialY, int rgb,
                                        int light, int overlay) {
        float tangentX = -radialY;
        float tangentY = radialX;
        float[][] a = beamCorners(ax, ay, az, aTangent, aRadial,
                tangentX, tangentY, radialX, radialY);
        float[][] b = beamCorners(bx, by, bz, bTangent, bRadial,
                tangentX, tangentY, radialX, radialY);
        float[][] normals = {
                {radialX, radialY, 0}, {-tangentX, -tangentY, 0},
                {-radialX, -radialY, 0}, {tangentX, tangentY, 0}
        };
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) & 3;
            emitQuad(pose, consumer,
                    a[side][0], a[side][1], a[side][2],
                    b[side][0], b[side][1], b[side][2],
                    b[next][0], b[next][1], b[next][2],
                    a[next][0], a[next][1], a[next][2],
                    rgb, 255, normals[side][0], normals[side][1], normals[side][2], light, overlay);
        }
        emitQuad(pose, consumer,
                a[3][0], a[3][1], a[3][2], a[2][0], a[2][1], a[2][2],
                a[1][0], a[1][1], a[1][2], a[0][0], a[0][1], a[0][2],
                rgb, 255, 0, 0, -1, light, overlay);
        emitQuad(pose, consumer,
                b[0][0], b[0][1], b[0][2], b[1][0], b[1][1], b[1][2],
                b[2][0], b[2][1], b[2][2], b[3][0], b[3][1], b[3][2],
                rgb, 255, 0, 0, 1, light, overlay);
    }

    static float[][] beamCorners(float cx, float cy, float z,
                                         float tangentHalf, float radialHalf,
                                         float tangentX, float tangentY,
                                         float radialX, float radialY) {
        return new float[][]{
                {cx + tangentX * tangentHalf + radialX * radialHalf,
                        cy + tangentY * tangentHalf + radialY * radialHalf, z},
                {cx - tangentX * tangentHalf + radialX * radialHalf,
                        cy - tangentY * tangentHalf + radialY * radialHalf, z},
                {cx - tangentX * tangentHalf - radialX * radialHalf,
                        cy - tangentY * tangentHalf - radialY * radialHalf, z},
                {cx + tangentX * tangentHalf - radialX * radialHalf,
                        cy + tangentY * tangentHalf - radialY * radialHalf, z}
        };
    }

    static void emitBox(PoseStack.Pose pose, VertexConsumer consumer,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                int rgb, int light, int overlay) {
        emitQuad(pose, consumer, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0,
                rgb,255, 0,0,-1, light,overlay);
        emitQuad(pose, consumer, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1,
                rgb,255, 0,0,1, light,overlay);
        emitQuad(pose, consumer, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1,
                rgb,255, -1,0,0, light,overlay);
        emitQuad(pose, consumer, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0,
                rgb,255, 1,0,0, light,overlay);
        emitQuad(pose, consumer, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1,
                rgb,255, 0,1,0, light,overlay);
        emitQuad(pose, consumer, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0,
                rgb,255, 0,-1,0, light,overlay);
    }

    private static final Vector3f QUAD_NORMAL = new Vector3f();

    static void applyNormal(VertexConsumer consumer, PoseStack.Pose pose, float nx, float ny, float nz) {
        Vector3f n = pose.transformNormal(nx, ny, nz, QUAD_NORMAL);
        consumer.setNormal(n.x(), n.y(), n.z());
    }

    static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                 float x0,float y0,float z0, float x1,float y1,float z1,
                                 float x2,float y2,float z2, float x3,float y3,float z3,
                                 int rgb, int alpha, float nx, float ny, float nz,
                                 int light, int overlay) {
        Vector3f n = pose.transformNormal(nx, ny, nz, QUAD_NORMAL);
        float tx = n.x(), ty = n.y(), tz = n.z();
        addTintedVertex(consumer, pose, x0,y0,z0, 0,1, rgb,alpha,tx,ty,tz, light,overlay);
        addTintedVertex(consumer, pose, x1,y1,z1, 0,0, rgb,alpha,tx,ty,tz, light,overlay);
        addTintedVertex(consumer, pose, x2,y2,z2, 1,0, rgb,alpha,tx,ty,tz, light,overlay);
        addTintedVertex(consumer, pose, x3,y3,z3, 1,1, rgb,alpha,tx,ty,tz, light,overlay);
    }

    static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer,
                         float x0,float y0,float z0, float x1,float y1,float z1,
                         float x2,float y2,float z2, float x3,float y3,float z3,
                         int rgb, int alpha, float nx, float ny, float nz,
                         int light, int overlayA, int overlayB) {
        Vector3f n = pose.transformNormal(nx, ny, nz, QUAD_NORMAL);
        float tx = n.x(), ty = n.y(), tz = n.z();
        addTintedVertex(consumer, pose, x0,y0,z0, 0,1, rgb,alpha,tx,ty,tz, light,overlayA);
        addTintedVertex(consumer, pose, x1,y1,z1, 0,0, rgb,alpha,tx,ty,tz, light,overlayB);
        addTintedVertex(consumer, pose, x2,y2,z2, 1,0, rgb,alpha,tx,ty,tz, light,overlayB);
        addTintedVertex(consumer, pose, x3,y3,z3, 1,1, rgb,alpha,tx,ty,tz, light,overlayA);
    }

    static int weightOverlay(float weight) {
        return Math.round(Mth.clamp(weight, 0f, 1f) * 255f);
    }

    static void addTintedVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                        float x,float y,float z, float u,float v,
                                        int rgb,int alpha, float nx,float ny,float nz,
                                        int light,int overlay) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .setUv(u, v).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }
}
