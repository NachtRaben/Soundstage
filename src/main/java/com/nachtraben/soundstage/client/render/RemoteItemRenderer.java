package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.item.RemoteItem;
import com.nachtraben.soundstage.util.ItemData;
import com.nachtraben.soundstage.util.WorldAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.GlobalPos;

//? if >=1.21.11 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3fc;
*///?} else {
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

//? if >=1.21.11 {
/*public final class RemoteItemRenderer implements SpecialModelRenderer<RemoteItemRenderer.Look> {
*///?} else {
public final class RemoteItemRenderer extends BlockEntityWithoutLevelRenderer {
//?}

    public record Look(SpeakerFinish finish, float flash, RemoteRenderer.Panel panel, boolean icon) {}

    private record BodyKey(ResourceLocation casing, int light, int overlay) {}

    private record PanelKey(RemoteRenderer.Panel panel, int light, int overlay) {}

    private record PanelGlowKey(RemoteRenderer.Panel panel, int overlay) {}

    private record IconKey(ResourceLocation casing, int light, int overlay) {}

    private record GlowKey(int overlay) {}

    private static final StaticMesh BODY = new StaticMesh(16);
    private static final StaticMesh PANEL = new StaticMesh(16);
    private static final StaticMesh PANEL_GLOW = new StaticMesh(8);
    private static final StaticMesh ICON = new StaticMesh(8);
    private static final StaticMesh GLOW = new StaticMesh(4);

    static final long FLASH_NANOS = 150_000_000L;
    static final long FLASH_FULL_NANOS = 110_000_000L;

    //? if >=26.1 {
    /*public record Unbaked(boolean held, boolean level, boolean icon) implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("held", false).forGetter(Unbaked::held),
                Codec.BOOL.optionalFieldOf("level", false).forGetter(Unbaked::level),
                Codec.BOOL.optionalFieldOf("icon", false).forGetter(Unbaked::icon)).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<Look> bake(SpecialModelRenderer.BakingContext context) {
            return new RemoteItemRenderer(held, level, icon);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<Look>> type() {
            return CODEC;
        }
    }
    *///?} else if >=1.21.11 {
    /*public record Unbaked(boolean held, boolean level, boolean icon) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("held", false).forGetter(Unbaked::held),
                Codec.BOOL.optionalFieldOf("level", false).forGetter(Unbaked::level),
                Codec.BOOL.optionalFieldOf("icon", false).forGetter(Unbaked::icon)).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new RemoteItemRenderer(held, level, icon);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return CODEC;
        }
    }
    *///?}

    //? if >=1.21.11 {
    /*private final boolean held;
    private final boolean level;
    private final boolean icon;

    public RemoteItemRenderer(boolean held, boolean level, boolean icon) {
        this.held = held;
        this.level = level;
        this.icon = icon;
    }

    private boolean queuedWithTheLevel() {
        //? if >=26.2 {
        /^return level;
        ^///?} else {
        return false;
        //?}
    }

    @Override
    public Look extractArgument(ItemStack stack) {
        if (icon) return new Look(finishOf(stack), 0f, RemoteRenderer.Panel.DARK, true);
        return new Look(finishOf(stack), held ? flashOf(stack) : 0f, panelOf(stack), false);
    }

    @Override
    //? if >=26.1 {
    /^public void submit(Look look, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
    ^///?} else {
    public void submit(Look look, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
    //?}
        if (look == null) return;
        GeometrySink sink = GeometrySinks.submit(poseStack, collector);
        if (look.icon()) {
            drawIcon(look.finish(), poseStack, sink, light, overlay);
            return;
        }
        draw(look.finish(), look.flash(), look.panel(), poseStack, sink, light, overlay, queuedWithTheLevel());
    }

    @Override
    public void getExtents(java.util.function.Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new org.joml.Vector3f((corner & 1) == 0 ? 5.6f / 16f : 10.4f / 16f,
                    ((corner >> 1) & 1) == 0 ? 7.3f / 16f : 8.9f / 16f,
                    ((corner >> 2) & 1) == 0 ? 2.0f / 16f : 13.1f / 16f));
        }
    }
    *///?} else {
    public RemoteItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        GeometrySink sink = GeometrySinks.immediate(poseStack, buffers);
        if (context == ItemDisplayContext.GUI) {
            drawIcon(finishOf(stack), poseStack, sink, light, overlay);
            return;
        }
        boolean held = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        draw(finishOf(stack), held ? flashOf(stack) : 0f, panelOf(stack), poseStack, sink, light, overlay, false);
    }
    //?}

    private static SpeakerFinish finishOf(ItemStack stack) {
        SpeakerFinish stored = ItemData.finish(stack);
        return stored != null ? stored : SpeakerFinish.DEFAULT;
    }

    static RemoteRenderer.Panel panelOf(ItemStack stack) {
        GlobalPos link = ItemData.remoteLink(stack);
        var level = Minecraft.getInstance().level;
        if (link == null || level == null) return RemoteRenderer.Panel.DARK;
        if (!level.dimension().equals(link.dimension())) return RemoteRenderer.Panel.DARK;
        if (!(WorldAccess.blockEntityIfLoaded(level, link.pos())
                instanceof ReceiverBlockEntity receiver)) {
            return RemoteRenderer.Panel.DARK;
        }
        var heard = AudioDecoder.getReceiver(link.pos());
        return new RemoteRenderer.Panel(receiver.isPlaying(), receiver.getLoopMode() != 0, receiver.isShuffle(),
                receiver.isLocked(), heard != null && !heard.ring.isEmpty());
    }

    private static float flashOf(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)) return 0f;
        InteractionHand hand = RemoteItem.flashHand();
        if (hand == null || player.getItemInHand(hand) != stack) return 0f;
        long elapsed = System.nanoTime() - RemoteItem.flashNanos();
        if (elapsed < 0 || elapsed >= FLASH_NANOS) return 0f;
        if (elapsed <= FLASH_FULL_NANOS) return 1f;
        return (FLASH_NANOS - elapsed) / (float) (FLASH_NANOS - FLASH_FULL_NANOS);
    }

    private static void drawIcon(SpeakerFinish finish, PoseStack poseStack, GeometrySink sink, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        StaticMesh.setDeferralAvailable(false);
        try {
            ICON.render(new IconKey(finish.casing(), light, overlay), poseStack, sink,
                    (scratch, iconSink) -> RemoteRenderer.icon(finish, iconSink, light, overlay));
            if (finish.isTransparent()) RemoteRenderer.iconClearTrim(finish, sink, light, overlay);
        } finally {
            StaticMesh.setDeferralAvailable(true);
            poseStack.popPose();
        }
    }

    private static void draw(SpeakerFinish finish, float flash, RemoteRenderer.Panel panel, PoseStack poseStack,
                             GeometrySink sink, int light, int overlay, boolean queued) {
        final int lit = light;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        StaticMesh.setDeferralAvailable(queued);
        try {
            BODY.render(new BodyKey(finish.casing(), lit, overlay), poseStack, sink,
                    (scratch, bodySink) -> RemoteRenderer.body(finish, bodySink, lit, overlay));
            PANEL.render(new PanelKey(panel, lit, overlay), poseStack, sink,
                    (scratch, panelSink) -> RemoteRenderer.panel(panel, panelSink, lit, overlay));
            if (finish.isTransparent()) RemoteRenderer.clearTrim(finish, sink, lit, overlay);
            if (!panel.equals(RemoteRenderer.Panel.DARK)) {
                RenderType halo = SoundstageRenderTypes.eyes(RemoteRenderer.HALO);
                PANEL_GLOW.renderBlended(new PanelGlowKey(panel, overlay), poseStack, sink,
                        (scratch, glowSink) -> RemoteRenderer.panelGlow(panel, glowSink, halo, overlay));
            }
            if (flash > 0f) {
                RenderType lens = SoundstageRenderTypes.eyes(RemoteRenderer.LENS_LIGHT);
                RenderType halo = SoundstageRenderTypes.eyes(RemoteRenderer.HALO);
                GLOW.renderBlended(new GlowKey(overlay), flash, flash, flash, poseStack, sink,
                        (scratch, glowSink) -> RemoteRenderer.glow(glowSink, lens, halo, overlay));
            }
        } finally {
            StaticMesh.setDeferralAvailable(true);
            poseStack.popPose();
        }
    }
}
