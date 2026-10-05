package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

final class DriverRenderer {
    private DriverRenderer() {}

    enum Kind { WOOFER, TWEETER }

    record Driver(Kind kind, float x, float y, float size) {
        CabinetRenderer.Hole hole() {
            return kind == Kind.WOOFER
                    ? new CabinetRenderer.Hole(x, y, size / 2f, WooferRenderer.openingHalfUnits(size), true)
                    : new CabinetRenderer.Hole(x, y, size, size * 0.62f, false);
        }
    }

    private static final int TRIM_WALL_RGB = 0x232427;
    private static final int FACEPLATE_RGB = 0x2A2C2F;
    private static final int WAVEGUIDE_OUTER_RGB = 0x36393D;
    private static final int WAVEGUIDE_INNER_RGB = 0x202225;
    private static final int DOME_RING_RGB = 0x0E0F10;
    private static final int DOME_EDGE_RGB = 0xB9BDC3;
    private static final int DOME_CROWN_RGB = 0xE4E7EB;

    private static final float PLATE_PROUD = 0.16f;

    static void tweeter(Driver d, float baffleZ, PoseStack.Pose pose, VertexConsumer c,
                        int light, int overlay) {
        float domeBase = 0.12f * d.size() - 0.02f;
        revolve(pose, c, d, baffleZ,
                new float[]{1.00f, 1.00f, 0.66f},
                new float[]{0f, -PLATE_PROUD, -PLATE_PROUD},
                new int[]{TRIM_WALL_RGB, FACEPLATE_RGB}, light, overlay);
        revolve(pose, c, d, baffleZ,
                new float[]{0.66f, 0.50f, 0.46f, 0.40f},
                new float[]{-PLATE_PROUD, domeBase - 0.02f, domeBase, domeBase},
                new int[]{WAVEGUIDE_OUTER_RGB, WAVEGUIDE_INNER_RGB, DOME_RING_RGB}, light, overlay);
        revolve(pose, c, d, baffleZ,
                new float[]{0.40f, 0.33f, 0.22f, 0.00f},
                new float[]{domeBase, domeBase - 0.06f * d.size(), domeBase - 0.11f * d.size(),
                        domeBase - 0.13f * d.size()},
                new int[]{DOME_EDGE_RGB, DOME_CROWN_RGB, DOME_CROWN_RGB}, light, overlay);
    }

    private static void revolve(PoseStack.Pose pose, VertexConsumer c, Driver d, float baffleZ,
                                float[] radii, float[] depths, int[] colours, int light, int overlay) {
        int segments = 28;
        float r = d.size();
        for (int band = 0; band < radii.length - 1; band++) {
            float r0 = radii[band] * r, r1 = radii[band + 1] * r;
            float z0 = depths[band], z1 = depths[band + 1];
            float dr = r1 - r0, dz = z1 - z0;
            float len = Mth.sqrt(dr * dr + dz * dz);
            float nr = len > 0 ? -dz / len : 0f;
            float nz = len > 0 ? dr / len : -1f;
            for (int i = 0; i < segments; i++) {
                float a0 = (float) (Math.PI * 2.0 * i / segments);
                float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                float cm = Mth.cos((a0 + a1) * 0.5f), sm = Mth.sin((a0 + a1) * 0.5f);
                Mesh.emitQuad(pose, c,
                        at(d.x() + c0 * r0), at(d.y() + s0 * r0), at(baffleZ + z0),
                        at(d.x() + c0 * r1), at(d.y() + s0 * r1), at(baffleZ + z1),
                        at(d.x() + c1 * r1), at(d.y() + s1 * r1), at(baffleZ + z1),
                        at(d.x() + c1 * r0), at(d.y() + s1 * r0), at(baffleZ + z0),
                        colours[band], 255, cm * nr, sm * nr, nz, light, overlay);
            }
        }
    }

    private static float at(float blockUnits) {
        return CabinetRenderer.at(blockUnits);
    }
}
