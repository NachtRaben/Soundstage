package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class RemoteRenderer {
    private RemoteRenderer() {}

    static final float LEFT = 6.4f, RIGHT = 9.6f, CHEEK = 0.42f;
    static final float TIP = 3.12f, CHEEK_FRONT = 3.3f, TAIL = 12.8f, TAIL_CAP = 0.4f;
    static final float BOTTOM = 7.44f, PLATE = 8.40f, CHEEK_TOP = 8.56f;
    static final float LENS_BACK = 3.95f, LENS_BOTTOM = 7.52f, LENS_INSET = 0.04f;

    private static final float IN0 = LEFT + CHEEK, IN1 = RIGHT - CHEEK, PLATE_END = TAIL - TAIL_CAP;

    private static final ResourceLocation FACE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "block/receiver_face");
    private static final ResourceLocation UNDERSIDE =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "block/receiver_casing");
    static final ResourceLocation LENS_LIGHT =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/led.png");
    static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "textures/block/tube_halo.png");

    private static final float BRUSHED_V0 = 3f / 256f, BRUSHED_V1 = 14f / 256f;
    private static final int PLATE_STRIPS = 3;

    private static final int SLOT_RGB = 0x151618;
    private static final int CAP_SIDE_RGB = 0x8C9197;
    private static final int CAP_TOP_RGB = 0xD6DADE;
    private static final int LED_RGB = 0x24402A;
    private static final int LEGEND_RGB = 0x34373C;
    private static final int LENS_FRONT_RGB = 0x3A0A0D;
    private static final int LENS_TOP_RGB = 0x4C1216;
    private static final int LENS_SIDE_RGB = 0x2E0709;
    private static final int LENS_GLOSS_RGB = 0x7A2228;
    private static final int LAMP_RING_RGB = 0x121314;
    private static final int LAMP_SHELL_RGB = 0x2A2B2D;
    private static final int LAMP_LENS_RGB = 0x4A3A26;
    private static final int LAMP_GLINT_RGB = 0x6B5A44;

    private static final float KEY_W = 0.48f, KEY_GAP = 0.08f, KEY_Z0 = 5.45f, KEY_Z1 = 6.25f, KEY_H = 0.18f;
    private static final float ROCKER_X0 = 6.98f, ROCKER_X1 = 7.46f, ROCKER_Z0 = 9.0f, ROCKER_Z1 = 11.14f,
            ROCKER_H = 0.2f;
    private static final float LAMP_X = 8.68f, LAMP_Z = 4.6f, LAMP_RING = 0.28f, LAMP_R = 0.2f, LAMP_H = 0.14f;
    private static final float ROW_Z = 4.6f, ROW_KEY_D = 0.3f;
    private static final float DPAD_Z = 7.75f, DPAD_R = 0.62f, DPAD_IN = 0.3f, DPAD_H = 0.1f, SELECT_R = 0.24f;
    private static final float CORNER_Z = 7.25f, CORNER_W = 0.4f, CORNER_D = 0.26f;
    private static final float PAD_X0 = 7.66f, PAD_Z0 = 9.0f, PAD_KEY_W = 0.4f, PAD_KEY_D = 0.34f;
    private static final float PAD_PITCH_X = 0.48f, PAD_PITCH_Z = 0.6f, DARK_H = 0.1f;
    private static final int DARK_SIDE_RGB = 0x1B1D20, DARK_TOP_RGB = 0x313438, DARK_EDGE_RGB = 0x3E4146;
    private static final int ARROW_RGB = 0xA2A7AC;
    private static final float SLOT = 0.05f, BEVEL = 0.05f;
    private static final float CLEAR_EDGE = 0.04f, WALL_INSET = 0.01f;
    private static final int INNER_WALL_RGB = 0x3A3D40;
    private static final float GLYPH = 0.11f;

    record Panel(boolean playing, boolean loop, boolean shuffle, boolean locked, boolean signal) {
        static final Panel DARK = new Panel(false, false, false, false, false);

        boolean latched(int key) {
            return switch (key) {
                case 0 -> playing;
                case 1 -> loop;
                case 2 -> shuffle;
                default -> locked;
            };
        }
    }

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int CAP_SIDE_PRESSED_RGB = 0x5E6368;
    private static final int CAP_TOP_PRESSED_RGB = 0x9A9FA4;
    private static final int LED_LIT_RGB = 0x6CFF7A;
    private static final int LAMP_LIT_RGB = 0xFFB347;
    private static final int LAMP_GLINT_LIT_RGB = 0xFFE6B8;
    private static final float KEY_IN_H = 0.07f;

    static void body(SpeakerFinish finish, GeometrySink sink, int light, int overlay) {
        if (!finish.isTransparent()) trim(finish, sink, light, overlay);
        if (finish.isTransparent()) {
            sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, c) -> {
                CabinetRenderer.clearBoxEdges(pose, c, LEFT, BOTTOM, CHEEK_FRONT, IN0, CHEEK_TOP, PLATE_END, CLEAR_EDGE,
                        light, overlay);
                CabinetRenderer.clearBoxEdges(pose, c, IN1, BOTTOM, CHEEK_FRONT, RIGHT, CHEEK_TOP, PLATE_END,
                        CLEAR_EDGE, light, overlay);
                CabinetRenderer.clearBoxEdges(pose, c, LEFT, BOTTOM, PLATE_END, RIGHT, CHEEK_TOP, TAIL, CLEAR_EDGE,
                        light, overlay);
            }));
        }
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, c) -> innerWalls(pose, c, light, overlay)));
        sink.emit(AtlasSprites.CUTOUT, (pose, c) -> {
            plate(pose, c, 0xFFFFFF, light, overlay);
            underside(pose, c, light, overlay);
        });
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, c) -> {
            lens(pose, c, light, overlay);
            topRow(pose, c, light, overlay);
            dpad(pose, c, light, overlay);
            numberPad(pose, c, light, overlay);
            rocker(pose, c, light, overlay);
        }));
    }

    static void panel(Panel panel, GeometrySink sink, int light, int overlay) {
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, c) -> {
            lamp(pose, c, panel.signal(), light, overlay);
            for (int key = 0; key < 4; key++) key(pose, c, key, panel.latched(key), light, overlay);
        }));
    }

    static void panelGlow(Panel panel, GeometrySink sink, RenderType haloType, int overlay) {
        sink.emit(haloType, (pose, c) -> {
            for (int key = 0; key < 4; key++) {
                if (!panel.latched(key)) continue;
                float cx = keyCenterX(key), cz = KEY_Z0 + 0.13f, y = PLATE + KEY_IN_H + 0.01f, r = 0.32f;
                facingUp(pose, c, cx - r, cz - r * 0.7f, cx + r, cz + r * 0.7f, y, 0x4C, 0xC8, 0x58,
                        FULL_BRIGHT, overlay);
            }
            if (panel.signal()) {
                float r = 0.5f;
                facingUp(pose, c, LAMP_X - r, LAMP_Z - r, LAMP_X + r, LAMP_Z + r, PLATE + LAMP_H + 0.01f,
                        0xE0, 0x90, 0x30, FULL_BRIGHT, overlay);
            }
        });
    }

    private static float keyCenterX(int index) {
        float total = 4 * KEY_W + 3 * KEY_GAP;
        return (LEFT + RIGHT) / 2f - total / 2f + index * (KEY_W + KEY_GAP) + KEY_W / 2f;
    }

    static final float ICON_LEFT = 5.8f, ICON_RIGHT = 10.2f, ICON_CHEEK = 0.8f, ICON_TIP = 3.0f, ICON_LENS_BACK = 4.1f;
    static final float ICON_TAIL = 13.0f, ICON_TAIL_CAP = 1.0f, ICON_BOTTOM = 7.4f, ICON_PLATE = 8.4f, ICON_TOP = 8.62f;
    private static final float ICON_EDGE = 0.2f;
    private static final float ICON_IN0 = ICON_LEFT + ICON_CHEEK, ICON_IN1 = ICON_RIGHT - ICON_CHEEK;
    private static final float ICON_PLATE_END = ICON_TAIL - ICON_TAIL_CAP;
    private static final float ICON_KEY_Z0 = 5.4f, ICON_KEY_Z1 = 6.6f, ICON_KEY_GAP = 0.22f, ICON_KEY_MARGIN = 0.2f;
    private static final float ICON_LAMP_Z = 4.75f, ICON_LAMP_RING = 0.5f, ICON_LAMP_R = 0.36f;
    private static final float ICON_DPAD_Z = 8.7f, ICON_DPAD_R = 1.1f, ICON_DPAD_IN = 0.5f, ICON_SELECT_R = 0.4f;

    static void icon(SpeakerFinish finish, GeometrySink sink, int light, int overlay) {
        if (!finish.isTransparent()) iconTrim(finish, sink, light, overlay);
        sink.emit(AtlasSprites.CUTOUT, AtlasSprites.white((pose, c) -> {
            if (finish.isTransparent()) {
                CabinetRenderer.clearBoxEdges(pose, c, ICON_LEFT, ICON_BOTTOM, ICON_LENS_BACK, ICON_IN0, ICON_TOP,
                        ICON_PLATE_END, ICON_EDGE, light, overlay);
                CabinetRenderer.clearBoxEdges(pose, c, ICON_IN1, ICON_BOTTOM, ICON_LENS_BACK, ICON_RIGHT, ICON_TOP,
                        ICON_PLATE_END, ICON_EDGE, light, overlay);
                CabinetRenderer.clearBoxEdges(pose, c, ICON_LEFT, ICON_BOTTOM, ICON_PLATE_END, ICON_RIGHT,
                        ICON_TOP, ICON_TAIL, ICON_EDGE, light, overlay);
            }
            iconLens(pose, c, light, overlay);
            iconGrooves(pose, c, light, overlay);
            iconLamp(pose, c, light, overlay);
            iconKeys(pose, c, light, overlay);
            iconDpad(pose, c, light, overlay);
        }));
        sink.emit(AtlasSprites.CUTOUT, (pose, c) -> {
            brushed(pose, c, ICON_IN0, ICON_IN1, ICON_LENS_BACK, ICON_PLATE_END, ICON_PLATE, 0xFFFFFF, light, overlay);
            if (finish.isTransparent()) iconCore(pose, c, light, overlay);
        });
    }

    static void iconClearTrim(SpeakerFinish finish, GeometrySink sink, int light, int overlay) {
        CabinetRenderer.renderClearBoxPanes(finish, ICON_LEFT, ICON_BOTTOM, ICON_LENS_BACK, ICON_IN0, ICON_TOP,
                ICON_PLATE_END, ICON_EDGE, false, sink, light, overlay);
        CabinetRenderer.renderClearBoxPanes(finish, ICON_IN1, ICON_BOTTOM, ICON_LENS_BACK, ICON_RIGHT, ICON_TOP,
                ICON_PLATE_END, ICON_EDGE, false, sink, light, overlay);
        CabinetRenderer.renderClearBoxPanes(finish, ICON_LEFT, ICON_BOTTOM, ICON_PLATE_END, ICON_RIGHT,
                ICON_TOP, ICON_TAIL, ICON_EDGE, true, sink, light, overlay);
    }

    private static void iconTrim(SpeakerFinish finish, GeometrySink sink, int light, int overlay) {
        CabinetRenderer.renderWoodBox(finish, ICON_LEFT, ICON_BOTTOM, ICON_LENS_BACK, ICON_IN0, ICON_TOP,
                ICON_PLATE_END, sink, light, overlay);
        CabinetRenderer.renderWoodBox(finish, ICON_IN1, ICON_BOTTOM, ICON_LENS_BACK, ICON_RIGHT, ICON_TOP,
                ICON_PLATE_END, sink, light, overlay);
        CabinetRenderer.renderWoodBox(finish, ICON_LEFT, ICON_BOTTOM, ICON_PLATE_END, ICON_RIGHT, ICON_TOP, ICON_TAIL,
                sink, light, overlay);
    }

    private static void iconCore(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        TextureAtlasSprite metal = AtlasSprites.sprite(UNDERSIDE);
        float y = ICON_BOTTOM + ICON_EDGE + 0.02f;
        texturedUp(pose, c, metal, ICON_LEFT + ICON_EDGE, ICON_LENS_BACK + ICON_EDGE, ICON_IN0, ICON_PLATE_END, y,
                0f, 0f, ICON_CHEEK / 16f, 0.5f, 0xFFFFFF, light, overlay);
        texturedUp(pose, c, metal, ICON_IN1, ICON_LENS_BACK + ICON_EDGE, ICON_RIGHT - ICON_EDGE, ICON_PLATE_END, y,
                0f, 0f, ICON_CHEEK / 16f, 0.5f, 0xFFFFFF, light, overlay);
        texturedUp(pose, c, metal, ICON_LEFT + ICON_EDGE, ICON_PLATE_END, ICON_RIGHT - ICON_EDGE,
                ICON_TAIL - ICON_EDGE, y, 0f, 0f, 0.25f, ICON_TAIL_CAP / 16f, 0xFFFFFF, light, overlay);
    }

    private static void iconLens(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        Mesh.emitBox(pose, c, at(ICON_LEFT), at(ICON_BOTTOM), at(ICON_TIP), at(ICON_RIGHT), at(ICON_TOP - 0.04f),
                at(ICON_LENS_BACK), LENS_SIDE_RGB, light, overlay);
        quadUp(pose, c, ICON_LEFT, ICON_TIP, ICON_RIGHT, ICON_LENS_BACK, ICON_TOP - 0.038f, LENS_TOP_RGB,
                light, overlay);
        quadUp(pose, c, ICON_LEFT + 0.3f, ICON_TIP + 0.25f, ICON_RIGHT - 0.3f, ICON_TIP + 0.45f, ICON_TOP - 0.036f,
                LENS_GLOSS_RGB, light, overlay);
    }

    private static void iconLamp(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float x = ICON_IN1 - 0.55f;
        discUp(pose, c, x, ICON_LAMP_Z, ICON_LAMP_RING, ICON_PLATE + 0.002f, LAMP_RING_RGB, light, overlay);
        shell(pose, c, x, ICON_LAMP_Z, ICON_LAMP_R, ICON_PLATE, ICON_PLATE + 0.1f, LAMP_SHELL_RGB, light, overlay);
        discUp(pose, c, x, ICON_LAMP_Z, ICON_LAMP_R, ICON_PLATE + 0.1f, LAMP_LENS_RGB, light, overlay);
    }

    private static void iconKeys(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float width = (ICON_IN1 - ICON_IN0 - 2 * ICON_KEY_MARGIN - 3 * ICON_KEY_GAP) / 4f;
        quadUp(pose, c, ICON_IN0 + ICON_KEY_MARGIN - ICON_KEY_GAP, ICON_KEY_Z0 - ICON_KEY_GAP,
                ICON_IN1 - ICON_KEY_MARGIN + ICON_KEY_GAP, ICON_KEY_Z1 + ICON_KEY_GAP, ICON_PLATE + 0.002f,
                DARK_SIDE_RGB, light, overlay);
        for (int key = 0; key < 4; key++) {
            float x0 = ICON_IN0 + ICON_KEY_MARGIN + key * (width + ICON_KEY_GAP), x1 = x0 + width;
            Mesh.emitBox(pose, c, at(x0), at(ICON_PLATE), at(ICON_KEY_Z0), at(x1), at(ICON_PLATE + 0.14f),
                    at(ICON_KEY_Z1), CAP_SIDE_RGB, light, overlay);
            quadUp(pose, c, x0, ICON_KEY_Z0, x1, ICON_KEY_Z1, ICON_PLATE + 0.141f, CAP_TOP_RGB, light, overlay);
        }
    }

    private static void iconGrooves(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float w = 0.16f, y = ICON_PLATE + 0.002f;
        quadUp(pose, c, ICON_IN0, ICON_LENS_BACK, ICON_IN0 + w, ICON_PLATE_END, y, SLOT_RGB, light, overlay);
        quadUp(pose, c, ICON_IN1 - w, ICON_LENS_BACK, ICON_IN1, ICON_PLATE_END, y, SLOT_RGB, light, overlay);
        quadUp(pose, c, ICON_IN0, ICON_PLATE_END - w, ICON_IN1, ICON_PLATE_END, y, SLOT_RGB, light, overlay);
    }

    private static void iconDpad(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float cx = (ICON_LEFT + ICON_RIGHT) / 2f, top = ICON_PLATE + 0.1f;
        discUp(pose, c, cx, ICON_DPAD_Z, ICON_DPAD_R + 0.08f, ICON_PLATE + 0.002f, SLOT_RGB, light, overlay);
        for (int k = 0; k < 4; k++) {
            float a0 = (float) (Math.PI / 2 * k), a1 = (float) (Math.PI / 2 * (k + 1));
            sectorUp(pose, c, cx, ICON_DPAD_Z, ICON_DPAD_IN, ICON_DPAD_R, a0, a1, top, DARK_TOP_RGB, light, overlay);
            arcWall(pose, c, cx, ICON_DPAD_Z, ICON_DPAD_R, a0, a1, ICON_PLATE, top, DARK_SIDE_RGB, light, overlay);
        }
        shell(pose, c, cx, ICON_DPAD_Z, ICON_SELECT_R, ICON_PLATE, ICON_PLATE + 0.16f, CAP_SIDE_RGB, light, overlay);
        discUp(pose, c, cx, ICON_DPAD_Z, ICON_SELECT_R, ICON_PLATE + 0.16f, CAP_TOP_RGB, light, overlay);
    }

    static void clearTrim(SpeakerFinish finish, GeometrySink sink, int light, int overlay) {
        CabinetRenderer.renderClearBoxPanes(finish, LEFT, BOTTOM, CHEEK_FRONT, IN0, CHEEK_TOP, PLATE_END,
                CLEAR_EDGE, false, sink, light, overlay);
        CabinetRenderer.renderClearBoxPanes(finish, IN1, BOTTOM, CHEEK_FRONT, RIGHT, CHEEK_TOP, PLATE_END,
                CLEAR_EDGE, false, sink, light, overlay);
        CabinetRenderer.renderClearBoxPanes(finish, LEFT, BOTTOM, PLATE_END, RIGHT, CHEEK_TOP, TAIL, CLEAR_EDGE, true,
                sink, light, overlay);
    }

    private static void innerWalls(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float x0 = IN0 + WALL_INSET, x1 = IN1 - WALL_INSET, y0 = BOTTOM + WALL_INSET, y1 = PLATE - WALL_INSET;
        float z0 = CHEEK_FRONT, z1 = PLATE_END - WALL_INSET;
        Mesh.emitQuad(pose, c, at(x0), at(y0), at(z0), at(x0), at(y1), at(z0), at(x0), at(y1), at(z1), at(x0),
                at(y0), at(z1), INNER_WALL_RGB, 255, -1, 0, 0, light, overlay);
        Mesh.emitQuad(pose, c, at(x1), at(y0), at(z1), at(x1), at(y1), at(z1), at(x1), at(y1), at(z0), at(x1),
                at(y0), at(z0), INNER_WALL_RGB, 255, 1, 0, 0, light, overlay);
        Mesh.emitQuad(pose, c, at(x0), at(y0), at(z1), at(x0), at(y1), at(z1), at(x1), at(y1), at(z1), at(x1),
                at(y0), at(z1), INNER_WALL_RGB, 255, 0, 0, 1, light, overlay);
    }

    static void trim(SpeakerFinish finish, GeometrySink sink, int light, int overlay) {
        CabinetRenderer.renderWoodBox(finish, LEFT, BOTTOM, CHEEK_FRONT, IN0, CHEEK_TOP, PLATE_END, sink,
                light, overlay);
        CabinetRenderer.renderWoodBox(finish, IN1, BOTTOM, CHEEK_FRONT, RIGHT, CHEEK_TOP, PLATE_END, sink,
                light, overlay);
        CabinetRenderer.renderWoodBox(finish, LEFT, BOTTOM, PLATE_END, RIGHT, CHEEK_TOP, TAIL, sink, light, overlay);
    }

    static void glow(GeometrySink sink, RenderType lensType, RenderType haloType, int overlay) {
        int light = 0x00F000F0;
        sink.emit(lensType, (pose, c) -> {
            float x0 = LEFT + LENS_INSET, x1 = RIGHT - LENS_INSET, top = CHEEK_TOP - LENS_INSET;
            facingFront(pose, c, x0, LENS_BOTTOM, x1, top, TIP - 0.004f, 255, 54, 40, light, overlay);
            facingUp(pose, c, x0, TIP, x1, CHEEK_FRONT, top + 0.004f, 255, 54, 40, light, overlay);
            facingUp(pose, c, IN0, CHEEK_FRONT, IN1, LENS_BACK, PLATE + 0.004f, 255, 54, 40, light, overlay);
        });
        sink.emit(haloType, (pose, c) -> {
            float cx = (LEFT + RIGHT) / 2f, cy = (LENS_BOTTOM + CHEEK_TOP) / 2f;
            facingUp(pose, c, cx - 2.0f, TIP - 1.0f, cx + 2.0f, LENS_BACK + 1.2f, CHEEK_TOP + 0.02f, 210, 34, 26,
                    light, overlay);
            facingFront(pose, c, cx - 2.0f, cy - 1.2f, cx + 2.0f, cy + 1.2f, TIP - 0.03f, 210, 34, 26, light, overlay);
        });
    }

    private static void plate(PoseStack.Pose pose, VertexConsumer c, int rgb, int light, int overlay) {
        brushed(pose, c, IN0, IN1, LENS_BACK, PLATE_END, PLATE, rgb, light, overlay);
    }

    private static void brushed(PoseStack.Pose pose, VertexConsumer c, float left, float right, float front, float back,
                                float y, int rgb, int light, int overlay) {
        TextureAtlasSprite face = AtlasSprites.sprite(FACE);
        float strip = (right - left) / PLATE_STRIPS;
        float u1 = (back - front) / 16f;
        for (int i = 0; i < PLATE_STRIPS; i++) {
            float x0 = left + i * strip, x1 = x0 + strip;
            texturedUp(pose, c, face, x0, front, x1, back, y, 0f, BRUSHED_V0, u1, BRUSHED_V1, rgb, light, overlay);
        }
    }

    private static void underside(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        TextureAtlasSprite metal = AtlasSprites.sprite(UNDERSIDE);
        float u1 = (PLATE_END - CHEEK_FRONT) / 16f, v1 = (IN1 - IN0) / 16f;
        float su0 = u(metal, 0f), su1 = u(metal, u1), sv0 = v(metal, 0f), sv1 = v(metal, v1);
        vertex(pose, c, IN0, BOTTOM, PLATE_END, su1, sv0, 0, -1, 0, 0xFFFFFF, light, overlay);
        vertex(pose, c, IN0, BOTTOM, CHEEK_FRONT, su0, sv0, 0, -1, 0, 0xFFFFFF, light, overlay);
        vertex(pose, c, IN1, BOTTOM, CHEEK_FRONT, su0, sv1, 0, -1, 0, 0xFFFFFF, light, overlay);
        vertex(pose, c, IN1, BOTTOM, PLATE_END, su1, sv1, 0, -1, 0, 0xFFFFFF, light, overlay);
    }

    private static void texturedUp(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                                   float x0, float z0, float x1, float z1, float y,
                                   float fu0, float fv0, float fu1, float fv1, int rgb, int light, int overlay) {
        float su0 = u(s, fu0), su1 = u(s, fu1), sv0 = v(s, fv0), sv1 = v(s, fv1);
        vertex(pose, c, x0, y, z1, su1, sv0, 0, 1, 0, rgb, light, overlay);
        vertex(pose, c, x1, y, z1, su1, sv1, 0, 1, 0, rgb, light, overlay);
        vertex(pose, c, x1, y, z0, su0, sv1, 0, 1, 0, rgb, light, overlay);
        vertex(pose, c, x0, y, z0, su0, sv0, 0, 1, 0, rgb, light, overlay);
    }

    private static void lens(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float x0 = LEFT + LENS_INSET, x1 = RIGHT - LENS_INSET, top = CHEEK_TOP - LENS_INSET;
        Mesh.emitBox(pose, c, at(x0), at(LENS_BOTTOM), at(TIP), at(x1), at(top), at(CHEEK_FRONT),
                LENS_SIDE_RGB, light, overlay);
        quadFront(pose, c, x0, LENS_BOTTOM, x1, top, TIP - 0.002f, LENS_FRONT_RGB, light, overlay);
        quadUp(pose, c, x0, TIP, x1, CHEEK_FRONT, top + 0.002f, LENS_TOP_RGB, light, overlay);
        quadFront(pose, c, x0 + 0.12f, top - 0.2f, x1 - 0.12f, top - 0.13f, TIP - 0.004f, LENS_GLOSS_RGB,
                light, overlay);
        quadUp(pose, c, IN0, CHEEK_FRONT, IN1, LENS_BACK, PLATE, LENS_TOP_RGB, light, overlay);
        quadUp(pose, c, IN0, CHEEK_FRONT + 0.05f, IN1, CHEEK_FRONT + 0.1f, PLATE + 0.002f, LENS_GLOSS_RGB,
                light, overlay);
        quadDown(pose, c, IN0, CHEEK_FRONT, IN1, LENS_BACK, LENS_BOTTOM, LENS_SIDE_RGB, light, overlay);
        quadUp(pose, c, IN0, LENS_BACK, IN1, LENS_BACK + 0.06f, PLATE + 0.002f, SLOT_RGB, light, overlay);
    }

    private static void lamp(PoseStack.Pose pose, VertexConsumer c, boolean lit, int light, int overlay) {
        int lensLight = lit ? FULL_BRIGHT : light;
        discUp(pose, c, LAMP_X, LAMP_Z, LAMP_RING, PLATE + 0.002f, LAMP_RING_RGB, light, overlay);
        shell(pose, c, LAMP_X, LAMP_Z, LAMP_R, PLATE, PLATE + LAMP_H, LAMP_SHELL_RGB, light, overlay);
        discUp(pose, c, LAMP_X, LAMP_Z, LAMP_R, PLATE + LAMP_H, lit ? LAMP_LIT_RGB : LAMP_LENS_RGB, lensLight, overlay);
        discUp(pose, c, LAMP_X - 0.06f, LAMP_Z - 0.06f, 0.06f, PLATE + LAMP_H + 0.002f,
                lit ? LAMP_GLINT_LIT_RGB : LAMP_GLINT_RGB, lensLight, overlay);
    }

    private static void key(PoseStack.Pose pose, VertexConsumer c, int index, boolean latched, int light, int overlay) {
        float cx = keyCenterX(index), x0 = cx - KEY_W / 2f, x1 = cx + KEY_W / 2f;
        float height = latched ? KEY_IN_H : KEY_H;
        quadUp(pose, c, x0 - SLOT, KEY_Z0 - SLOT, x1 + SLOT, KEY_Z1 + SLOT, PLATE + 0.002f, SLOT_RGB, light, overlay);
        Mesh.emitBox(pose, c, at(x0), at(PLATE), at(KEY_Z0), at(x1), at(PLATE + height), at(KEY_Z1),
                latched ? CAP_SIDE_PRESSED_RGB : CAP_SIDE_RGB, light, overlay);
        float top = PLATE + height;
        quadUp(pose, c, x0 + BEVEL, KEY_Z0 + BEVEL, x1 - BEVEL, KEY_Z1 - BEVEL, top + 0.001f,
                latched ? CAP_TOP_PRESSED_RGB : CAP_TOP_RGB, light, overlay);
        quadUp(pose, c, cx - 0.1f, KEY_Z0 + 0.09f, cx + 0.1f, KEY_Z0 + 0.17f, top + 0.002f,
                latched ? LED_LIT_RGB : LED_RGB, latched ? FULL_BRIGHT : light, overlay);
        legend(pose, c, index, cx, KEY_Z0 + 0.5f, top + 0.003f, light, overlay);
    }

    private static void legend(PoseStack.Pose pose, VertexConsumer c, int index, float cx, float gz, float y,
                               int light, int overlay) {
        switch (index) {
            case 0 -> {
                triangleRight(pose, c, cx, gz, -1.0f, 0.2f, 0.8f, y, light, overlay);
                bar(pose, c, cx, gz, 0.45f, 0.7f, -0.8f, 0.8f, y, light, overlay);
                bar(pose, c, cx, gz, 0.9f, 1.15f, -0.8f, 0.8f, y, light, overlay);
            }
            case 1 -> {
                bar(pose, c, cx, gz, -1.0f, 1.0f, -0.7f, -0.45f, y, light, overlay);
                bar(pose, c, cx, gz, -1.0f, -0.75f, -0.45f, 0.45f, y, light, overlay);
                bar(pose, c, cx, gz, 0.75f, 1.0f, -0.45f, 0.45f, y, light, overlay);
                bar(pose, c, cx, gz, -1.0f, 0.1f, 0.45f, 0.7f, y, light, overlay);
                triangleRight(pose, c, cx, gz, 0.1f, 0.75f, 0.42f, y, light, overlay);
            }
            case 2 -> {
                stroke(pose, c, cx, gz, -1.0f, -0.6f, 0.45f, 0.6f, 0.14f, y, light, overlay);
                stroke(pose, c, cx, gz, -1.0f, 0.6f, 0.45f, -0.6f, 0.14f, y, light, overlay);
                triangleRight(pose, c, cx, gz, 0.45f, 1.05f, 0.4f, y, light, overlay, 0.6f);
                triangleRight(pose, c, cx, gz, 0.45f, 1.05f, 0.4f, y, light, overlay, -0.6f);
            }
            default -> {
                bar(pose, c, cx, gz, -0.6f, 0.6f, -0.8f, 0.1f, y, light, overlay);
                bar(pose, c, cx, gz, -0.42f, -0.24f, 0.1f, 0.75f, y, light, overlay);
                bar(pose, c, cx, gz, 0.24f, 0.42f, 0.1f, 0.75f, y, light, overlay);
                bar(pose, c, cx, gz, -0.42f, 0.42f, 0.6f, 0.78f, y, light, overlay);
            }
        }
    }

    private static void stroke(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, float ax, float ay,
                               float bx, float by, float half, float y, int light, int overlay) {
        float dx = bx - ax, dy = by - ay, len = Mth.sqrt(dx * dx + dy * dy);
        float px = -dy / len * half, py = dx / len * half;
        Mesh.emitQuad(pose, c,
                at(cx + (ax - px) * GLYPH), at(y), at(cz - (ay - py) * GLYPH),
                at(cx + (bx - px) * GLYPH), at(y), at(cz - (by - py) * GLYPH),
                at(cx + (bx + px) * GLYPH), at(y), at(cz - (by + py) * GLYPH),
                at(cx + (ax + px) * GLYPH), at(y), at(cz - (ay + py) * GLYPH),
                LEGEND_RGB, 255, 0, 1, 0, light, overlay);
    }

    private static void topRow(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        darkKey(pose, c, IN0 + 0.16f, ROW_Z - ROW_KEY_D / 2f, IN0 + 0.74f, ROW_Z + ROW_KEY_D / 2f, light, overlay);
        darkKey(pose, c, 7.74f, ROW_Z - ROW_KEY_D / 2f, 8.18f, ROW_Z + ROW_KEY_D / 2f, light, overlay);
    }

    private static void dpad(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        float cx = (LEFT + RIGHT) / 2f, top = PLATE + DPAD_H;
        discUp(pose, c, cx, DPAD_Z, DPAD_R + 0.05f, PLATE + 0.002f, SLOT_RGB, light, overlay);
        for (int k = 0; k < 4; k++) {
            float mid = (float) (Math.PI / 2 * k);
            float a0 = mid - (float) Math.toRadians(41), a1 = mid + (float) Math.toRadians(41);
            sectorUp(pose, c, cx, DPAD_Z, DPAD_IN, DPAD_R, a0, a1, top, DARK_TOP_RGB, light, overlay);
            arcWall(pose, c, cx, DPAD_Z, DPAD_R, a0, a1, PLATE, top, DARK_SIDE_RGB, light, overlay);
            float ux = Mth.cos(mid), uz = Mth.sin(mid), px = -uz, pz = ux;
            float tip = DPAD_R - 0.1f, base = DPAD_IN + 0.12f, half = 0.07f, y = top + 0.002f;
            Mesh.emitQuad(pose, c,
                    at(cx + ux * base + px * half), at(y), at(DPAD_Z + uz * base + pz * half),
                    at(cx + ux * tip), at(y), at(DPAD_Z + uz * tip),
                    at(cx + ux * base - px * half), at(y), at(DPAD_Z + uz * base - pz * half),
                    at(cx + ux * base - px * half), at(y), at(DPAD_Z + uz * base - pz * half),
                    ARROW_RGB, 255, 0, 1, 0, light, overlay);
        }
        shell(pose, c, cx, DPAD_Z, SELECT_R, PLATE, PLATE + KEY_H, CAP_SIDE_RGB, light, overlay);
        discUp(pose, c, cx, DPAD_Z, SELECT_R, PLATE + KEY_H, CAP_TOP_RGB, light, overlay);
        darkKey(pose, c, IN0 + 0.14f, CORNER_Z - CORNER_D / 2f, IN0 + 0.14f + CORNER_W, CORNER_Z + CORNER_D / 2f,
                light, overlay);
        darkKey(pose, c, IN1 - 0.14f - CORNER_W, CORNER_Z - CORNER_D / 2f, IN1 - 0.14f, CORNER_Z + CORNER_D / 2f,
                light, overlay);
    }

    private static void numberPad(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 3 && col != 1) continue;
                float x0 = PAD_X0 + col * PAD_PITCH_X, z0 = PAD_Z0 + row * PAD_PITCH_Z;
                darkKey(pose, c, x0, z0, x0 + PAD_KEY_W, z0 + PAD_KEY_D, light, overlay);
            }
        }
    }

    private static void darkKey(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1,
                                int light, int overlay) {
        quadUp(pose, c, x0 - 0.03f, z0 - 0.03f, x1 + 0.03f, z1 + 0.03f, PLATE + 0.002f, SLOT_RGB, light, overlay);
        Mesh.emitBox(pose, c, at(x0), at(PLATE), at(z0), at(x1), at(PLATE + DARK_H), at(z1), DARK_SIDE_RGB,
                light, overlay);
        quadUp(pose, c, x0, z0, x1, z1, PLATE + DARK_H + 0.001f, DARK_EDGE_RGB, light, overlay);
        quadUp(pose, c, x0 + 0.04f, z0 + 0.04f, x1 - 0.04f, z1 - 0.04f, PLATE + DARK_H + 0.002f, DARK_TOP_RGB,
                light, overlay);
    }

    private static void sectorUp(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, float rIn, float rOut,
                                 float a0, float a1, float y, int rgb, int light, int overlay) {
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            float b0 = a0 + (a1 - a0) * i / steps, b1 = a0 + (a1 - a0) * (i + 1) / steps;
            Mesh.emitQuad(pose, c,
                    at(cx + Mth.cos(b0) * rIn), at(y), at(cz + Mth.sin(b0) * rIn),
                    at(cx + Mth.cos(b0) * rOut), at(y), at(cz + Mth.sin(b0) * rOut),
                    at(cx + Mth.cos(b1) * rOut), at(y), at(cz + Mth.sin(b1) * rOut),
                    at(cx + Mth.cos(b1) * rIn), at(y), at(cz + Mth.sin(b1) * rIn),
                    rgb, 255, 0, 1, 0, light, overlay);
        }
    }

    private static void arcWall(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, float r, float a0, float a1,
                                float y0, float y1, int rgb, int light, int overlay) {
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            float b0 = a0 + (a1 - a0) * i / steps, b1 = a0 + (a1 - a0) * (i + 1) / steps, mid = (b0 + b1) / 2f;
            Mesh.emitQuad(pose, c,
                    at(cx + Mth.cos(b0) * r), at(y0), at(cz + Mth.sin(b0) * r),
                    at(cx + Mth.cos(b0) * r), at(y1), at(cz + Mth.sin(b0) * r),
                    at(cx + Mth.cos(b1) * r), at(y1), at(cz + Mth.sin(b1) * r),
                    at(cx + Mth.cos(b1) * r), at(y0), at(cz + Mth.sin(b1) * r),
                    rgb, 255, Mth.cos(mid), 0, Mth.sin(mid), light, overlay);
        }
    }

    private static void rocker(PoseStack.Pose pose, VertexConsumer c, int light, int overlay) {
        cap(pose, c, ROCKER_X0, ROCKER_Z0, ROCKER_X1, ROCKER_Z1, ROCKER_H, light, overlay);
        float top = PLATE + ROCKER_H, cx = (ROCKER_X0 + ROCKER_X1) / 2f, mid = (ROCKER_Z0 + ROCKER_Z1) / 2f;
        quadUp(pose, c, ROCKER_X0 + BEVEL, mid - 0.02f, ROCKER_X1 - BEVEL, mid + 0.02f, top + 0.003f, LEGEND_RGB,
                light, overlay);
        float plus = ROCKER_Z0 + 0.5f, minus = ROCKER_Z1 - 0.5f, y = top + 0.003f;
        bar(pose, c, cx, plus, -0.6f, 0.6f, -0.13f, 0.13f, y, light, overlay);
        bar(pose, c, cx, plus, -0.13f, 0.13f, -0.6f, 0.6f, y, light, overlay);
        bar(pose, c, cx, minus, -0.6f, 0.6f, -0.13f, 0.13f, y, light, overlay);
    }

    private static void cap(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1, float height,
                            int light, int overlay) {
        quadUp(pose, c, x0 - SLOT, z0 - SLOT, x1 + SLOT, z1 + SLOT, PLATE + 0.002f, SLOT_RGB, light, overlay);
        Mesh.emitBox(pose, c, at(x0), at(PLATE), at(z0), at(x1), at(PLATE + height), at(z1), CAP_SIDE_RGB,
                light, overlay);
        quadUp(pose, c, x0 + BEVEL, z0 + BEVEL, x1 - BEVEL, z1 - BEVEL, PLATE + height + 0.001f, CAP_TOP_RGB,
                light, overlay);
    }

    private static void bar(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, float gx0, float gx1,
                            float gy0, float gy1, float y, int light, int overlay) {
        quadUp(pose, c, cx + gx0 * GLYPH, cz - gy1 * GLYPH, cx + gx1 * GLYPH, cz - gy0 * GLYPH, y, LEGEND_RGB,
                light, overlay);
    }

    private static void triangleRight(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, float gx0, float gx1,
                                      float half, float y, int light, int overlay) {
        triangleRight(pose, c, cx, cz, gx0, gx1, half, y, light, overlay, 0f);
    }

    private static void triangleRight(PoseStack.Pose pose, VertexConsumer c, float cx, float cz, float gx0, float gx1,
                                      float half, float y, int light, int overlay, float gy) {
        float mz = cz - gy * GLYPH;
        float xa = cx + gx0 * GLYPH, xb = cx + gx1 * GLYPH, za = mz - half * GLYPH, zb = mz + half * GLYPH;
        Mesh.emitQuad(pose, c, at(xa), at(y), at(zb), at(xb), at(y), at(mz), at(xa), at(y), at(za), at(xa),
                at(y), at(za), LEGEND_RGB, 255, 0, 1, 0, light, overlay);
    }

    private static void discUp(PoseStack.Pose pose, VertexConsumer c, float x, float z, float r, float y, int rgb,
                               int light, int overlay) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2 * i / segments), a1 = (float) (Math.PI * 2 * (i + 1) / segments);
            Mesh.emitQuad(pose, c, at(x), at(y), at(z),
                    at(x + Mth.cos(a1) * r), at(y), at(z + Mth.sin(a1) * r),
                    at(x + Mth.cos(a0) * r), at(y), at(z + Mth.sin(a0) * r),
                    at(x), at(y), at(z), rgb, 255, 0, 1, 0, light, overlay);
        }
    }

    private static void shell(PoseStack.Pose pose, VertexConsumer c, float x, float z, float r, float y0, float y1,
                              int rgb, int light, int overlay) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2 * i / segments), a1 = (float) (Math.PI * 2 * (i + 1) / segments);
            float mid = (a0 + a1) / 2f;
            Mesh.emitQuad(pose, c,
                    at(x + Mth.cos(a0) * r), at(y0), at(z + Mth.sin(a0) * r),
                    at(x + Mth.cos(a0) * r), at(y1), at(z + Mth.sin(a0) * r),
                    at(x + Mth.cos(a1) * r), at(y1), at(z + Mth.sin(a1) * r),
                    at(x + Mth.cos(a1) * r), at(y0), at(z + Mth.sin(a1) * r),
                    rgb, 255, Mth.cos(mid), 0, Mth.sin(mid), light, overlay);
        }
    }

    private static void quadUp(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1, float y,
                               int rgb, int light, int overlay) {
        Mesh.emitQuad(pose, c, at(x0), at(y), at(z1), at(x1), at(y), at(z1), at(x1), at(y), at(z0), at(x0),
                at(y), at(z0), rgb, 255, 0, 1, 0, light, overlay);
    }

    private static void quadDown(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1, float y,
                                 int rgb, int light, int overlay) {
        Mesh.emitQuad(pose, c, at(x0), at(y), at(z0), at(x1), at(y), at(z0), at(x1), at(y), at(z1), at(x0),
                at(y), at(z1), rgb, 255, 0, -1, 0, light, overlay);
    }

    private static void quadFront(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float x1,
                                  float y1, float z, int rgb, int light, int overlay) {
        Mesh.emitQuad(pose, c, at(x1), at(y0), at(z), at(x0), at(y0), at(z), at(x0), at(y1), at(z), at(x1),
                at(y1), at(z), rgb, 255, 0, 0, -1, light, overlay);
    }

    private static void facingUp(PoseStack.Pose pose, VertexConsumer c, float x0, float z0, float x1, float z1, float y,
                                 int r, int g, int b, int light, int overlay) {
        glowVertex(pose, c, x0, y, z1, 0f, 1f, r, g, b, 0, 1, 0, light, overlay);
        glowVertex(pose, c, x1, y, z1, 1f, 1f, r, g, b, 0, 1, 0, light, overlay);
        glowVertex(pose, c, x1, y, z0, 1f, 0f, r, g, b, 0, 1, 0, light, overlay);
        glowVertex(pose, c, x0, y, z0, 0f, 0f, r, g, b, 0, 1, 0, light, overlay);
    }

    private static void facingFront(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float x1,
                                    float y1, float z, int r, int g, int b, int light, int overlay) {
        glowVertex(pose, c, x1, y0, z, 1f, 1f, r, g, b, 0, 0, -1, light, overlay);
        glowVertex(pose, c, x0, y0, z, 0f, 1f, r, g, b, 0, 0, -1, light, overlay);
        glowVertex(pose, c, x0, y1, z, 0f, 0f, r, g, b, 0, 0, -1, light, overlay);
        glowVertex(pose, c, x1, y1, z, 1f, 0f, r, g, b, 0, 0, -1, light, overlay);
    }

    private static void glowVertex(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, float u, float v,
                                   int r, int g, int b, float nx, float ny, float nz, int light, int overlay) {
        c.addVertex(pose.pose(), at(x), at(y), at(z)).setColor(r, g, b, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light);
        Mesh.applyNormal(c, pose, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int rgb, int light, int overlay) {
        c.addVertex(pose.pose(), at(x), at(y), at(z))
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light);
        Mesh.applyNormal(c, pose, nx, ny, nz);
    }

    private static float u(TextureAtlasSprite s, float fraction) {
        //? if <1.21 {
        /*return s.getU(fraction * 16f);
        *///?} else {
        return s.getU(fraction);
        //?}
    }

    private static float v(TextureAtlasSprite s, float fraction) {
        //? if <1.21 {
        /*return s.getV(fraction * 16f);
        *///?} else {
        return s.getV(fraction);
        //?}
    }

    private static float at(float units) {
        return CabinetRenderer.at(units);
    }
}
