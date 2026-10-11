package com.nachtraben.soundstage.client.render;

//? if >=26.2 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.joml.Matrix4fStack;
*///?} else if >=1.21.11 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4fStack;
*///?} else if <1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
//? if >=1.21 {
import org.joml.Matrix4fStack;
//?}
import org.lwjgl.opengl.GL11;
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class ScreenOverlay {

    private record Picture(ResourceLocation texture, float[] xyz, float[] uv) {}

    private record Caption(Matrix4f matrix, FormattedCharSequence text, int color, int background) {}

    private static final List<Caption> PENDING_CAPTIONS = new ArrayList<>();

    private static final int MAX_PENDING = 4096;

    private static final List<Picture> PENDING = new ArrayList<>();

    private ScreenOverlay() {
    }

    public static boolean active() {
        return IrisCompat.isShaderPackInUse() && !IrisCompat.isRenderingShadows();
    }

    public static boolean ownsCaptions() {
        return IrisCompat.isShaderPackInUse();
    }

    //? if >=26.2 {
    /*private static StagedVertexBuffer staged;
    private static final List<net.minecraft.client.renderer.RenderType> STAGED_TYPES = new ArrayList<>();
    private static final List<StagedVertexBuffer.Draw> STAGED_DRAWS = new ArrayList<>();
    private static final java.util.Map<net.minecraft.client.renderer.RenderType, StagedVertexBuffer.Draw> GLYPH_DRAWS =
            new java.util.HashMap<>();

    private static StagedVertexBuffer.Draw stage(net.minecraft.client.renderer.RenderType type) {
        StagedVertexBuffer.Draw draw = staged.appendDraw(type.format(), type.primitiveTopology());
        STAGED_TYPES.add(type);
        STAGED_DRAWS.add(draw);
        return draw;
    }
    *///?}

    static void record(ResourceLocation texture, float[] xyz, float[] uv) {
        if (PENDING.size() >= MAX_PENDING) PENDING.clear();
        PENDING.add(new Picture(texture, xyz, uv));
    }

    static void recordCaption(Matrix4f matrix, FormattedCharSequence text, int color, int background) {
        if (PENDING_CAPTIONS.size() >= MAX_PENDING) PENDING_CAPTIONS.clear();
        PENDING_CAPTIONS.add(new Caption(matrix, text, color, background));
    }

    public static void drawAfterLevel(Matrix4f modelView, Matrix4f projection) {
        if (PENDING.isEmpty() && PENDING_CAPTIONS.isEmpty()) return;
        //? if >=26.2 {
        /*Minecraft mc = Minecraft.getInstance();
        if (staged == null) staged = new StagedVertexBuffer(() -> "Soundstage screen overlay", 1 << 16);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            stack.set(modelView);
            for (Picture picture : PENDING) {
                VertexConsumer consumer =
                        staged.getVertexBuilder(stage(SoundstageRenderTypes.screenOverlay(picture.texture())));
                float[] xyz = picture.xyz();
                float[] uv = picture.uv();
                for (int i = 0; i < 4; i++) {
                    consumer.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setUv(uv[i * 2], uv[i * 2 + 1]);
                }
            }
            for (Caption caption : PENDING_CAPTIONS) {
                GLYPH_DRAWS.clear();
                //? if >=26.3 {
                /^net.minecraft.client.gui.font.TextRenderable box =
                        ScreenBlockEntityRenderer.captionBackground(mc.font, mc.font.width(caption.text()));
                box.render(caption.matrix(), staged.getVertexBuilder(
                        GLYPH_DRAWS.computeIfAbsent(box.renderType(Font.DisplayMode.NORMAL), ScreenOverlay::stage)),
                        RenderLight.FULL_BRIGHT, false);
                ^///?} else {
                ScreenBlockEntityRenderer.captionBox(caption.matrix(),
                        staged.getVertexBuilder(stage(SoundstageRenderTypes.textBackground())),
                        mc.font.width(caption.text()));
                //?}
                mc.font.prepareText(caption.text(), 0, 0, caption.color(), false, false, 0)
                        .visit(new Font.GlyphVisitor() {
                    @Override
                    public void acceptRenderable(net.minecraft.client.gui.font.TextRenderable glyph) {
                        net.minecraft.client.renderer.RenderType type = glyph.renderType(Font.DisplayMode.NORMAL);
                        StagedVertexBuffer.Draw draw = GLYPH_DRAWS.computeIfAbsent(type, ScreenOverlay::stage);
                        glyph.render(caption.matrix(), staged.getVertexBuilder(draw), RenderLight.FULL_BRIGHT, false);
                    }
                });
            }
            staged.upload();
            for (int i = 0; i < STAGED_DRAWS.size(); i++) {
                StagedVertexBuffer.ExecuteInfo info = staged.getExecuteInfo(STAGED_DRAWS.get(i));
                if (info == null) continue;
                //? if >=26.3 {
                /^var target = mc.gameRenderer.mainRenderTarget();
                try (var pass = RenderSystem.getDevice().createCommandEncoder()
                        .createRenderPass(() -> "Soundstage screen overlay",
                        target.getColorTextureView(), java.util.Optional.empty(),
                        target.hasDepth() ? target.getDepthTextureView() : null, java.util.OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    STAGED_TYPES.get(i).prepare().drawFromBuffer(info, pass);
                }
                ^///?} else {
                STAGED_TYPES.get(i).prepare().drawFromBuffer(info);
                //?}
            }
        } finally {
            STAGED_TYPES.clear();
            STAGED_DRAWS.clear();
            GLYPH_DRAWS.clear();
            staged.endFrame();
            PENDING.clear();
            PENDING_CAPTIONS.clear();
            stack.popMatrix();
        }
        *///?} else if >=1.21.11 {
        /*Minecraft mc = Minecraft.getInstance();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            stack.set(modelView);
            for (Picture picture : PENDING) {
                BufferBuilder builder =
                        Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                float[] xyz = picture.xyz();
                float[] uv = picture.uv();
                for (int i = 0; i < 4; i++) {
                    builder.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setUv(uv[i * 2], uv[i * 2 + 1]);
                }
                SoundstageRenderTypes.screenOverlay(picture.texture()).draw(builder.buildOrThrow());
            }
            if (!PENDING_CAPTIONS.isEmpty()) {
                MultiBufferSource.BufferSource text = mc.renderBuffers().bufferSource();
                for (Caption caption : PENDING_CAPTIONS) {
                    ScreenBlockEntityRenderer.captionBox(caption.matrix(),
                            text.getBuffer(SoundstageRenderTypes.textBackground()), mc.font.width(caption.text()));
                    mc.font.drawInBatch(caption.text(), 0, 0, caption.color(), false, caption.matrix(),
                            text, Font.DisplayMode.NORMAL, 0, RenderLight.FULL_BRIGHT);
                }
                text.endBatch();
            }
        } finally {
            PENDING.clear();
            PENDING_CAPTIONS.clear();
            stack.popMatrix();
        }
        *///?} else {

        Minecraft mc = Minecraft.getInstance();
        //? if >=1.21 {
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        //?} else {
        /*com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
        *///?}
        RenderSystem.backupProjectionMatrix();
        //? if >=1.21 {
        stack.pushMatrix();
        //?} else {
        /*stack.pushPose();
        *///?}
        try {
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            //? if >=1.21 {
            stack.set(modelView);
            //?} else {
            /*stack.last().pose().identity();
            *///?}
            RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1f, -4f);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            for (Picture picture : PENDING) {
                AbstractTexture texture = mc.getTextureManager().getTexture(picture.texture());
                RenderSystem.setShaderTexture(0, picture.texture());
                texture.setFilter(true, true);
                float[] xyz = picture.xyz();
                float[] uv = picture.uv();
                //? if >=1.21 {
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_TEX);
                for (int i = 0; i < 4; i++) {
                    builder.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setUv(uv[i * 2], uv[i * 2 + 1]);
                }
                BufferUploader.drawWithShader(builder.buildOrThrow());
                //?} else {
                /*BufferBuilder builder = Tesselator.getInstance().getBuilder();
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                for (int i = 0; i < 4; i++) {
                    builder.vertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).uv(uv[i * 2], uv[i * 2 + 1]).endVertex();
                }
                BufferUploader.drawWithShader(builder.end());
                *///?}
            }

            if (!PENDING_CAPTIONS.isEmpty()) {
                RenderSystem.polygonOffset(0f, 0f);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                MultiBufferSource.BufferSource textBuffer = mc.renderBuffers().bufferSource();
                for (Caption caption : PENDING_CAPTIONS) {
                    //? if <1.21 {
                    /*var box = com.mojang.blaze3d.vertex.VertexConsumer.of(
                            textBuffer.getBuffer(SoundstageRenderTypes.textBackground()));
                    ScreenBlockEntityRenderer.captionBox(caption.matrix(), box, mc.font.width(caption.text()));
                    box.finish();
                    *///?} else {
                    ScreenBlockEntityRenderer.captionBox(caption.matrix(),
                            textBuffer.getBuffer(SoundstageRenderTypes.textBackground()),
                            mc.font.width(caption.text()));
                    //?}
                    mc.font.drawInBatch(caption.text(), 0, 0, caption.color(), false, caption.matrix(),
                            textBuffer, Font.DisplayMode.NORMAL, 0, RenderLight.FULL_BRIGHT);
                }
                textBuffer.endBatch();
                RenderSystem.disableBlend();
                RenderSystem.polygonOffset(-1f, -4f);
            }
        } finally {
            PENDING.clear();
            PENDING_CAPTIONS.clear();
            RenderSystem.polygonOffset(0f, 0f);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            //? if >=1.21 {
            stack.popMatrix();
            //?} else {
            /*stack.popPose();
            *///?}
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
        //?}
    }
}
