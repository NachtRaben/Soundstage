package com.nachtraben.soundstage.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class OAuthRequestS2CPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("soundstage", "oauth_request");

    public static final CustomPacketPayload.Type<OAuthRequestS2CPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OAuthRequestS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(
        OAuthRequestS2CPacket::toBytes,
        OAuthRequestS2CPacket::new
    );

    public final String verificationUrl;
    public final String userCode;

    public OAuthRequestS2CPacket(FriendlyByteBuf buf) {
        this.verificationUrl = buf.readUtf();
        this.userCode = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(verificationUrl);
        buf.writeUtf(userCode);
    }

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
