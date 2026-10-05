package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

public final class GeometrySinks {

    private GeometrySinks() {}

    //? if <1.21.11 {
    public static GeometrySink immediate(PoseStack poseStack,
                                         net.minecraft.client.renderer.MultiBufferSource bufferSource) {
        return new GeometrySink() {
            @Override
            public void emit(RenderType type, GeometrySink.Emitter body) {
                //? if <1.21 {
                /*var consumer = com.mojang.blaze3d.vertex.VertexConsumer.of(bufferSource.getBuffer(type));
                body.accept(poseStack.last(), consumer);
                consumer.finish();
                *///?} else {
                body.accept(poseStack.last(), bufferSource.getBuffer(type));
                //?}
            }

            @Override
            public void emitBlockModel(RenderType type, BlockState state, int packedLight, int packedOverlay) {
                tesselateIntoConsumer(this, type, state, packedLight, packedOverlay);
            }
        };
    }
    //?}

    //? if >=1.21.11 {
    /*public static GeometrySink submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector) {
        return new GeometrySink() {
            @Override
            public void emit(RenderType type, GeometrySink.Emitter body) {
                int order = SoundstageRenderTypes.submitOrder(type);
                (order == 0 ? collector : collector.order(order)).submitCustomGeometry(poseStack, type, body::accept);
            }

            @Override
            public void emitBlockModel(RenderType type, BlockState state, int packedLight, int packedOverlay) {
                tesselateIntoConsumer(this, type, state, packedLight, packedOverlay);
            }
        };
    }
    *///?}

    private static void tesselateIntoConsumer(GeometrySink sink, RenderType type, BlockState state,
                                              int packedLight, int packedOverlay) {
        //? if >=26.1 {
        /*var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        var parts = new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        model.collectParts(net.minecraft.util.RandomSource.create(42L), parts);
        var instance = new com.mojang.blaze3d.vertex.QuadInstance();
        instance.setLightCoords(packedLight);
        instance.setOverlayCoords(packedOverlay);
        sink.emit(type, (pose, consumer) -> {
            for (var part : parts) {
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                    for (var quad : part.getQuads(side)) consumer.putBakedQuad(pose, quad, instance);
                }
                for (var quad : part.getQuads(null)) consumer.putBakedQuad(pose, quad, instance);
            }
        });
        *///?} else {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        var model = blockRenderer.getBlockModel(state);
        sink.emit(type, (pose, consumer) -> {
            //? if >=1.21.11 {
            /*net.minecraft.client.renderer.block.ModelBlockRenderer.renderModel(
                    pose, consumer, model, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
            *///?} else if >=1.21 {
            blockRenderer.getModelRenderer().renderModel(
                    pose, consumer, state, model, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
            //?} else {
            /*blockRenderer.getModelRenderer().renderModel(pose,
                    ((com.mojang.blaze3d.vertex.VertexConsumer.Adapter) consumer).unwrap(),
                    state, model, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
            *///?}
        });
        //?}
    }

}
