package com.nachtraben.soundstage.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21 && <1.21.11 {
import com.llamalad7.mixinextras.sugar.Local;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.IntBuffer;
//?}

@Mixin(Library.class)
public class LibraryMixin {

    private static final int MIN_STREAMING_CHANNELS = 32;

    @ModifyExpressionValue(method = "init",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I", ordinal = 0))
    private int soundstage$raiseStreamingPoolFloor(int original) {
        return Math.max(original, MIN_STREAMING_CHANNELS);
    }

    //? if >=1.21 && <1.21.11 {
    @Unique private static final int ALC_HRTF_SOFT = 0x1992;
    @Unique private static final int ALC_NUM_HRTF_SPECIFIERS_SOFT = 0x1994;
    @Unique private static final int ALC_HRTF_ID_SOFT = 0x1996;
    @Unique private static final int ALC_OUTPUT_LIMITER_SOFT = 0x199A;
    @Unique private static final IntBuffer soundstage$contextAttributes = BufferUtils.createIntBuffer(7);

    @Shadow private long currentDevice;

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"), index = 1)
    private IntBuffer soundstage$keepHrtfRequest(IntBuffer original, @Local(argsOnly = true) boolean enableHrtf) {
        IntBuffer attributes = soundstage$contextAttributes.clear();
        if (ALC10.alcGetInteger(this.currentDevice, ALC_NUM_HRTF_SPECIFIERS_SOFT) > 0) {
            boolean hrtf = enableHrtf && ALC10.alcIsExtensionPresent(this.currentDevice, "ALC_SOFT_HRTF");
            attributes.put(ALC_HRTF_SOFT).put(hrtf ? 1 : 0).put(ALC_HRTF_ID_SOFT).put(0);
        }
        return attributes.put(ALC_OUTPUT_LIMITER_SOFT).put(1).put(0).flip();
    }
    //?}
}
