package com.nachtraben.soundstage.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;

public interface GeometrySink {

    void emit(RenderType type, Emitter body);

    void emitBlockModel(RenderType type, net.minecraft.world.level.block.state.BlockState state,
                        int packedLight, int packedOverlay);

    @FunctionalInterface
    interface Emitter {
        void accept(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
