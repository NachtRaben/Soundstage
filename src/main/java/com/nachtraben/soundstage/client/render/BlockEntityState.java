package com.nachtraben.soundstage.client.render;

//? if >=1.21.11 {
/*import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockEntityState<T extends BlockEntity> extends BlockEntityRenderState {
    public T blockEntity;
    public float partialTick;
}
*///?} else {
public final class BlockEntityState {
    private BlockEntityState() {
    }
}
//?}
