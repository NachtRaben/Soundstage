package com.nachtraben.soundstage.mixin.client;

import com.nachtraben.soundstage.client.audio.DeviceClockSampler;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Shadow @Final private SoundEngineExecutor executor;
    @Shadow private boolean loaded;

    @Inject(method = "updateSource", at = @At("TAIL"))
    private void soundstage$sampleDeviceClock(Camera camera, CallbackInfo ci) {
        if (loaded && camera.isInitialized()) {
            executor.execute(DeviceClockSampler::sample);
        }
    }
}
