package com.nachtraben.soundstage.command;

import com.mojang.brigadier.CommandDispatcher;
import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.network.OAuthRequestS2CPacket;
import com.nachtraben.soundstage.util.Players;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.methods.CloseableHttpResponse;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.client.methods.HttpPost;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.entity.ContentType;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.entity.StringEntity;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.impl.client.CloseableHttpClient;
import dev.armadeus.armalib.lavaplayer.shadow.org.apache.http.impl.client.HttpClients;
import dev.armadeus.armalib.platform.Operators;
import dev.armadeus.armalib.platform.network.Network;
import dev.lavalink.youtube.http.YoutubeOauth2Handler;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SoundstageCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
            Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("soundstage")
            .requires(Operators.requirement())
            .then(Commands.literal("oauth")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayer();
                    if (player != null) {
                        triggerOAuthFlow(player, true);
                    } else {
                        source.sendSystemMessage(Component.literal("This command must be run by a player."));
                    }
                    return 1;
                })
            )
        );
    }

    public static void triggerOAuthFlow(ServerPlayer player, boolean isCommand) {
        if (isCommand) {
            player.sendSystemMessage(Component.literal("Initiating YouTube OAuth flow..."));
        }

        Thread oauthThread = new Thread(() -> {
            try {
                var youtube = AudioManager.get().getYoutubeSource();
                if (youtube == null) {
                    player.sendSystemMessage(Component.literal(
                            "YouTube is disabled in this server's Soundstage config, so there is nothing to "
                                    + "sign in to."));
                    return;
                }
                YoutubeOauth2Handler handler = youtube.getOauth2Handler();

                JsonBrowser response = handler.fetchDeviceCode();
                String verificationUrl = response.get("verification_url").text();
                String userCode = response.get("user_code").text();
                String deviceCode = response.get("device_code").text();
                long interval = response.get("interval").asLong(0) * 1000;
                if (interval == 0) interval = 5000;

                if (Players.level(player).getServer().isSingleplayer()) {
                    Network.sendToPlayer(player, new OAuthRequestS2CPacket(verificationUrl, userCode));
                } else {
                    //? if >=1.21.11 {
                    /*ClickEvent open;
                    try {
                        open = new ClickEvent.OpenUrl(java.net.URI.create(verificationUrl));
                    } catch (IllegalArgumentException malformed) {
                        open = null;
                    }
                    ClickEvent copy = new ClickEvent.CopyToClipboard(userCode);
                    HoverEvent hint = new HoverEvent.ShowText(Component.literal("Click to copy"));
                    *///?} else {
                    ClickEvent open = new ClickEvent(ClickEvent.Action.OPEN_URL, verificationUrl);
                    ClickEvent copy = new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, userCode);
                    HoverEvent hint = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"));
                    //?}
                    Component message = Component.literal("YouTube OAuth: Please go to ")
                        .append(Component.literal(verificationUrl)
                            .withStyle(Style.EMPTY.withColor(0x00AFFF).withClickEvent(open)))
                        .append(Component.literal(" and enter code "))
                        .append(Component.literal(userCode)
                            .withStyle(Style.EMPTY.withColor(0xFFD700).withClickEvent(copy).withHoverEvent(hint)));

                    player.sendSystemMessage(message);
                }

                while (true) {
                    try {
                        JsonBrowser tokenResponse = fetchRefreshTokenRaw(deviceCode);
                        if (!tokenResponse.get("error").isNull()) {
                            String error = tokenResponse.get("error").text();
                            if (error.equals("authorization_pending") || error.equals("slow_down")) {
                                if (error.equals("slow_down")) interval += 5000;
                                Thread.sleep(interval);
                                continue;
                            } else if (error.equals("expired_token")) {
                                player.sendSystemMessage(
                                        Component.literal("OAuth token expired! Run the command again."));
                                break;
                            } else if (error.equals("access_denied")) {
                                player.sendSystemMessage(Component.literal("OAuth access denied."));
                                break;
                            } else {
                                player.sendSystemMessage(Component.literal("Unhandled OAuth error: " + error));
                                break;
                            }
                        }

                        String refreshToken = tokenResponse.get("refresh_token").text();
                        SoundstageConfig.setOauthRefreshToken(refreshToken);
                        AudioManager.get().getYoutubeSource().useOauth2(refreshToken, true);
                        player.sendSystemMessage(Component.literal("Successfully authenticated YouTube OAuth!"));
                        break;
                    } catch (Exception e) {
                        LoggerFactory.getLogger("Soundstage").warn("OAuth polling failed", e);
                        player.sendSystemMessage(Component.literal(
                                "OAuth polling failed: " + e.getMessage() + ". Run the command again."));
                        break;
                    }
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("Failed to initiate OAuth: " + e.getMessage()));
            }
        }, "Soundstage-OAuth-" + player.getUUID().toString());
        oauthThread.setDaemon(true);
        oauthThread.start();
    }

    private static JsonBrowser fetchRefreshTokenRaw(String deviceCode) throws IOException {
        String requestJson = "{" +
            "\"client_id\":\"861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com\"," +
            "\"client_secret\":\"SboVhoG9s0rNafixCSGGKXAT\"," +
            "\"code\":\"" + deviceCode + "\"," +
            "\"grant_type\":\"http://oauth.net/grant_type/device/1.0\"" +
            "}";

        HttpPost request = new HttpPost("https://www.youtube.com/o/oauth2/token");
        StringEntity body = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
        request.setEntity(body);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            return JsonBrowser.parse(response.getEntity().getContent());
        }
    }
}
