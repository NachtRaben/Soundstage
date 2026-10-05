package com.nachtraben.soundstage.mixin;

import com.nachtraben.soundstage.util.PlayerLanguage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if <1.20.2 {
/*import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerLanguageMixin implements PlayerLanguage {
    @Unique
    private volatile String soundstage$language;

    @Override
    public String soundstage$language() {
        return soundstage$language;
    }

    //? if <1.20.2 {
    /*@Inject(method = "updateOptions", at = @At("HEAD"))
    private void soundstage$recordLanguage(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        soundstage$language = packet.language();
    }
    *///?}
}
