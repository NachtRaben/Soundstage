package com.nachtraben.soundstage.mixin.client;

//? if !forge {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//? if <1.21 {
/*import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
*///?} else {
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class SodiumRebuildMixin {
    //? if <1.21 {
    /*@WrapOperation(method = "scheduleRebuild", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;isBuilt()Z"))
    *///?} else {
    @WrapOperation(method = "scheduleRebuild", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;isBuilt()Z"))
    //?}
    private boolean soundstage$keepRequestAfterFirstSnapshot(RenderSection section, Operation<Boolean> isBuilt) {
        if (isBuilt.call(section)) return true;
        //? if <1.21.11 {
        return section.getPendingUpdate() == null;
        //?} else {
        /*return section.getPendingUpdate() == 0;
        *///?}
    }
}
//?}
