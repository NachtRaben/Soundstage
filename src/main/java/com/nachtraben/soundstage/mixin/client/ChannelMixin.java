package com.nachtraben.soundstage.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.nachtraben.soundstage.client.audio.JitterAudioStream;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTDeviceClock;
import org.lwjgl.openal.SOFTSourceLatency;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Channel.class)
public class ChannelMixin {

    @Shadow @Final private int source;
    @Shadow private AudioStream stream;

    private final long[] soundstage$sampleClock = new long[2];

    @Redirect(method = "play", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/AL10;alSourcePlay(I)V"))
    private void soundstage$alignJoiningSource(int source) {
        if (stream instanceof JitterAudioStream jitter) {
            JitterAudioStream.probeDeviceClockSupport();
            if (jitter.playAlignedToGroup(source)) return;
            if (jitter.playAtGroupStart(source)) return;
        }
        AL10.alSourcePlay(source);
    }

    @Inject(method = "removeProcessedBuffers", at = @At("TAIL"))
    private void soundstage$trackUnqueuedBuffers(CallbackInfoReturnable<Integer> cir) {
        if (!(stream instanceof JitterAudioStream jitter)) return;
        int removed = cir.getReturnValueI();
        if (removed > 0) {
            jitter.onBuffersUnqueued(removed);
        }
    }

    @Inject(method = "updateStream", at = @At("TAIL"))
    private void soundstage$readRenderClock(CallbackInfo ci) {
        if (!(stream instanceof JitterAudioStream jitter)) return;
        jitter.releaseHeldGroupIfDue();
        jitter.onBuffersQueued(AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED));
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return;
        if (JitterAudioStream.probeClockReadSupport()) {
            SOFTSourceLatency.alGetSourcei64vSOFT(
                    source, SOFTDeviceClock.AL_SAMPLE_OFFSET_CLOCK_SOFT,
                    soundstage$sampleClock);
            jitter.onDeviceClockMeasure(soundstage$sampleClock[0], soundstage$sampleClock[1]);
        } else {
            long context = ALC10.alcGetCurrentContext();
            long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
            if (JitterAudioStream.probeSourceLatencySupport()) {
                SOFTSourceLatency.alGetSourcei64vSOFT(
                        source, SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT,
                        soundstage$sampleClock);
                jitter.onRenderClock(soundstage$sampleClock[0] / 4_294_967_296.0, soundstage$sampleClock[1],
                        System.nanoTime(), device);
            } else {
                jitter.onRenderClock(AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET), 0L,
                        System.nanoTime(), device);
            }
        }
        jitter.onRenderMeasure(AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET), System.nanoTime());
    }
}
