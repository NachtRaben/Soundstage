package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.util.ItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;

//? if >=1.21.11 {
/*public final class BlockItemRenderer implements SpecialModelRenderer<BlockItemRenderer.Display> {
*///?} else {
public final class BlockItemRenderer extends BlockEntityWithoutLevelRenderer {
//?}

    record Display(BlockState state, SpeakerFinish finish) {
        Block block() {
            return state.getBlock();
        }
    }

    private static final int MAX_DISPLAYS = 64;

    private final Map<Display, BlockEntity> displays = new HashMap<>();
    private final Map<Block, SpeakerFinish> defaults = new HashMap<>();
    private final SpeakerBlockEntityRenderer<SpeakerBlockEntity> renderer = new SpeakerBlockEntityRenderer<>(null);
    private final ReceiverBlockEntityRenderer receiverRenderer = new ReceiverBlockEntityRenderer(null);

    //? if >=26.1 {
    /*public record Unbaked(boolean level) implements SpecialModelRenderer.Unbaked<Display> {
        public static final MapCodec<Unbaked> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        com.mojang.serialization.Codec.BOOL.optionalFieldOf("level", false).forGetter(Unbaked::level))
                        .apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<Display> bake(SpecialModelRenderer.BakingContext context) {
            return new BlockItemRenderer(level);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<Display>> type() {
            return CODEC;
        }
    }
    *///?} else if >=1.21.11 {
    /*public record Unbaked(boolean level) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        com.mojang.serialization.Codec.BOOL.optionalFieldOf("level", false).forGetter(Unbaked::level))
                        .apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new BlockItemRenderer(level);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return CODEC;
        }
    }
    *///?}
    //? if >=1.21.11 {
    /*private final boolean level;

    public BlockItemRenderer(boolean level) {
        this.level = level;
    }

    private boolean queuedWithTheLevel() {
        //? if >=26.2 {
        /^return level;
        ^///?} else {
        return false;
        //?}
    }

    @Override
    public Display extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        Block block = item.getBlock();
        SpeakerFinish stored = ItemData.finish(stack);
        return new Display(ItemData.blockState(stack, block.defaultBlockState()),
                stored != null ? stored : defaultFinish(block, entityBlock));
    }

    @Override
    //? if >=26.1 {
    /^public void submit(Display key, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
    ^///?} else {
    public void submit(Display key, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
    //?}
        if (key == null || !(key.block() instanceof EntityBlock entityBlock)) return;
        draw(key, entityBlock, poseStack, GeometrySinks.submit(poseStack, collector), light, overlay);
    }

    @Override
    public void getExtents(java.util.function.Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new org.joml.Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
        }
    }

    private void draw(Display key, EntityBlock entityBlock, PoseStack poseStack, GeometrySink sink, int light,
                      int overlay) {
        if (displays.size() >= MAX_DISPLAYS) {
            displays.values().forEach(BlockItemRenderer::release);
            displays.clear();
        }
        var display = displays.computeIfAbsent(key, k -> {
            var created = entityBlock.newBlockEntity(BlockPos.ZERO, k.state());
            if (created instanceof SpeakerBlockEntity speaker && k.finish() != null) {
                speaker.setFinish(k.finish());
            } else if (created instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver
                    && k.finish() != null) {
                receiver.setFinish(k.finish());
            }
            return created;
        });
        StaticMesh.setDeferralAvailable(queuedWithTheLevel());
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

    //? if <1.21.11 {
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        Block block = item.getBlock();
        SpeakerFinish stored = ItemData.finish(stack);
        SpeakerFinish finish = stored != null ? stored : defaultFinish(block, entityBlock);

        if (displays.size() >= MAX_DISPLAYS) {
            displays.values().forEach(BlockItemRenderer::release);
            displays.clear();
        }
        var display = displays.computeIfAbsent(
                new Display(ItemData.blockState(stack, block.defaultBlockState()), finish), key -> {
            var created = entityBlock.newBlockEntity(BlockPos.ZERO, key.state());
            if (created instanceof SpeakerBlockEntity speaker && key.finish() != null) {
                speaker.setFinish(key.finish());
            } else if (created instanceof ReceiverBlockEntity receiver
                    && key.finish() != null) {
                receiver.setFinish(key.finish());
            }
            return created;
        });
        StaticMesh.setDeferralAvailable(false);
        try {
            if (display instanceof SpeakerBlockEntity speaker) {
                renderer.render(speaker, 0f, poseStack, buffers, light, overlay);
            } else if (display instanceof ReceiverBlockEntity receiver) {
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
        } else if (probe instanceof ReceiverBlockEntity receiver) {
            found = receiver.getFinish();
        }
        if (found != null) defaults.put(block, found);
        return found;
    }

    private static void release(BlockEntity display) {
        if (display instanceof SpeakerBlockEntity speaker) {
            speaker.renderCache.close();
        }
    }
}
