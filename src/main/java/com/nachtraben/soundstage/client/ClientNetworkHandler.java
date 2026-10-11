package com.nachtraben.soundstage.client;

import com.nachtraben.soundstage.network.OAuthRequestS2CPacket;
import com.nachtraben.soundstage.client.gui.OAuthScreen;
import net.minecraft.client.Minecraft;

public class ClientNetworkHandler {
    public static void handleOAuthRequest(OAuthRequestS2CPacket packet) {
        Minecraft.getInstance().setScreen(new OAuthScreen(packet.getVerificationUrl(), packet.getUserCode()));
    }
}
