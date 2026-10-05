package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.util.Players;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.gui.AudioPlayerScreen;
import dev.armadeus.armalib.core.network.HandlerThread;
import dev.armadeus.armalib.platform.Platform;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import dev.armadeus.armalib.platform.network.Network;
import dev.armadeus.armalib.platform.network.ServerPayloadContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

public class NetworkHandler {

    public static void initClient() {
        ClientNetwork.handle(AudioPacket.TYPE, (payload, context) -> {
            AudioDecoder.handlePacket(payload);
        });
        ClientNetwork.handle(OAuthRequestS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> com.nachtraben.soundstage.client.ClientNetworkHandler.handleOAuthRequest(payload));
        });
        ClientNetwork.handle(SpeakerMetadataS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof AudioPlayerScreen screen) {
                    screen.updateMetadata(payload);
                }
            });
        });
        ClientNetwork.handle(NowPlayingS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> {
                if (!com.nachtraben.soundstage.config.SoundstageAppearance.trackChangeToast()) return;
                String artwork = com.nachtraben.soundstage.config.SoundstageAppearance.trackChangeToastArtwork()
                        ? payload.getArtworkUrl() : "";
                com.nachtraben.soundstage.client.gui.NowPlayingToast.show(
                        payload.getReceiverPos(), payload.getTitle(), payload.getArtist(), artwork);
            });
        });
        ClientNetwork.handle(LiveInputStateS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> com.nachtraben.soundstage.client.audio.LiveInputClient.onServerState(payload));
        });
        ClientNetwork.handle(VideoPacket.TYPE, (payload, context) -> {
            context.queue(() -> com.nachtraben.soundstage.client.video.ClientVideoReceivers.handle(payload));
        });
        ClientNetwork.handle(SubtitlePacket.TYPE, (payload, context) -> {
            context.queue(() -> com.nachtraben.soundstage.client.video.ClientSubtitles.handle(payload));
        });
        ClientNetwork.handle(SubtitleOptionsS2CPacket.TYPE, (payload, context) ->
                context.queue(() -> com.nachtraben.soundstage.client.video.ClientSubtitleOptions.handle(payload)));
        ClientNetwork.handle(DisplayInfoS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> com.nachtraben.soundstage.client.video.ClientDisplayInfo.handle(payload));
        });
        ClientNetwork.handle(MediaBrowseReplyS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.nachtraben.soundstage.client.gui.MediaBrowseScreen screen) {
                    screen.updateResults(payload);
                }
            });
        });
    }

    public static void init() {
        Network.playToClient(AudioPacket.TYPE, AudioPacket.STREAM_CODEC, HandlerThread.NETWORK);
        Network.playToClient(OAuthRequestS2CPacket.TYPE, OAuthRequestS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(SpeakerMetadataS2CPacket.TYPE, SpeakerMetadataS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(NowPlayingS2CPacket.TYPE, NowPlayingS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(LiveInputStateS2CPacket.TYPE, LiveInputStateS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        if (!Platform.isClient()
                && com.nachtraben.soundstage.RigStaging.serverStages("soundstage:dedicated-video", "undeclared-video")) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn("Soundstage: red control — VideoPacket is "
                    + "not declared on this server (undeclared-video), so no "
                    + "screen will get a picture");
        } else {
            Network.playToClient(VideoPacket.TYPE, VideoPacket.STREAM_CODEC, HandlerThread.MAIN);
        }
        Network.playToClient(MediaBrowseReplyS2CPacket.TYPE, MediaBrowseReplyS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(SubtitlePacket.TYPE, SubtitlePacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(SubtitleOptionsS2CPacket.TYPE, SubtitleOptionsS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        if (!Platform.isClient() && com.nachtraben.soundstage.RigStaging.serverStages(
                "soundstage:dedicated-join", "undeclared-s2c")) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn("Soundstage: red control — "
                    + "DisplayInfoS2CPacket is not declared on this server (rig soundstage:dedicated-join)");
        } else {
            Network.playToClient(DisplayInfoS2CPacket.TYPE, DisplayInfoS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        }

        Network.playToServer(LiveAudioC2SPacket.TYPE, LiveAudioC2SPacket.STREAM_CODEC, HandlerThread.NETWORK, (payload, context) -> {
            com.nachtraben.soundstage.audio.LiveInputSessions.offer(context.player().getUUID(), payload.opus);
        });
        Network.playToServer(SpeakerControlC2SPacket.TYPE, SpeakerControlC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerControlPacket);
        Network.playToServer(SpeakerRoutingC2SPacket.TYPE, SpeakerRoutingC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerRoutingPacket);
        Network.playToServer(SpeakerAimC2SPacket.TYPE, SpeakerAimC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerAimPacket);
        Network.playToServer(SpeakerRangeC2SPacket.TYPE, SpeakerRangeC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerRangePacket);
        Network.playToServer(SpeakerGrilleC2SPacket.TYPE, SpeakerGrilleC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerGrillePacket);
        Network.playToServer(QueueOpC2SPacket.TYPE, QueueOpC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleQueueOpPacket);
        Network.playToServer(SpeakerDelayC2SPacket.TYPE, SpeakerDelayC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerDelayPacket);
        Network.playToServer(SpeakerVolumeC2SPacket.TYPE, SpeakerVolumeC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSpeakerVolumePacket);
        Network.playToServer(MediaBrowseRequestC2SPacket.TYPE, MediaBrowseRequestC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleMediaBrowseRequest);
        Network.playToServer(ScreenFitModeC2SPacket.TYPE, ScreenFitModeC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleScreenFitModePacket);
        Network.playToServer(SubtitleChoiceC2SPacket.TYPE, SubtitleChoiceC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleSubtitleChoicePacket);
        Network.playToServer(DisplayUnlinkC2SPacket.TYPE, DisplayUnlinkC2SPacket.STREAM_CODEC, HandlerThread.MAIN, NetworkHandler::handleDisplayUnlinkPacket);
    }

    @org.jetbrains.annotations.Nullable
    private static SpeakerBlockEntity editableSpeaker(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) >= 96 * 96) return null;
        ServerLevel level = Players.level(player);
        if (!(com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof SpeakerBlockEntity speaker)) {
            return null;
        }
        if (!com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock.canEdit(level, player, speaker)) {
            com.nachtraben.soundstage.util.PlayerMessage.actionBar(player,
                    net.minecraft.network.chat.Component.literal("Receiver is locked!"));
            return null;
        }
        return speaker;
    }

    private static void handleSpeakerGrillePacket(SpeakerGrilleC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (editableSpeaker(player, packet.pos) == null) return;
            ServerLevel level = Players.level(player);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(packet.pos);
            if (state.getBlock() instanceof com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock) {
                level.setBlock(packet.pos, state.setValue(
                        com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock.GRILLE, packet.grille), 3);
            }
        });
    }

    private static void handleSpeakerRangePacket(SpeakerRangeC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            SpeakerBlockEntity speaker = editableSpeaker(player, packet.pos);
            if (speaker == null) return;
            speaker.setRange(packet.range);
            Players.level(player).sendBlockUpdated(packet.pos, speaker.getBlockState(), speaker.getBlockState(), 3);
        });
    }

    private static void handleSpeakerAimPacket(SpeakerAimC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            SpeakerBlockEntity speaker = editableSpeaker(player, packet.pos);
            if (speaker == null) return;
            speaker.setPitch(packet.pitch);
            speaker.setYawOffset(packet.yaw);
            Players.level(player).sendBlockUpdated(packet.pos, speaker.getBlockState(), speaker.getBlockState(), 3);
        });
    }

    private static void handleSpeakerControlPacket(SpeakerControlC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player != null) {
                if (packet.action == SpeakerControlC2SPacket.Action.SHARE_STOP
                        || player.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) < 96 * 96) {
                    AudioManager.get().handleControl(packet.pos, packet.action, packet.stringData, packet.intData, packet.longData, player);
                }
            }
        });
    }

    private static void handleSpeakerDelayPacket(SpeakerDelayC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            SpeakerBlockEntity speaker = editableSpeaker(player, packet.pos);
            if (speaker == null) return;
            speaker.setDelayMs(packet.delayMs);
            Players.level(player).sendBlockUpdated(packet.pos, speaker.getBlockState(), speaker.getBlockState(), 3);
        });
    }

    private static void handleSpeakerVolumePacket(SpeakerVolumeC2SPacket packet,
                                                  ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            SpeakerBlockEntity speaker = editableSpeaker(player, packet.pos);
            if (speaker == null) return;
            speaker.setVolumePercent(packet.volumePercent);
            Players.level(player).sendBlockUpdated(packet.pos, speaker.getBlockState(), speaker.getBlockState(), 3);
        });
    }

    private static void handleMediaBrowseRequest(MediaBrowseRequestC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) >= 96 * 96) return;
            if (!com.nachtraben.soundstage.config.SoundstageConfig.enableMediaFolder()) return;
            ServerLevel level = Players.level(player);
            BlockEntity be = com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, packet.pos);
            if (!(be instanceof com.nachtraben.soundstage.block.IAudioReceiver receiver)) return;
            if (!receiver.mayControl(player)) {
                com.nachtraben.soundstage.util.PlayerMessage.actionBar(player,
                        net.minecraft.network.chat.Component.literal("Receiver is locked!"));
                return;
            }
            com.nachtraben.soundstage.audio.media.MediaBrowseRequests.handle(level, player, packet.pos);
        });
    }

    private static void handleScreenFitModePacket(ScreenFitModeC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) >= 96 * 96) return;

            ServerLevel level = Players.level(player);
            if (!(com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, packet.pos)
                    instanceof com.nachtraben.soundstage.block.ScreenBlockEntity screen)) {
                return;
            }
            com.nachtraben.soundstage.block.ScreenBlockEntity.FitMode[] modes =
                    com.nachtraben.soundstage.block.ScreenBlockEntity.FitMode.values();
            if (packet.fitMode < 0 || packet.fitMode >= modes.length) return;
            com.nachtraben.soundstage.block.ScreenBlockEntity.FitMode mode = modes[packet.fitMode];

            BlockPos receiverPos = screen.getReceiverPos();
            if (receiverPos != null
                    && com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, receiverPos)
                            instanceof com.nachtraben.soundstage.block.IAudioReceiver receiver
                    && !receiver.mayControl(player)) {
                com.nachtraben.soundstage.util.PlayerMessage.actionBar(player,
                        net.minecraft.network.chat.Component.literal("This display's receiver is locked!"));
                return;
            }

            net.minecraft.core.Direction facing = screen.getBlockState()
                    .getValue(com.nachtraben.soundstage.block.ScreenBlock.FACING);
            com.nachtraben.soundstage.core.video.DisplayLayout layout =
                    com.nachtraben.soundstage.block.ScreenBlockEntity.findDisplay(level, packet.pos, facing);
            java.util.List<BlockPos> members = com.nachtraben.soundstage.RigStaging.fault("fit-not-applied")
                    ? java.util.List.of(packet.pos)
                    : com.nachtraben.soundstage.block.ScreenBlockEntity.memberPositions(packet.pos, facing, layout);
            for (BlockPos memberPos : members) {
                if (com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, memberPos)
                        instanceof com.nachtraben.soundstage.block.ScreenBlockEntity member) {
                    member.setFitMode(mode);
                }
            }
        });
    }

    private static void handleSubtitleChoicePacket(SubtitleChoiceC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos().getX(), packet.pos().getY(), packet.pos().getZ()) >= 96 * 96) return;
            ServerLevel level = Players.level(player);
            Object at = com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, packet.pos());
            BlockPos receiverPos = at instanceof com.nachtraben.soundstage.block.ScreenBlockEntity screen
                    ? screen.getReceiverPos()
                    : at instanceof com.nachtraben.soundstage.block.IAudioReceiver ? packet.pos() : null;
            if (receiverPos == null) return;
            if (!(com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, receiverPos)
                    instanceof com.nachtraben.soundstage.block.IAudioReceiver receiver)) {
                return;
            }
            if (!receiver.mayControl(player)) {
                com.nachtraben.soundstage.util.PlayerMessage.actionBar(player,
                        net.minecraft.network.chat.Component.literal("This display's receiver is locked!"));
                return;
            }
            com.nachtraben.soundstage.audio.TrackScheduler scheduler =
                    com.nachtraben.soundstage.audio.AudioManagerState.scheduler(receiverPos, level);
            com.nachtraben.soundstage.audio.video.VideoRelay relay = scheduler == null ? null : scheduler.videoRelay();
            if (relay == null || !relay.chooseSubtitles(packet.choice())) {
                com.nachtraben.soundstage.util.PlayerMessage.actionBar(player,
                        net.minecraft.network.chat.Component.literal("Nothing playing has subtitles to choose."));
            }
        });
    }

    private static void handleDisplayUnlinkPacket(DisplayUnlinkC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) >= 96 * 96) return;
            ServerLevel level = Players.level(player);
            if (!(com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, packet.pos)
                    instanceof com.nachtraben.soundstage.block.ScreenBlockEntity screen)) {
                return;
            }
            com.nachtraben.soundstage.item.SpeakerLinkItem.unlinkDisplay(level, player, packet.pos, screen);
        });
    }

    private static void handleQueueOpPacket(QueueOpC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) >= 96 * 96) return;

            ServerLevel level = Players.level(player);
            BlockEntity be = com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, packet.pos);
            if (!(be instanceof com.nachtraben.soundstage.block.IAudioReceiver receiver)) return;
            if (!receiver.mayControl(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("This receiver is locked by the owner."));
                return;
            }
            AudioManager.noteController(receiver, player);

            com.nachtraben.soundstage.audio.TrackScheduler scheduler = AudioManager.get().getScheduler(packet.pos, level);
            if (scheduler == null) return;

            switch (packet.action) {
                case MOVE -> scheduler.moveEntry(packet.entryId, packet.targetIndex);
                case REMOVE -> scheduler.removeEntry(packet.entryId);
                case CLEAR -> scheduler.clearQueue();
                case PLAY_NOW -> {
                    if (AudioManager.get().mayStartPlaying(scheduler, player)) scheduler.playNow(packet.entryId);
                }
            }
        });
    }

    private static void handleSpeakerRoutingPacket(SpeakerRoutingC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player != null) {
                if (player.distanceToSqr(packet.receiverPos.getX(), packet.receiverPos.getY(), packet.receiverPos.getZ()) < 96 * 96) {
                    BlockEntity be = Players.level(player).getBlockEntity(packet.receiverPos);
                    if (be instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity receiver) {
                        if (!receiver.mayControl(player)) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("This receiver is locked by the owner."));
                            return;
                        }
                        
                        if (!receiver.getSpeakers().contains(packet.speakerPos)) {
                            return;
                        }
                        BlockEntity speakerBe = com.nachtraben.soundstage.util.WorldAccess
                                .blockEntityIfLoaded(Players.level(player), packet.speakerPos);
                        if (speakerBe instanceof com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity speaker) {
                            try {
                                com.nachtraben.soundstage.core.ChannelMode newMode = com.nachtraben.soundstage.core.ChannelMode.valueOf(packet.channelMode);
                                net.minecraft.world.level.block.Block block = speakerBe.getBlockState().getBlock();
                                boolean isValid = block instanceof com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock asb
                                        && java.util.Arrays.asList(asb.getValidModes()).contains(newMode);
                                
                                if (isValid) {
                                    speaker.setChannelMode(newMode);
                                    Players.level(player).sendBlockUpdated(packet.speakerPos, speaker.getBlockState(), speaker.getBlockState(), 3);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        });
    }

    public static void sendToTracking(ServerLevel level, BlockPos pos, java.util.List<BlockPos> targets, AudioPacket packet) {
        java.util.List<BlockPos> emitters = new java.util.ArrayList<>();
        emitters.add(pos);
        if (targets != null) {
            emitters.addAll(targets);
        }

        for (ServerPlayer player : SpeakerAudience.collect(level, emitters)) {
            Network.sendToPlayer(player, packet);
        }
    }

    public static void sendMetadataToTracking(ServerLevel level, BlockPos pos, SpeakerMetadataS2CPacket packet) {
        for (ServerPlayer player : com.nachtraben.soundstage.audio.MetadataViewers.of(level, pos)) {
            Network.sendToPlayer(player, packet);
        }
    }
}
