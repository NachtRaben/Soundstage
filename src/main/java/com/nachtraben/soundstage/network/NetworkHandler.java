package com.nachtraben.soundstage.network;

import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.audio.AudioManagerState;
import com.nachtraben.soundstage.audio.LiveInputSessions;
import com.nachtraben.soundstage.audio.MetadataViewers;
import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.audio.TrackScheduler;
import com.nachtraben.soundstage.audio.media.MediaBrowseRequests;
import com.nachtraben.soundstage.audio.video.VideoRelay;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.ScreenBlock;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.client.ClientNetworkHandler;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.audio.LiveInputClient;
import com.nachtraben.soundstage.client.gui.AudioPlayerScreen;
import com.nachtraben.soundstage.client.gui.MediaBrowseScreen;
import com.nachtraben.soundstage.client.gui.NowPlayingToast;
import com.nachtraben.soundstage.client.video.ClientDisplayInfo;
import com.nachtraben.soundstage.client.video.ClientSubtitleOptions;
import com.nachtraben.soundstage.client.video.ClientSubtitles;
import com.nachtraben.soundstage.client.video.ClientVideoReceivers;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.core.video.DisplayLayout;
import com.nachtraben.soundstage.item.SpeakerLinkItem;
import com.nachtraben.soundstage.util.PlayerMessage;
import com.nachtraben.soundstage.util.Players;
import com.nachtraben.soundstage.util.WorldAccess;
import dev.armadeus.armalib.core.network.HandlerThread;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import dev.armadeus.armalib.platform.network.Network;
import dev.armadeus.armalib.platform.network.ServerPayloadContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NetworkHandler {

    public static void initClient() {
        ClientNetwork.handle(AudioPacket.TYPE, (payload, context) -> {
            AudioDecoder.handlePacket(payload);
        });
        ClientNetwork.handle(OAuthRequestS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> ClientNetworkHandler.handleOAuthRequest(payload));
        });
        ClientNetwork.handle(SpeakerMetadataS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> {
                if (Minecraft.getInstance().screen instanceof AudioPlayerScreen screen) {
                    screen.updateMetadata(payload);
                }
            });
        });
        ClientNetwork.handle(NowPlayingS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> {
                if (!SoundstageAppearance.trackChangeToast()) return;
                String artwork = SoundstageAppearance.trackChangeToastArtwork()
                        ? payload.getArtworkUrl() : "";
                NowPlayingToast.show(
                        payload.getReceiverPos(), payload.getTitle(), payload.getArtist(), artwork);
            });
        });
        ClientNetwork.handle(LiveInputStateS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> LiveInputClient.onServerState(payload));
        });
        ClientNetwork.handle(VideoPacket.TYPE, (payload, context) -> {
            context.queue(() -> ClientVideoReceivers.handle(payload));
        });
        ClientNetwork.handle(SubtitlePacket.TYPE, (payload, context) -> {
            context.queue(() -> ClientSubtitles.handle(payload));
        });
        ClientNetwork.handle(SubtitleOptionsS2CPacket.TYPE, (payload, context) ->
                context.queue(() -> ClientSubtitleOptions.handle(payload)));
        ClientNetwork.handle(DisplayInfoS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> ClientDisplayInfo.handle(payload));
        });
        ClientNetwork.handle(RemoteOpenS2CPacket.TYPE, (payload, context) ->
                context.queue(() -> SoundstageClientInit.openFromRemote(payload.receiverPos())));
        ClientNetwork.handle(MediaBrowseReplyS2CPacket.TYPE, (payload, context) -> {
            context.queue(() -> {
                if (Minecraft.getInstance().screen instanceof MediaBrowseScreen screen) {
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
        Network.playToClient(VideoPacket.TYPE, VideoPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(MediaBrowseReplyS2CPacket.TYPE, MediaBrowseReplyS2CPacket.STREAM_CODEC,
                HandlerThread.MAIN);
        Network.playToClient(SubtitlePacket.TYPE, SubtitlePacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(SubtitleOptionsS2CPacket.TYPE, SubtitleOptionsS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(RemoteOpenS2CPacket.TYPE, RemoteOpenS2CPacket.STREAM_CODEC, HandlerThread.MAIN);
        Network.playToClient(DisplayInfoS2CPacket.TYPE, DisplayInfoS2CPacket.STREAM_CODEC, HandlerThread.MAIN);

        Network.playToServer(LiveAudioC2SPacket.TYPE, LiveAudioC2SPacket.STREAM_CODEC, HandlerThread.NETWORK,
                (payload, context) -> {
            LiveInputSessions.offer(context.player().getUUID(), payload.opus);
        });
        Network.playToServer(SpeakerControlC2SPacket.TYPE, SpeakerControlC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerControlPacket);
        Network.playToServer(SpeakerRoutingC2SPacket.TYPE, SpeakerRoutingC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerRoutingPacket);
        Network.playToServer(SpeakerAimC2SPacket.TYPE, SpeakerAimC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerAimPacket);
        Network.playToServer(SpeakerRangeC2SPacket.TYPE, SpeakerRangeC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerRangePacket);
        Network.playToServer(SpeakerGrilleC2SPacket.TYPE, SpeakerGrilleC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerGrillePacket);
        Network.playToServer(QueueOpC2SPacket.TYPE, QueueOpC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleQueueOpPacket);
        Network.playToServer(SpeakerDelayC2SPacket.TYPE, SpeakerDelayC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerDelayPacket);
        Network.playToServer(SpeakerVolumeC2SPacket.TYPE, SpeakerVolumeC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSpeakerVolumePacket);
        Network.playToServer(MediaBrowseRequestC2SPacket.TYPE, MediaBrowseRequestC2SPacket.STREAM_CODEC,
                HandlerThread.MAIN, NetworkHandler::handleMediaBrowseRequest);
        Network.playToServer(ScreenFitModeC2SPacket.TYPE, ScreenFitModeC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleScreenFitModePacket);
        Network.playToServer(SubtitleChoiceC2SPacket.TYPE, SubtitleChoiceC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleSubtitleChoicePacket);
        Network.playToServer(VideoWatchC2SPacket.TYPE, VideoWatchC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleVideoWatchPacket);
        Network.playToServer(DisplayUnlinkC2SPacket.TYPE, DisplayUnlinkC2SPacket.STREAM_CODEC, HandlerThread.MAIN,
                NetworkHandler::handleDisplayUnlinkPacket);
    }

    @Nullable
    private static SpeakerBlockEntity editableSpeaker(ServerPlayer player, BlockPos pos) {
        if (!IAudioReceiver.withinControlRange(player, pos)) return null;
        ServerLevel level = Players.level(player);
        if (!(WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof SpeakerBlockEntity speaker)) {
            return null;
        }
        if (!AbstractSpeakerBlock.canEdit(level, player, speaker)) {
            PlayerMessage.actionBar(player,
                    Component.literal("Receiver is locked!"));
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
            BlockState state = level.getBlockState(packet.pos);
            if (state.getBlock() instanceof AbstractSpeakerBlock) {
                level.setBlock(packet.pos, state.setValue(
                        AbstractSpeakerBlock.GRILLE, packet.grille), 3);
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
                        || IAudioReceiver.withinControlRange(player, packet.pos)) {
                    AudioManager.get().handleControl(packet.pos, packet.action, packet.stringData, packet.intData,
                            packet.longData, player);
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
            if (!IAudioReceiver.withinControlRange(player, packet.pos)) return;
            if (!SoundstageConfig.enableMediaFolder()) return;
            ServerLevel level = Players.level(player);
            BlockEntity be = WorldAccess.blockEntityIfLoaded(level, packet.pos);
            if (!(be instanceof IAudioReceiver receiver)) return;
            if (!receiver.mayControl(player)) {
                PlayerMessage.actionBar(player,
                        Component.literal("Receiver is locked!"));
                return;
            }
            MediaBrowseRequests.handle(level, player, packet.pos);
        });
    }

    private static void handleScreenFitModePacket(ScreenFitModeC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (!IAudioReceiver.withinControlRange(player, packet.pos)) return;

            ServerLevel level = Players.level(player);
            if (!(WorldAccess.blockEntityIfLoaded(level, packet.pos)
                    instanceof ScreenBlockEntity screen)) {
                return;
            }
            ScreenBlockEntity.FitMode[] modes =
                    ScreenBlockEntity.FitMode.values();
            if (packet.fitMode < 0 || packet.fitMode >= modes.length) return;
            ScreenBlockEntity.FitMode mode = modes[packet.fitMode];

            BlockPos receiverPos = screen.getReceiverPos();
            if (receiverPos != null
                    && WorldAccess.blockEntityIfLoaded(level, receiverPos)
                            instanceof IAudioReceiver receiver
                    && !receiver.mayControl(player)) {
                PlayerMessage.actionBar(player,
                        Component.literal("This display's receiver is locked!"));
                return;
            }

            Direction facing = screen.getBlockState()
                    .getValue(ScreenBlock.FACING);
            DisplayLayout layout =
                    ScreenBlockEntity.findDisplay(level, packet.pos, facing);
            List<BlockPos> members = ScreenBlockEntity.memberPositions(packet.pos, facing, layout);
            for (BlockPos memberPos : members) {
                if (WorldAccess.blockEntityIfLoaded(level, memberPos)
                        instanceof ScreenBlockEntity member) {
                    member.setFitMode(mode);
                }
            }
        });
    }

    private static void handleSubtitleChoicePacket(SubtitleChoiceC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (!IAudioReceiver.withinControlRange(player, packet.pos())) return;
            if (!SubtitleChoiceThrottle.tryAcquire(player.getUUID(), System.currentTimeMillis())) return;
            ServerLevel level = Players.level(player);
            Object at = WorldAccess.blockEntityIfLoaded(level, packet.pos());
            BlockPos receiverPos = at instanceof ScreenBlockEntity screen
                    ? screen.getReceiverPos()
                    : at instanceof IAudioReceiver ? packet.pos() : null;
            if (receiverPos == null) return;
            if (!(WorldAccess.blockEntityIfLoaded(level, receiverPos)
                    instanceof IAudioReceiver receiver)) {
                return;
            }
            if (!receiver.mayControl(player)) {
                PlayerMessage.actionBar(player,
                        Component.literal("This display's receiver is locked!"));
                return;
            }
            TrackScheduler scheduler =
                    AudioManagerState.scheduler(receiverPos, level);
            VideoRelay relay = scheduler == null ? null : scheduler.videoRelay();
            if (relay == null || !relay.chooseSubtitles(packet.choice())) {
                PlayerMessage.actionBar(player,
                        Component.literal("Nothing playing has subtitles to choose."));
            }
        });
    }

    private static void handleVideoWatchPacket(VideoWatchC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            ServerLevel level = Players.level(player);
            if (!(WorldAccess.blockEntityIfLoaded(level, packet.receiverPos())
                    instanceof ReceiverBlockEntity)) {
                return;
            }
            TrackScheduler scheduler =
                    AudioManagerState.scheduler(packet.receiverPos(), level);
            VideoRelay relay = scheduler == null ? null : scheduler.videoRelay();
            if (relay != null) relay.watch(player, packet.watching());
        });
    }

    private static void handleDisplayUnlinkPacket(DisplayUnlinkC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (!IAudioReceiver.withinControlRange(player, packet.pos)) return;
            ServerLevel level = Players.level(player);
            if (!(WorldAccess.blockEntityIfLoaded(level, packet.pos)
                    instanceof ScreenBlockEntity screen)) {
                return;
            }
            SpeakerLinkItem.unlinkDisplay(level, player, packet.pos, screen);
        });
    }

    private static void handleQueueOpPacket(QueueOpC2SPacket packet, ServerPayloadContext context) {
        context.queue(() -> {
            ServerPlayer player = context.player();
            if (player == null) return;
            if (!IAudioReceiver.withinControlRange(player, packet.pos)) return;

            ServerLevel level = Players.level(player);
            BlockEntity be = WorldAccess.blockEntityIfLoaded(level, packet.pos);
            if (!(be instanceof IAudioReceiver receiver)) return;
            if (!receiver.mayControl(player)) {
                player.sendSystemMessage(Component.literal("This receiver is locked by the owner."));
                return;
            }
            AudioManager.noteController(receiver, player);

            TrackScheduler scheduler = AudioManager.get().getScheduler(packet.pos, level);
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
                if (IAudioReceiver.withinControlRange(player, packet.receiverPos)) {
                    BlockEntity be = Players.level(player).getBlockEntity(packet.receiverPos);
                    if (be instanceof ReceiverBlockEntity receiver) {
                        if (!receiver.mayControl(player)) {
                            player.sendSystemMessage(Component.literal("This receiver is locked by the owner."));
                            return;
                        }

                        if (!receiver.getSpeakers().contains(packet.speakerPos)) {
                            return;
                        }
                        BlockEntity speakerBe = WorldAccess
                                .blockEntityIfLoaded(Players.level(player), packet.speakerPos);
                        if (speakerBe instanceof SpeakerBlockEntity speaker) {
                            try {
                                ChannelMode newMode = ChannelMode.valueOf(packet.channelMode);
                                Block block = speakerBe.getBlockState().getBlock();
                                boolean isValid = block instanceof AbstractSpeakerBlock asb
                                        && Arrays.asList(asb.getValidModes()).contains(newMode);

                                if (isValid) {
                                    speaker.setChannelMode(newMode);
                                    Players.level(player).sendBlockUpdated(packet.speakerPos, speaker.getBlockState(),
                                            speaker.getBlockState(), 3);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        });
    }

    public static void sendToTracking(ServerLevel level, BlockPos pos, List<BlockPos> targets, AudioPacket packet) {
        List<BlockPos> emitters = new ArrayList<>();
        emitters.add(pos);
        if (targets != null) {
            emitters.addAll(targets);
        }

        for (ServerPlayer player : SpeakerAudience.collect(level, emitters)) {
            Network.sendToPlayer(player, packet);
        }
    }

    public static void sendMetadataToTracking(ServerLevel level, BlockPos pos, SpeakerMetadataS2CPacket packet) {
        for (ServerPlayer player : MetadataViewers.of(level, pos)) {
            Network.sendToPlayer(player, packet);
        }
    }
}
