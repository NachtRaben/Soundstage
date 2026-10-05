package com.nachtraben.soundstage.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nachtraben.soundstage.block.MusicSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Parrot.class)
public abstract class ParrotMixin {

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean soundstage$danceRange(BlockPos source, Position position, double radius,
                                         Operation<Boolean> original) {
        Parrot self = (Parrot) (Object) this;
        return original.call(source, position,
                MusicSource.danceRadius(self.level(), source, radius));
    }

    //? if >=26.1 {
    /*@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean soundstage$danceSource(BlockState state, Object block, Operation<Boolean> original) {
    *///?} else {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean soundstage$danceSource(BlockState state, Block block, Operation<Boolean> original) {
    //?}
        return original.call(state, block) || MusicSource.isMusicSource(state);
    }
}
