package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nachtraben.soundstage.block.ScreenBlock;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.client.video.ClientSubtitles;
import com.nachtraben.soundstage.client.video.ClientVideoReceivers;
import com.nachtraben.soundstage.client.video.NowPlayingCards;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.core.video.DisplayLayout;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
//? if <1.21.11 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;

//? if >=1.21.11 {
/*public class ScreenBlockEntityRenderer
        implements BlockEntityRenderer<ScreenBlockEntity, BlockEntityState<ScreenBlockEntity>> {
*///?} else {
public class ScreenBlockEntityRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
//?}

    private static final Set<BlockPos> CAPTION_ANNOUNCED =
            ConcurrentHashMap.newKeySet();

    private static final float DEPTH_UNITS = 1f;

    private static final float BEZEL_UNITS = 1f;

    private static final float BEZEL_DEPTH_EPSILON = 0.004f;

    private static final RenderType BEZEL_TYPE = SoundstageRenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);

    private static final int CASING_RGB = 0x1E1E1E;

    public ScreenBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @FunctionalInterface
    private interface CaptionOut {
        void line(PoseStack poseStack, Matrix4f matrix, FormattedCharSequence line, int width);
    }

    //? if >=1.21.11 {
    /*@Override
    public BlockEntityState<ScreenBlockEntity> createRenderState() {
        return new BlockEntityState<>();
    }

    @Override
    public void extractRenderState(ScreenBlockEntity blockEntity, BlockEntityState<ScreenBlockEntity> state,
            float partialTick, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jetbrains.annotations.Nullable
                    CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, breaking);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(BlockEntityState<ScreenBlockEntity> state, PoseStack poseStack,
                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.CameraRenderState camera) {
        if (state.blockEntity == null) return;
        GeometrySink sink = GeometrySinks.submit(poseStack, collector);
        net.minecraft.client.renderer.OrderedSubmitNodeCollector late = collector.order(1);
        CaptionOut captions = (stack, matrix, line, width) -> {
            //? if >=26.3 {
            /^late.submitTextBackground(stack, -1f, CAPTION_BOX_TOP, width, CAPTION_BOX_BOTTOM, CAPTION_BOX,
                    Font.DisplayMode.NORMAL, RenderLight.FULL_BRIGHT);
            ^///?} else {
            late.submitCustomGeometry(stack, SoundstageRenderTypes.textBackground(),
                    (pose, consumer) -> captionBox(pose.pose(), consumer, width));
            //?}
            late.submitText(stack, 0, 0, line, false, Font.DisplayMode.NORMAL, RenderLight.FULL_BRIGHT,
                    CAPTION_COLOUR, 0, 0);
        };
        draw(state.blockEntity, poseStack, sink, captions, state.lightCoords);
    }
    *///?} else {
    @Override
    public void render(ScreenBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        CaptionOut captions = (stack, matrix, line, width) -> {
            if (IrisCompat.isRenderingShadows()) return;
            if (PENDING_CAPTIONS.size() >= MAX_PENDING_CAPTIONS) PENDING_CAPTIONS.clear();
            PENDING_CAPTIONS.add(new PendingCaption(
                    new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(matrix),
                    line, width));
        };
        draw(blockEntity, poseStack, GeometrySinks.immediate(poseStack, bufferSource), captions, packedLight);
    }

    private record PendingCaption(Matrix4f modelView, FormattedCharSequence line, int width) {}

    private static final List<PendingCaption> PENDING_CAPTIONS = new ArrayList<>();

    private static MultiBufferSource.BufferSource captionBuffers;

    private static MultiBufferSource.BufferSource captionBuffers() {
        if (captionBuffers == null) {
            //? if <1.21 {
            /*captionBuffers = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(4096));
            *///?} else {
            captionBuffers = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096));
            //?}
        }
        return captionBuffers;
    }
    private static final int MAX_PENDING_CAPTIONS = 256;

    public static void flushCaptions() {
        if (PENDING_CAPTIONS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource shared = minecraft.renderBuffers().bufferSource();
        if (shared.getClass() != MultiBufferSource.BufferSource.class) {
            shared.endBatch();
        } else {
            shared.endLastBatch();
        }
        MultiBufferSource.BufferSource buffers = captionBuffers();
        //? if <1.21 {
        /*PoseStack modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        *///?} else {
        org.joml.Matrix4fStack modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        //?}
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            for (PendingCaption caption : PENDING_CAPTIONS) {
                //? if <1.21 {
                /*var box = com.mojang.blaze3d.vertex.VertexConsumer.of(
                        buffers.getBuffer(SoundstageRenderTypes.textBackground()));
                captionBox(caption.modelView(), box, caption.width());
                box.finish();
                *///?} else {
                captionBox(caption.modelView(), buffers.getBuffer(SoundstageRenderTypes.textBackground()),
                        caption.width());
                //?}
                font.drawInBatch(caption.line(), 0, 0, CAPTION_COLOUR, false, caption.modelView(), buffers,
                        Font.DisplayMode.NORMAL, 0, RenderLight.FULL_BRIGHT);
            }
            buffers.endBatch();
        } finally {
            PENDING_CAPTIONS.clear();
            //? if <1.21 {
            /*modelView.popPose();
            *///?} else {
            modelView.popMatrix();
            //?}
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        }
    }
    //?}

    private static final double FOV_MODIFIER_MAX = 1.5;

    private static boolean displayInView(BlockPos pos, Direction facing, DisplayLayout layout) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) return true;
        //? if >=1.21.11 {
        /*net.minecraft.world.phys.Vec3 eye = camera.position();
        org.joml.Vector3fc forward = camera.forwardVector();
        org.joml.Vector3fc up = camera.upVector();
        org.joml.Vector3fc left = camera.leftVector();
        *///?} else {
        net.minecraft.world.phys.Vec3 eye = camera.getPosition();
        org.joml.Vector3fc forward = camera.getLookVector();
        org.joml.Vector3fc up = camera.getUpVector();
        org.joml.Vector3fc left = camera.getLeftVector();
        //?}
        Direction right = facing.getCounterClockWise();
        double face = DEPTH_UNITS / 16.0 - 0.5;
        double originX = pos.getX() + 0.5 + facing.getStepX() * face - right.getStepX() * (layout.startCol() + 0.5);
        double originY = pos.getY() - layout.startRow();
        double originZ = pos.getZ() + 0.5 + facing.getStepZ() * face - right.getStepZ() * (layout.startCol() + 0.5);
        double aspect = Math.max(1, minecraft.getWindow().getWidth())
                / (double) Math.max(1, minecraft.getWindow().getHeight());
        double tanVertical = Math.tan(Math.min(Math.toRadians(minecraft.options.fov().get()) * FOV_MODIFIER_MAX / 2,
                Math.toRadians(89)));
        double tanHorizontal = tanVertical * aspect;
        int outsideAll = 0b11111;
        for (int corner = 0; corner < 4; corner++) {
            int across = (corner & 1) * layout.widthCells();
            int upward = (corner >> 1) * layout.heightCells();
            double dx = originX + right.getStepX() * across - eye.x;
            double dy = originY + upward - eye.y;
            double dz = originZ + right.getStepZ() * across - eye.z;
            double depth = dx * forward.x() + dy * forward.y() + dz * forward.z();
            double sideways = dx * left.x() + dy * left.y() + dz * left.z();
            double height = dx * up.x() + dy * up.y() + dz * up.z();
            int outside = 0;
            if (depth < 0) outside |= 1;
            if (sideways > depth * tanHorizontal) outside |= 2;
            if (-sideways > depth * tanHorizontal) outside |= 4;
            if (height > depth * tanVertical) outside |= 8;
            if (-height > depth * tanVertical) outside |= 16;
            outsideAll &= outside;
        }
        return outsideAll == 0;
    }

    private void draw(ScreenBlockEntity blockEntity, PoseStack poseStack, GeometrySink sink, CaptionOut captions,
                      int packedLight) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof ScreenBlock)) return;
        Direction facing = state.getValue(ScreenBlock.FACING);

        DisplayLayout layout = blockEntity.getDisplayLayout();
        if (layout == null) return;
        int col = layout.startCol();
        int row = layout.startRow();

        BlockPos receiverPos = blockEntity.getReceiverPos();
        ClientVideoReceivers.Frame frame = receiverPos == null ? null
                : ClientVideoReceivers.present(receiverPos, displayInView(blockEntity.getBlockPos(), facing, layout));
        boolean card = false;
        if (frame == null && receiverPos != null) {
            frame = NowPlayingCards.frame(receiverPos,
                    layout.widthCells(), layout.heightCells());
            card = frame != null;
        }

        ScreenBlockEntity.FitMode fitMode = ScreenBlockEntity.FitMode.FIT;
        if (blockEntity.getLevel() != null) {
            BlockPos anchorPos = ScreenBlockEntity.anchorPosition(blockEntity.getBlockPos(), facing, layout);
            if (blockEntity.getLevel().getBlockEntity(anchorPos) instanceof ScreenBlockEntity anchor) {
                fitMode = anchor.getFitMode();
            }
        }

        DisplayLayout.Slice slice = null;
        DisplayLayout.Rect picture = null;
        if (frame != null) {
            int videoW = frame.width();
            int videoH = frame.height();
            picture = fitMode == ScreenBlockEntity.FitMode.FILL && !card
                    ? DisplayLayout.fill(videoW, videoH, layout.widthCells(), layout.heightCells())
                    : DisplayLayout.fit(videoW, videoH, layout.widthCells(), layout.heightCells());
            slice = DisplayLayout.slice(picture, col, row);
        }

        boolean bezelLeft = !layout.contains(col - 1, row);
        boolean bezelRight = !layout.contains(col + 1, row);
        boolean bezelBottom = !layout.contains(col, row - 1);
        boolean bezelTop = !layout.contains(col, row + 1);

        Direction right = facing.getCounterClockWise();
        int rx = right.getStepX(), rz = right.getStepZ();
        int fx = facing.getStepX(), fz = facing.getStepZ();

        float depth = DEPTH_UNITS / 16f;
        float bezelDepth = depth + BEZEL_DEPTH_EPSILON;
        float bezelWidth = BEZEL_UNITS / 16f;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        DisplayLayout.Slice finalSlice = slice;
        sink.emit(BEZEL_TYPE, AtlasSprites.white((pose, consumer) -> {
            if (finalSlice == null) {
                blackQuad(pose, consumer, rx, rz, fx, fz, 0f, 1f, 0f, 1f, depth, RenderLight.FULL_BRIGHT);
            } else {
                float left = (float) finalSlice.cellX();
                float edge = left + (float) finalSlice.cellWidth();
                float bottom = (float) finalSlice.cellY();
                float top = bottom + (float) finalSlice.cellHeight();
                if (left > 0f) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, 0f, left, 0f, 1f, depth, RenderLight.FULL_BRIGHT);
                }
                if (edge < 1f) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, edge, 1f, 0f, 1f, depth, RenderLight.FULL_BRIGHT);
                }
                if (bottom > 0f) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, left, edge, 0f, bottom, depth, RenderLight.FULL_BRIGHT);
                }
                if (top < 1f) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, left, edge, top, 1f, depth, RenderLight.FULL_BRIGHT);
                }
            }

            if (bezelLeft || bezelRight || bezelBottom || bezelTop) {
                float interiorLeft = bezelLeft ? bezelWidth : 0f;
                float interiorRight = 1f - (bezelRight ? bezelWidth : 0f);
                float interiorBottom = bezelBottom ? bezelWidth : 0f;
                float interiorTop = 1f - (bezelTop ? bezelWidth : 0f);
                if (bezelLeft) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, 0f, interiorLeft, 0f, 1f, bezelDepth, packedLight);
                }
                if (bezelRight) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, interiorRight, 1f, 0f, 1f, bezelDepth, packedLight);
                }
                if (bezelBottom) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, interiorLeft, interiorRight, 0f, interiorBottom,
                            bezelDepth, packedLight);
                }
                if (bezelTop) {
                    blackQuad(pose, consumer, rx, rz, fx, fz, interiorLeft, interiorRight, interiorTop, 1f,
                            bezelDepth, packedLight);
                }
            }

            casing(pose, consumer, rx, rz, fx, fz, bezelLeft, bezelRight, bezelBottom, bezelTop,
                    bezelDepth, packedLight);
        }));

        if (finalSlice != null) {
            sink.emit(SoundstageRenderTypes.video(frame.texture()),
                    (pose, consumer) -> picture(pose, consumer, rx, rz, fx, fz, finalSlice, depth, false));
            if (ScreenOverlay.active()) {
                recordOverlay(poseStack.last(), frame.texture(), rx, rz, fx, fz, finalSlice, depth);
            }
        }

        if (frame != null && !card && col == layout.anchorCol() && row == layout.anchorRow()
                && SoundstageAppearance.showSubtitles()) {
            String caption = ClientSubtitles.activeText(receiverPos, frame.ptsMillis());
            if (caption != null && !caption.isEmpty()) {
                DisplayLayout.Rect visible = visiblePicture(picture, layout.widthCells(), layout.heightCells());
                DisplayLayout.Rect area = layout.captionArea(visible);
                if (area != null) renderCaption(poseStack, captions, facing, visible, area, col, row, depth, caption);
                if (CAPTION_ANNOUNCED.add(receiverPos)) {
                    LoggerFactory.getLogger("Soundstage").info(area != null
                            ? "Soundstage: drawing subtitles for receiver {} on its display." :
                            "Soundstage: a subtitle is due for receiver {}, but its display has no row to draw it on.",
                            receiverPos.toShortString());
                }
            }
        }

        poseStack.popPose();
    }

    private static final float CAPTION_DEPTH_EPSILON = BEZEL_DEPTH_EPSILON * 2f;

    private static DisplayLayout.Rect visiblePicture(DisplayLayout.Rect picture, int widthCells, int heightCells) {
        double left = Math.max(picture.x(), 0.0);
        double bottom = Math.max(picture.y(), 0.0);
        double right = Math.min(picture.x() + picture.width(), widthCells);
        double top = Math.min(picture.y() + picture.height(), heightCells);
        return new DisplayLayout.Rect(left, bottom, right - left, top - bottom);
    }

    private static void renderCaption(PoseStack poseStack, CaptionOut captions, Direction facing,
                                      DisplayLayout.Rect picture, DisplayLayout.Rect area, int col, int row,
                                      float depth, String text) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        float lineHeightBlocks = (float) Math.max(0.06, picture.height() / captionLinesPerPicture());
        float scale = lineHeightBlocks / font.lineHeight;
        int maxWidthPx = Math.max(20, (int) (0.9 * area.width() / scale));

        List<FormattedCharSequence> lines = new ArrayList<>();
        for (FormattedCharSequence wrapped : font.split(
                Component.literal(text).withStyle(style -> style.withFont(SUBTITLE_FONT_STYLE)), maxWidthPx)) {
            lines.add(FormattedCharSequence.composite(PAD, wrapped, PAD));
        }
        int lineCount = Math.min(lines.size(), 3);
        if (lineCount == 0) return;

        poseStack.pushPose();
        float yaw = -facing.toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        float xCenter = (float) (area.x() + area.width() / 2.0 - (col + 0.5));
        float yBase = (float) (area.y() + picture.height() * CAPTION_BOTTOM_MARGIN - (row + 0.5))
                + CAPTION_BOX_BOTTOM * scale;
        float z = depth - 0.5f + CAPTION_DEPTH_EPSILON;
        poseStack.translate(xCenter, yBase, z);
        poseStack.scale(scale, -scale, scale);

        boolean overlay = ScreenOverlay.active();
        boolean inLevel = !ScreenOverlay.ownsCaptions();
        for (int i = 0; i < lineCount; i++) {
            FormattedCharSequence line = lines.get(lineCount - 1 - i);
            int width = font.width(line);
            poseStack.pushPose();
            poseStack.translate(-width / 2f, -(i * CAPTION_LINE_ADVANCE), 0f);
            Matrix4f lineMatrix = poseStack.last().pose();
            if (inLevel) captions.line(poseStack, lineMatrix, line, width);
            if (overlay) {
                ScreenOverlay.recordCaption(new Matrix4f(lineMatrix), line, CAPTION_COLOUR, CAPTION_BOX);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static final ResourceLocation SUBTITLE_FONT =
            ResourceLocation.fromNamespaceAndPath("soundstage", "subtitle");
    //? if >=1.21.11 {
    /*private static final net.minecraft.network.chat.FontDescription SUBTITLE_FONT_STYLE =
            new net.minecraft.network.chat.FontDescription.Resource(SUBTITLE_FONT);
    *///?} else {
    private static final ResourceLocation SUBTITLE_FONT_STYLE = SUBTITLE_FONT;
    //?}

    static void captionBox(Matrix4f matrix, VertexConsumer box, int width) {
        float x0 = -1f, x1 = width, top = CAPTION_BOX_TOP, bottom = CAPTION_BOX_BOTTOM, z = -0.01f;
        int light = RenderLight.FULL_BRIGHT;
        int colour = CAPTION_BOX;
        box.addVertex(matrix, x0, bottom, z).setColor(colour).setLight(light);
        box.addVertex(matrix, x1, bottom, z).setColor(colour).setLight(light);
        box.addVertex(matrix, x1, top, z).setColor(colour).setLight(light);
        box.addVertex(matrix, x0, top, z).setColor(colour).setLight(light);
    }

    private static final float CAPTION_BOX_TOP = -0.5f;
    private static final float CAPTION_BOX_BOTTOM = 11.5f;
    private static final float CAPTION_LINE_ADVANCE = CAPTION_BOX_BOTTOM - CAPTION_BOX_TOP;

    private static double captionLinesPerPicture() {
        return switch (SoundstageAppearance.subtitleSize()) {
            case SMALL -> 18.0;
            case MEDIUM -> 14.0;
            case LARGE -> 10.0;
        };
    }
    private static final double CAPTION_BOTTOM_MARGIN = 0.07;
    private static final int CAPTION_COLOUR = 0xFFFFFFFF;

    //? if >=26.3 {
    /*static net.minecraft.client.gui.font.TextRenderable captionBackground(Font font, int width) {
        return font.prepareBackground(-1f, CAPTION_BOX_TOP, width, CAPTION_BOX_BOTTOM, CAPTION_BOX);
    }
    *///?}

    private static final int CAPTION_BOX = 0xBF000000;
    private static final FormattedCharSequence PAD =
            Component.literal(" ").withStyle(style -> style.withFont(SUBTITLE_FONT_STYLE)).getVisualOrderText();

    private record Corner(float x, float y, float z) {}

    private static Corner corner(int rx, int rz, int fx, int fz, float u, float v, float depth) {
        float x = rx * (u - 0.5f) + fx * (depth - 0.5f);
        float y = v - 0.5f;
        float z = rz * (u - 0.5f) + fz * (depth - 0.5f);
        return new Corner(x, y, z);
    }

    private static void blackQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                  int rx, int rz, int fx, int fz,
                                  float left, float right, float bottom, float top,
                                  float depth, int light) {
        Corner a = corner(rx, rz, fx, fz, left, bottom, depth);
        Corner b = corner(rx, rz, fx, fz, right, bottom, depth);
        Corner c = corner(rx, rz, fx, fz, right, top, depth);
        Corner d = corner(rx, rz, fx, fz, left, top, depth);
        Mesh.emitQuad(pose, consumer,
                a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), c.x(), c.y(), c.z(), d.x(), d.y(), d.z(),
                0x000000, 255, fx, 0, fz, light, OverlayTexture.NO_OVERLAY);
    }

    private static void casing(PoseStack.Pose pose, VertexConsumer consumer, int rx, int rz, int fx, int fz,
                               boolean left, boolean right, boolean bottom, boolean top, float front, int light) {
        casingQuad(pose, consumer, rx, rz, fx, fz,
                0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, -fx, 0, -fz, light);
        if (left) {
            casingQuad(pose, consumer, rx, rz, fx, fz,
                    0f, 0f, 0f, 0f, 0f, front, 0f, 1f, front, 0f, 1f, 0f, -rx, 0, -rz, light);
        }
        if (right) {
            casingQuad(pose, consumer, rx, rz, fx, fz,
                    1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, front, 1f, 0f, front, rx, 0, rz, light);
        }
        if (bottom) {
            casingQuad(pose, consumer, rx, rz, fx, fz,
                    0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, front, 0f, 0f, front, 0, -1, 0, light);
        }
        if (top) {
            casingQuad(pose, consumer, rx, rz, fx, fz,
                    0f, 1f, 0f, 0f, 1f, front, 1f, 1f, front, 1f, 1f, 0f, 0, 1, 0, light);
        }
    }

    private static void casingQuad(PoseStack.Pose pose, VertexConsumer consumer, int rx, int rz, int fx, int fz,
                                   float u0, float v0, float d0, float u1, float v1, float d1,
                                   float u2, float v2, float d2, float u3, float v3, float d3,
                                   int nx, int ny, int nz, int light) {
        Corner a = corner(rx, rz, fx, fz, u0, v0, d0);
        Corner b = corner(rx, rz, fx, fz, u1, v1, d1);
        Corner c = corner(rx, rz, fx, fz, u2, v2, d2);
        Corner d = corner(rx, rz, fx, fz, u3, v3, d3);
        Mesh.emitQuad(pose, consumer,
                a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), c.x(), c.y(), c.z(), d.x(), d.y(), d.z(),
                CASING_RGB, 255, nx, ny, nz, light, OverlayTexture.NO_OVERLAY);
    }

    private static void recordOverlay(PoseStack.Pose pose, ResourceLocation texture,
                                      int rx, int rz, int fx, int fz, DisplayLayout.Slice slice, float depth) {
        float left = (float) slice.cellX();
        float right = left + (float) slice.cellWidth();
        float bottom = (float) slice.cellY();
        float top = bottom + (float) slice.cellHeight();
        float u0 = (float) slice.u0(), v0 = (float) slice.v0();
        float u1 = (float) slice.u1(), v1 = (float) slice.v1();
        Corner[] corners = {
                corner(rx, rz, fx, fz, left, bottom, depth),
                corner(rx, rz, fx, fz, right, bottom, depth),
                corner(rx, rz, fx, fz, right, top, depth),
                corner(rx, rz, fx, fz, left, top, depth)};
        float[] xyz = new float[12];
        Vector3f p = new Vector3f();
        for (int i = 0; i < 4; i++) {
            pose.pose().transformPosition(corners[i].x(), corners[i].y(), corners[i].z(), p);
            xyz[i * 3] = p.x();
            xyz[i * 3 + 1] = p.y();
            xyz[i * 3 + 2] = p.z();
        }
        ScreenOverlay.record(texture, xyz, new float[]{u0, v1, u1, v1, u1, v0, u0, v0});
    }

    private static void picture(PoseStack.Pose pose, VertexConsumer consumer,
                                int rx, int rz, int fx, int fz,
                                DisplayLayout.Slice slice, float depth) {
        picture(pose, consumer, rx, rz, fx, fz, slice, depth, false);
    }

    private static void picture(PoseStack.Pose pose, VertexConsumer consumer,
                                int rx, int rz, int fx, int fz,
                                DisplayLayout.Slice slice, float depth, boolean reverse) {
        float left = (float) slice.cellX();
        float right = left + (float) slice.cellWidth();
        float bottom = (float) slice.cellY();
        float top = bottom + (float) slice.cellHeight();
        float u0 = (float) slice.u0(), v0 = (float) slice.v0();
        float u1 = (float) slice.u1(), v1 = (float) slice.v1();

        Corner a = corner(rx, rz, fx, fz, left, bottom, depth);
        Corner b = corner(rx, rz, fx, fz, right, bottom, depth);
        Corner c = corner(rx, rz, fx, fz, right, top, depth);
        Corner d = corner(rx, rz, fx, fz, left, top, depth);

        int nx = reverse ? -fx : fx, nz = reverse ? -fz : fz;
        Vector3f n = pose.transformNormal(nx, 0, nz, new Vector3f());
        int overlay = OverlayTexture.NO_OVERLAY;
        int light = RenderLight.FULL_BRIGHT;
        if (!reverse) {
            Mesh.addTintedVertex(consumer, pose, a.x(), a.y(), a.z(), u0, v1, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
            Mesh.addTintedVertex(consumer, pose, b.x(), b.y(), b.z(), u1, v1, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
            Mesh.addTintedVertex(consumer, pose, c.x(), c.y(), c.z(), u1, v0, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
            Mesh.addTintedVertex(consumer, pose, d.x(), d.y(), d.z(), u0, v0, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
        } else {
            Mesh.addTintedVertex(consumer, pose, a.x(), a.y(), a.z(), u0, v1, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
            Mesh.addTintedVertex(consumer, pose, d.x(), d.y(), d.z(), u0, v0, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
            Mesh.addTintedVertex(consumer, pose, c.x(), c.y(), c.z(), u1, v0, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
            Mesh.addTintedVertex(consumer, pose, b.x(), b.y(), b.z(), u1, v1, 0xFFFFFF, 255, n.x(), n.y(), n.z(),
                    light, overlay);
        }
    }
}
