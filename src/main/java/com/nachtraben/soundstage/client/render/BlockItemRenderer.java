package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.registry.ModComponents;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3fc;
*///?} else {
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;

//? if >=1.21.11 {
/*public final class BlockItemRenderer implements SpecialModelRenderer<BlockItemRenderer.Display> {
*///?} else {
public final class BlockItemRenderer extends BlockEntityWithoutLevelRenderer {
//?}

    record Display(Block block, SpeakerFinish finish) {}

    private static final int MAX_DISPLAYS = 64;

    private final java.util.Map<Display, net.minecraft.world.level.block.entity.BlockEntity> displays = new java.util.HashMap<>();
    private final java.util.Map<Block, SpeakerFinish> defaults = new java.util.HashMap<>();
    private final SpeakerBlockEntityRenderer<SpeakerBlockEntity> renderer = new SpeakerBlockEntityRenderer<>(null);
    private final ReceiverBlockEntityRenderer receiverRenderer = new ReceiverBlockEntityRenderer(null);

    //? if >=26.1 {
    /*public record Unbaked() implements SpecialModelRenderer.Unbaked<Display> {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Display> bake(SpecialModelRenderer.BakingContext context) {
            return new BlockItemRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<Display>> type() {
            return CODEC;
        }
    }
    *///?} else if >=1.21.11 {
    /*public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new BlockItemRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return CODEC;
        }
    }
    *///?}
    //? if >=1.21.11 {
    /*public BlockItemRenderer() {
    }

    @Override
    public Display extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        Block block = item.getBlock();
        SpeakerFinish stored = storedFinish(stack);
        return new Display(block, stored != null ? stored : defaultFinish(block, entityBlock));
    }

    @Override
    //? if >=26.1 {
    /^public void submit(Display key, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
    ^///?} else {
    public void submit(Display key, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
    //?}
        DRAWN.incrementAndGet();
        if (key == null || !(key.block() instanceof EntityBlock entityBlock)) return;
        draw(key, entityBlock, poseStack, GeometrySinks.submit(poseStack, collector), light, overlay);
    }

    @Override
    public void getExtents(java.util.function.Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new org.joml.Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
        }
    }

    private void draw(Display key, EntityBlock entityBlock, PoseStack poseStack, GeometrySink sink, int light, int overlay) {
        if (displays.size() >= MAX_DISPLAYS) {
            displays.values().forEach(BlockItemRenderer::release);
            displays.clear();
        }
        var display = displays.computeIfAbsent(key, k -> {
            var created = entityBlock.newBlockEntity(BlockPos.ZERO, k.block().defaultBlockState());
            if (created instanceof SpeakerBlockEntity speaker && k.finish() != null) {
                speaker.setFinish(k.finish());
            } else if (created instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver
                    && k.finish() != null) {
                receiver.setFinish(k.finish());
            }
            return created;
        });
        StaticMesh.setDeferralAvailable(false);
        try {
            if (display instanceof SpeakerBlockEntity speaker) {
                renderer.drawItem(speaker, poseStack, sink, light, overlay);
            } else if (display instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver) {
                receiverRenderer.drawItem(receiver, poseStack, sink, light, overlay);
            }
        } finally {
            StaticMesh.setDeferralAvailable(true);
        }
    }
    *///?} else {
    public BlockItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    //?}

    private static SpeakerFinish storedFinish(ItemStack stack) {
        if (com.nachtraben.soundstage.RigStaging.fault("item-finish-unread")) return null;
        return com.nachtraben.soundstage.util.ItemData.finish(stack);
    }

    public static final java.util.concurrent.atomic.AtomicLong DRAWN = new java.util.concurrent.atomic.AtomicLong();

    //? if <1.21.11 {
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        DRAWN.incrementAndGet();
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        Block block = item.getBlock();
        SpeakerFinish stored = storedFinish(stack);
        SpeakerFinish finish = stored != null ? stored : defaultFinish(block, entityBlock);

        if (displays.size() >= MAX_DISPLAYS) {
            displays.values().forEach(BlockItemRenderer::release);
            displays.clear();
        }
        var display = displays.computeIfAbsent(new Display(block, finish), key -> {
            var created = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (created instanceof SpeakerBlockEntity speaker && key.finish() != null) {
                speaker.setFinish(key.finish());
            } else if (created instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver
                    && key.finish() != null) {
                receiver.setFinish(key.finish());
            }
            return created;
        });
        StaticMesh.setDeferralAvailable(false);
        try {
            if (display instanceof SpeakerBlockEntity speaker) {
                renderer.render(speaker, 0f, poseStack, buffers, light, overlay);
            } else if (display instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver) {
                receiverRenderer.render(receiver, 0f, poseStack, buffers, light, overlay);
            }
        } finally {
            StaticMesh.setDeferralAvailable(true);
        }
    }
    //?}

    private SpeakerFinish defaultFinish(Block block, EntityBlock entityBlock) {
        SpeakerFinish known = defaults.get(block);
        if (known != null) return known;
        var probe = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        SpeakerFinish found = null;
        if (probe instanceof SpeakerBlockEntity speaker) {
            found = speaker.getFinish();
        } else if (probe instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver) {
            found = receiver.getFinish();
        }
        if (found != null) defaults.put(block, found);
        return found;
    }

    private static void release(net.minecraft.world.level.block.entity.BlockEntity display) {
        if (display instanceof SpeakerBlockEntity speaker) {
            speaker.renderCache.close();
        }
    }
}
