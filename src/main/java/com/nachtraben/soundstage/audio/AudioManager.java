package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.util.Players;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.AudioSourceRegistry;
import com.nachtraben.soundstage.core.IdentityScopedKey;
import com.nachtraben.soundstage.core.IdleReclaim;
import com.nachtraben.soundstage.core.PlaylistPolicy;
import com.nachtraben.soundstage.core.QueueAdmission;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.http.YoutubeOauth2Handler;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Accessors(fluent = true)
public class AudioManager {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");
    @Getter
    private static final AudioManager get = new AudioManager();
    @Getter
    private final AudioPlayerManager playerManager;
    private final Map<IdentityScopedKey<ServerLevel, BlockPos>, AudioPlayer> activePlayers = new ConcurrentHashMap<>();
    private final Map<IdentityScopedKey<ServerLevel, BlockPos>, TrackScheduler> trackSchedulers = new ConcurrentHashMap<>();
    private final Map<IdentityScopedKey<ServerLevel, BlockPos>, AudioEncoder> activeEncoders = new ConcurrentHashMap<>();
    private final Map<IdentityScopedKey<ServerLevel, BlockPos>, com.nachtraben.soundstage.audio.video.VideoRelay> activeRelays =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupService =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Soundstage-Idle-Sweep");
                thread.setDaemon(true);
                return thread;
            });

    private final YoutubeAudioSourceManager ytSource;

    private AudioManager() {
        org.apache.logging.log4j.core.config.Configurator.setLevel(
                YoutubeOauth2Handler.class.getName(), org.apache.logging.log4j.Level.INFO);
        org.apache.logging.log4j.core.config.Configurator.setLevel(
                com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager.class.getPackageName(),
                org.apache.logging.log4j.Level.INFO);

        if (com.nachtraben.soundstage.RigStaging.sessionRuns("soundstage:http-stream-resumes")) {
            com.nachtraben.soundstage.config.SoundstageConfigSpec.enableHttpForRigSession();
        }
        java.util.List<com.sedmelluq.discord.lavaplayer.source.AudioSourceManager> first =
                SoundstageConfig.enableMediaFolder()
                        ? java.util.List.of(new com.nachtraben.soundstage.audio.media.MediaFolderSourceManager())
                        : java.util.List.of();
        AudioSourceRegistry.Result sources = AudioSourceRegistry.create(SoundstageConfig.settings(), first,
                com.nachtraben.soundstage.audio.media.YoutubeVideoSourceManager::new,
                com.nachtraben.soundstage.audio.media.TwitchVideoSourceManager::new,
                com.nachtraben.soundstage.audio.media.WebMediaSourceManager::new);
        this.playerManager = sources.playerManager();
        this.ytSource = sources.youtubeSource();

        cleanupService.scheduleAtFixedRate(this::cleanupIdlePlayers, 5, 5, TimeUnit.MINUTES);
        AudioManagerState.markCreated();
    }

    private static IdentityScopedKey<ServerLevel, BlockPos> sessionKey(ServerLevel level, BlockPos pos) {
        return IdentityScopedKey.of(level, pos.immutable());
    }

    public AudioPlayer getOrCreatePlayer(BlockPos pos, ServerLevel level) {
        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        net.minecraft.world.level.block.entity.BlockEntity currentBlockEntity = level.getBlockEntity(key.local());
        com.nachtraben.soundstage.block.IAudioReceiver currentReceiver =
                currentBlockEntity instanceof com.nachtraben.soundstage.block.IAudioReceiver found ? found : null;
        TrackScheduler existingScheduler = trackSchedulers.get(key);
        if (existingScheduler != null && !existingScheduler.ownsReceiver(currentReceiver)) {
            removePlayer(key);
        }
        return activePlayers.computeIfAbsent(key, ignored -> {
            BlockPos receiverPos = key.local();
            com.nachtraben.soundstage.block.IAudioReceiver receiver = currentReceiver;
            AudioPlayer player = playerManager.createPlayer();
            lastActiveMillis.put(key, System.currentTimeMillis());
            com.nachtraben.soundstage.audio.video.VideoRelay relay =
                    com.nachtraben.soundstage.audio.video.VideoRelay.forReceiver(player, level, receiverPos, receiver);
            TrackScheduler scheduler = new TrackScheduler(player, receiverPos, level, receiver, relay);
            player.addListener(scheduler);
            trackSchedulers.put(key, scheduler);

            java.util.List<BlockPos> targets = null;
            if (receiver != null) {
                targets = receiver.getSpeakers();
                player.setVolume(Math.min(receiver.getVolume(),
                        com.nachtraben.soundstage.block.IAudioReceiver.LAVAPLAYER_MAX_VOLUME));
                scheduler.setLoopMode(receiver.getLoopMode());
                scheduler.setShuffle(receiver.isShuffle());

                String savedTrack = receiver.getCurrentTrackData();
                boolean hasQueue = receiver.getQueueData() != null && !receiver.getQueueData().isEmpty();
                if (((savedTrack != null && !savedTrack.isEmpty()) || hasQueue)
                        && !com.nachtraben.soundstage.RigStaging.fault("restore-skipped")) {
                    player.setPaused(!receiver.isPlaying());
                    restoreState(receiverPos, level, receiver, scheduler);
                }
            }

            AudioEncoder encoder = new AudioEncoder();
            if (relay != null) {
                java.util.List<BlockPos> speakers = targets;
                relay.surround().setWanted(() -> speakers != null
                        && com.nachtraben.soundstage.audio.SpeakerAudience.wantsSurround(level, speakers));
            }
            encoder.setSurround(relay != null ? relay.surround() : null);
            encoder.startEncodingLoop(player, level, receiverPos, targets, receiver);
            activeEncoders.put(key, encoder);
            if (relay != null) {
                relay.start();
                activeRelays.put(key, relay);
            }
            return player;
        });
    }

    private void restoreState(BlockPos pos, net.minecraft.server.level.ServerLevel level, com.nachtraben.soundstage.block.IAudioReceiver cbe, TrackScheduler scheduler) {
        String currentData = cbe.getCurrentTrackData();
        long savedPosition = cbe.getCurrentTrackPosition();
        java.util.List<String> queueData = new java.util.ArrayList<>(cbe.getQueueData());

        int restored = 0;
        int failed = 0;

        if (currentData != null && !currentData.isEmpty()) {
            com.sedmelluq.discord.lavaplayer.track.AudioTrack track = decodeTrack(currentData);
            if (track != null) {
                scheduler.setPendingSeek(savedPosition);
                scheduler.queue(track, false);
                restored++;
            } else {
                failed++;
                LOGGER.warn("Receiver at {} could not decode its saved current track; it will not resume.", pos);
            }
        }
        for (String qData : queueData) {
            com.sedmelluq.discord.lavaplayer.track.AudioTrack t = decodeTrack(qData);
            if (t != null) {
                scheduler.queue(t, false);
                restored++;
            } else {
                failed++;
            }
        }

        if (!com.nachtraben.soundstage.RigStaging.fault("restore-unflushed")) {
            scheduler.persistToReceiver();
        }

        if (failed > 0) {
            LOGGER.warn("Receiver at {} restored {} track(s) and dropped {} that would not decode. "
                    + "Persisted tracks are LavaPlayer's own encoding, so a source that has changed "
                    + "shape since they were saved renders them unreadable.", pos, restored, failed);
        } else if (restored > 0) {
            LOGGER.info("Receiver at {} restored {} track(s) from saved state.", pos, restored);
        }
    }

    public AudioPlayer getPlayer(BlockPos pos, ServerLevel level) {
        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        AudioPlayer player = getOrCreatePlayer(pos, level);
        AudioEncoder encoder = activeEncoders.get(key);
        if (encoder != null && !encoder.isAlive()) {
            encoder.stop();
            AudioEncoder newEncoder = new AudioEncoder();
            TrackScheduler restarted = trackSchedulers.get(key);
            com.nachtraben.soundstage.audio.video.VideoRelay restartedRelay =
                    restarted == null ? null : restarted.videoRelay();
            newEncoder.setSurround(restartedRelay != null ? restartedRelay.surround() : null);
            java.util.List<BlockPos> targets = null;
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            com.nachtraben.soundstage.block.IAudioReceiver receiver = null;
            if (be instanceof com.nachtraben.soundstage.block.IAudioReceiver cbe) {
                targets = cbe.getSpeakers();
                receiver = cbe;
            }
            newEncoder.startEncodingLoop(player, level, pos, targets, receiver);
            activeEncoders.put(key, newEncoder);
        }
        return player;
    }

    public String encodeTrack(com.sedmelluq.discord.lavaplayer.track.AudioTrack track) {
        if (track instanceof LiveInputTrack && !com.nachtraben.soundstage.RigStaging.fault("share-saved")) return "";
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            playerManager.encodeTrack(new com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput(baos), track);
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.error("Failed to encode track for persistence", e);
            return "";
        }
    }

    public com.sedmelluq.discord.lavaplayer.track.AudioTrack decodeTrack(String base64) {
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            return playerManager.decodeTrack(new com.sedmelluq.discord.lavaplayer.tools.io.MessageInput(bais)).decodedTrack;
        } catch (Exception e) {
            LOGGER.error("Failed to decode a persisted track; dropping it from the restored queue", e);
            return null;
        }
    }

    public void removePlayer(BlockPos pos, ServerLevel level) {
        removePlayer(sessionKey(level, pos));
    }

    private void removePlayer(IdentityScopedKey<ServerLevel, BlockPos> key) {
        TrackScheduler scheduler = trackSchedulers.get(key);
        if (scheduler != null) {
            if (!com.nachtraben.soundstage.RigStaging.fault("shares-outlive-receiver")) {
                LiveInputSessions.endAllFor(scheduler, LiveInputSession.End.RECEIVER_GONE);
            }
            scheduler.beginTeardown();
            trackSchedulers.remove(key, scheduler);
        } else {
            trackSchedulers.remove(key);
        }
        AudioPlayer player = activePlayers.remove(key);
        if (player != null) {
            player.destroy();
        }
        lastActiveMillis.remove(key);
        pendingLoads.forget(key);
        AudioEncoder encoder = activeEncoders.remove(key);
        if (encoder != null) {
            encoder.stop();
        }
        com.nachtraben.soundstage.audio.video.VideoRelay relay = activeRelays.remove(key);
        if (relay != null) {
            relay.stop();
        }
    }

    public void shutdown() {
        LiveInputSessions.forgetAll();
        for (IdentityScopedKey<ServerLevel, BlockPos> key : activePlayers.keySet()) {
            removePlayer(key);
        }
        activePlayers.clear();
        trackSchedulers.clear();
        activeEncoders.clear();
        activeRelays.clear();
        lastActiveMillis.clear();
        pendingLoads.clear();
        SpeakerAudience.forgetAll();
    }

    private static boolean needsLiveTrack(com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action action) {
        return switch (action) {
            case PLAY_PAUSE, SKIP, SEEK, QUEUE, SHARE_START -> true;
            case STOP, REQUEST_SYNC, CLOSE_SYNC, SET_VOLUME, TOGGLE_LOOP, TOGGLE_SHUFFLE,
                    TOGGLE_LOCK, SHARE_STOP, TOGGLE_PICTURE, TOGGLE_LEVELLING -> false;
        };
    }

    public boolean reviveIfNeeded(BlockPos pos, ServerLevel level) {
        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        TrackScheduler scheduler = trackSchedulers.get(key);
        if (scheduler == null || !scheduler.isReclaimed()) return false;
        if (scheduler.getPlayer().getPlayingTrack() != null) return false;

        com.nachtraben.soundstage.block.IAudioReceiver cbe = currentReceiver(key, scheduler);
        if (cbe == null) {
            return false;
        }
        String currentData = cbe.getCurrentTrackData();
        if (currentData == null || currentData.isEmpty()) {
            LOGGER.warn("Receiver at {} is reclaimed but holds no snapshot to revive from.", pos);
            return false;
        }
        LOGGER.info("Receiver at {} was reclaimed while idle; reviving from saved state.", pos);
        com.sedmelluq.discord.lavaplayer.track.AudioTrack track = decodeTrack(currentData);
        if (track == null) {
            LOGGER.warn("Receiver at {} could not decode its saved current track; it will not resume.", pos);
            return false;
        }
        scheduler.setPendingSeek(cbe.getCurrentTrackPosition());
        scheduler.queue(track, false);
        LOGGER.info("Receiver at {} restored 1 track(s) from saved state.", pos);
        return true;
    }

    void resumeAfterServerPause(BlockPos pos, ServerLevel level, AudioPlayer expected) {
        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        if (activePlayers.get(key) != expected) return;
        TrackScheduler scheduler = trackSchedulers.get(key);
        if (scheduler == null || currentReceiver(key, scheduler) == null) {
            expected.setPaused(false);
            return;
        }
        if (reviveIfNeeded(pos, level)) {
            expected.setPaused(false);
        } else {
            scheduler.resume(false);
        }
    }

    public TrackScheduler getScheduler(BlockPos pos, ServerLevel level) {
        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        TrackScheduler scheduler = trackSchedulers.get(key);
        return scheduler != null && currentReceiver(key, scheduler) != null ? scheduler : null;
    }

    boolean isCurrentSession(ServerLevel level, BlockPos pos, TrackScheduler scheduler) {
        return currentReceiver(sessionKey(level, pos), scheduler) != null;
    }

    boolean isRegisteredSession(ServerLevel level, BlockPos pos, TrackScheduler scheduler) {
        return trackSchedulers.get(sessionKey(level, pos)) == scheduler;
    }

    private com.nachtraben.soundstage.block.IAudioReceiver currentReceiver(
            IdentityScopedKey<ServerLevel, BlockPos> key, TrackScheduler scheduler) {
        if (trackSchedulers.get(key) != scheduler) {
            return null;
        }
        net.minecraft.world.level.block.entity.BlockEntity be =
                com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(key.scope(), key.local());
        return be instanceof com.nachtraben.soundstage.block.IAudioReceiver receiver
                && scheduler.ownsReceiver(receiver) ? receiver : null;
    }

    public static void noteController(com.nachtraben.soundstage.block.IAudioReceiver receiver, ServerPlayer player) {
        if (receiver instanceof com.nachtraben.soundstage.block.ReceiverBlockEntity withScreens) {
            withScreens.setControllerLanguage(com.nachtraben.soundstage.util.Players.language(player));
        }
    }

    public void handleControl(BlockPos pos, com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action action, String stringData, int intData, long longData, ServerPlayer player) {
        if (action == com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action.SHARE_STOP) {
            LiveInputSessions.stop(player.getUUID(), LiveInputSession.End.STOPPED);
            return;
        }
        net.minecraft.server.level.ServerLevel level = Players.level(player);
        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        net.minecraft.world.level.block.entity.BlockEntity be =
                com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, pos);
        if (!(be instanceof com.nachtraben.soundstage.block.IAudioReceiver receiver)) {
            return;
        }
        if (action == com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action.CLOSE_SYNC) {
            MetadataViewers.closed(level, pos, player);
            return;
        }
        if (!receiver.mayControl(player)
                && action != com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action.REQUEST_SYNC) {
            player.sendSystemMessage(Component.literal("This receiver is locked by the owner."));
            if (action == com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action.SHARE_START) {
                dev.armadeus.armalib.platform.network.Network.sendToPlayer(player,
                        com.nachtraben.soundstage.network.LiveInputStateS2CPacket.refused(pos));
            }
            return;
        }
        if (action != com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action.REQUEST_SYNC) {
            noteController(receiver, player);
        }
        if (action == com.nachtraben.soundstage.network.SpeakerControlC2SPacket.Action.TOGGLE_LOCK) {
            if (receiver.mayToggleLock(player)) {
                receiver.setLocked(!receiver.isLocked());
                player.sendSystemMessage(Component.literal("Receiver is now " + (receiver.isLocked() ? "Locked" : "Unlocked")));
                TrackScheduler scheduler = trackSchedulers.get(key);
                if (scheduler != null) scheduler.syncMetadata();
            } else {
                player.sendSystemMessage(Component.literal("Only the owner can lock this receiver."));
            }
            return;
        }

        TrackScheduler before = trackSchedulers.get(key);
        boolean wasPlaying = before != null && before.isActivelyPlaying();
        boolean revived = needsLiveTrack(action) && reviveIfNeeded(pos, level);

        AudioPlayer audioPlayer = getPlayer(pos, Players.level(player));
        TrackScheduler scheduler = trackSchedulers.get(key);
        if (audioPlayer != null && scheduler != null) {
            switch (action) {
                case PLAY_PAUSE:
                    if (wasPlaying) {
                        audioPlayer.setPaused(true);
                    } else if (!mayStartPlaying(scheduler, player)) {
                    } else {
                        scheduler.clearSuspension();
                        if (revived) {
                            audioPlayer.setPaused(false);
                        } else {
                            scheduler.resume(true);
                        }
                    }
                    receiver.setPlaying(scheduler.isActivelyPlaying());
                    scheduler.syncMetadata();
                    break;
                case SKIP:
                    scheduler.clearSuspension();
                    scheduler.skipTrack();
                    persistQueueState(key, scheduler);
                    break;
                case SET_VOLUME:
                    int volume = Math.max(0, Math.min(com.nachtraben.soundstage.block.IAudioReceiver.MAX_VOLUME, intData));
                    audioPlayer.setVolume(Math.min(volume, com.nachtraben.soundstage.block.IAudioReceiver.LAVAPLAYER_MAX_VOLUME));
                    receiver.setVolume(volume);
                    scheduler.syncMetadata();
                    break;
                case QUEUE:
                    play(pos, stringData, Players.level(player), player);
                    break;
                case REQUEST_SYNC:
                    MetadataViewers.opened(Players.level(player), pos, player);
                    scheduler.syncMetadata();
                    break;
                case STOP:
                    scheduler.stop();
                    receiver.setCurrentTrackData("");
                    receiver.setCurrentTrackPosition(0);
                    receiver.setQueueData(java.util.Collections.emptyList());
                    receiver.setPlaying(false);
                    break;
                case TOGGLE_LOOP:
                    int newLoopMode = (scheduler.getLoopMode() + 1) % 3;
                    scheduler.setLoopMode(newLoopMode);
                    receiver.setLoopMode(newLoopMode);
                    scheduler.syncMetadata();
                    break;
                case TOGGLE_SHUFFLE:
                    scheduler.setShuffle(!scheduler.isShuffle());
                    receiver.setShuffle(scheduler.isShuffle());
                    scheduler.syncMetadata();
                    break;
                case SEEK:
                    long position = Math.max(0L, longData);
                    scheduler.seek(position);
                    receiver.setCurrentTrackPosition(position);
                    break;
                case SHARE_START:
                    startShare(pos, level, scheduler, receiver, player);
                    break;
                case TOGGLE_PICTURE:
                    receiver.setPictureEnabled(!receiver.isPictureEnabled());
                    if (!com.nachtraben.soundstage.RigStaging.fault("picture-toggle-late")) scheduler.replayCurrent();
                    break;
                case TOGGLE_LEVELLING:
                    receiver.setLevelling(!receiver.isLevelling());
                    scheduler.syncMetadata();
                    break;
                default:
                    break;
            }
        }
    }

    private void startShare(BlockPos pos, ServerLevel level, TrackScheduler scheduler,
                            com.nachtraben.soundstage.block.IAudioReceiver receiver, ServerPlayer player) {
        String refusal = com.nachtraben.soundstage.RigStaging.fault("share-refused") ? "chat.soundstage.share.disabled"
                : !SoundstageConfig.enableLiveInput() && !com.nachtraben.soundstage.RigStaging.fault("live-input-unchecked") ? "chat.soundstage.share.disabled"
                : !com.nachtraben.soundstage.core.OpusCodec.available() ? "chat.soundstage.share.no_codec"
                : null;
        if (refusal != null) {
            player.sendSystemMessage(Component.translatable(refusal).withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (refusal != null || !mayStartPlaying(scheduler, player)) {
            dev.armadeus.armalib.platform.network.Network.sendToPlayer(player,
                    com.nachtraben.soundstage.network.LiveInputStateS2CPacket.refused(pos));
            return;
        }
        LiveInputSession session = LiveInputSessions.begin(player, level, pos, scheduler);
        scheduler.startLiveShare(new LiveInputTrack(session));
        receiver.setPlaying(scheduler.isActivelyPlaying());
        dev.armadeus.armalib.platform.network.Network.sendToPlayer(player,
                com.nachtraben.soundstage.network.LiveInputStateS2CPacket.started(pos, session.id()));
        LOGGER.info("Receiver at {} is playing audio shared by {}", pos, session.sharerName());
    }

    private void persistQueueState(IdentityScopedKey<ServerLevel, BlockPos> key, TrackScheduler scheduler) {
        com.nachtraben.soundstage.block.IAudioReceiver cbe = currentReceiver(key, scheduler);
        if (cbe != null) {
            com.sedmelluq.discord.lavaplayer.track.AudioTrack current = scheduler.getPlayer().getPlayingTrack();
            if (current != null && !(current instanceof LiveInputTrack)) {
                cbe.setCurrentTrackData(encodeTrack(current));
                cbe.setCurrentTrackPosition(current.getPosition());
            } else {
                cbe.setCurrentTrackData("");
                cbe.setCurrentTrackPosition(0);
            }
            java.util.List<String> dataList = new java.util.ArrayList<>();
            for (TrackScheduler.QueueEntry entry : scheduler.getQueue()) {
                if (!entry.encoded().isEmpty()) dataList.add(entry.encoded());
            }
            cbe.setQueueData(dataList);
            cbe.setPlaying(!scheduler.getPlayer().isPaused());
        }
    }

    public void broadcastMessage(BlockPos pos, ServerLevel level, TrackScheduler scheduler, Component message) {
        broadcastToNearby(pos, level, scheduler, player -> player.sendSystemMessage(message));
    }

    public void broadcastToNearby(BlockPos pos, ServerLevel level, TrackScheduler scheduler,
                                  java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        level.getServer().execute(() -> {
            IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
            com.nachtraben.soundstage.block.IAudioReceiver receiver = currentReceiver(key, scheduler);
            if (receiver == null) {
                return;
            }
            java.util.List<BlockPos> speakers = new java.util.ArrayList<>();
            if (receiver.getSpeakers() != null) {
                speakers.addAll(receiver.getSpeakers());
            }
            if (speakers.isEmpty()) {
                speakers.add(pos);
            }

            java.util.Set<net.minecraft.server.level.ServerPlayer> notified = new java.util.HashSet<>();
            for (BlockPos speakerPos : speakers) {
                net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(speakerPos).inflate(64.0D);
                for (net.minecraft.server.level.ServerPlayer p : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, aabb)) {
                    if (notified.add(p)) {
                        action.accept(p);
                    }
                }
            }
        });
    }

    public void play(BlockPos pos, String url, ServerLevel level, ServerPlayer player) {
        if (!SoundstageConfig.isUrlAllowed(url)) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("That URL is not permitted on this server.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return;
        }

        if (com.nachtraben.soundstage.core.PrivateNetworkAccess.unreadableHost(url)) {
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                                com.nachtraben.soundstage.core.PrivateNetworkAccess.REFUSED_HOST_FORM)
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return;
        }

        IdentityScopedKey<ServerLevel, BlockPos> key = sessionKey(level, pos);
        getPlayer(pos, level);
        TrackScheduler scheduler = trackSchedulers.get(key);
        if (scheduler == null || currentReceiver(key, scheduler) == null) {
            return;
        }

        AudioPlayer audioPlayer = activePlayers.get(key);
        if (player != null && (audioPlayer == null || audioPlayer.getPlayingTrack() == null)
                && !mayStartPlaying(scheduler, player)) {
            return;
        }

        Runnable settle = player == null || com.nachtraben.soundstage.RigStaging.fault("cap-ignored")
                ? () -> { } : pendingLoads.tryAcquire(key);
        if (settle != null && com.nachtraben.soundstage.RigStaging.fault("slots-kept")) settle = () -> { };
        if (settle == null) {
            player.sendSystemMessage(Component.literal("Still loading earlier requests for this receiver; try again in a moment.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        if (scheduler.isLoadingSuspendedForUrl(url) && !com.nachtraben.soundstage.RigStaging.fault("breaker-ignored")) {
            if (player == null) {
                return;
            }
            if (!com.nachtraben.soundstage.RigStaging.fault("breaker-retry-ignored")) {
                scheduler.clearSuspension();
            }
        }
        try {
            startLoad(key, pos, url, level, player, scheduler, settle);
        } catch (RuntimeException e) {
            settle.run();
            throw e;
        }
    }

    private TrackScheduler loadTarget(IdentityScopedKey<ServerLevel, BlockPos> key, BlockPos pos,
                                      ServerLevel level, TrackScheduler started) {
        if (!com.nachtraben.soundstage.RigStaging.fault("stale-load-lands")) return started;
        getPlayer(pos, level);
        TrackScheduler now = trackSchedulers.get(key);
        return now != null ? now : started;
    }

    private void startLoad(IdentityScopedKey<ServerLevel, BlockPos> key, BlockPos pos, String url,
                           ServerLevel level, ServerPlayer player, TrackScheduler scheduler, Runnable settle) {
        boolean operator = player != null && com.nachtraben.soundstage.block.IAudioReceiver.operator(player);
        boolean allowPrivate = SoundstageConfig.allowPrivateNetworkHosts();
        playerManager.loadItem(com.nachtraben.soundstage.core.PrivateNetworkAccess.reference(url, allowPrivate, operator),
                new com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler() {
            @Override
            public void trackLoaded(com.sedmelluq.discord.lavaplayer.track.AudioTrack track) {
                settle.run();
                level.getServer().execute(() -> {
                    TrackScheduler target = loadTarget(key, pos, level, scheduler);
                    if (currentReceiver(key, target) == null) {
                        return;
                    }
                    if (QueueAdmission.of(target.queueSize(), SoundstageConfig.maxQueueSize(), 1, 1).full()) {
                        broadcastMessage(pos, level, target, Component.literal("Queue is full (" + SoundstageConfig.maxQueueSize() + " tracks).")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    target.onLoadSucceeded(url);
                    target.queue(track);
                    persistQueueState(key, target);
                });
            }

            @Override
            public void playlistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                settle.run();
                level.getServer().execute(() -> {
                    if (currentReceiver(key, scheduler) == null) return;
                    if (playlist.getTracks().isEmpty()) return;

                    com.sedmelluq.discord.lavaplayer.track.AudioTrack selected = playlist.getSelectedTrack();
                    if (selected == null && playlist.isSearchResult()) {
                        selected = playlist.getTracks().get(0);
                    }

                    if (PlaylistPolicy.decide(url, playlist.isSearchResult(), selected != null)
                            == PlaylistPolicy.Decision.SELECTED_ONLY && selected != null) {
                        trackLoaded(selected);
                        return;
                    }

                    QueueAdmission admission = QueueAdmission.of(scheduler.queueSize(),
                            SoundstageConfig.maxQueueSize(), SoundstageConfig.maxPlaylistTracks(),
                            playlist.getTracks().size());

                    int[] order = PlaylistPolicy.order(admission.available(), playlist.getTracks().indexOf(selected));

                    if (admission.full()) {
                        broadcastMessage(pos, level, scheduler, Component.literal("Queue is full (" + SoundstageConfig.maxQueueSize() + " tracks).")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }

                    scheduler.onLoadSucceeded(url);
                    for (int i = 0; i < admission.accepted(); i++) {
                        scheduler.queue(playlist.getTracks().get(order[i]));
                    }
                    persistQueueState(key, scheduler);

                    String summary = admission.truncated()
                            ? "Queued " + admission.accepted() + " of " + admission.available()
                                    + " tracks from " + playlist.getName() + " (limit reached)"
                            : "Queued playlist: " + playlist.getName();
                    if (admission.truncated()) {
                        broadcastMessage(pos, level, scheduler, Component.literal(summary).withStyle(net.minecraft.ChatFormatting.YELLOW));
                    }
                });
            }

            @Override
            public void noMatches() {
                settle.run();
                level.getServer().execute(() -> {
                    if (currentReceiver(key, scheduler) == null) return;
                    LOGGER.warn("Receiver at {} found no source that could load {}", pos, url);
                    broadcastMessage(pos, level, scheduler, Component.literal("No matches found for: " + url).withStyle(net.minecraft.ChatFormatting.RED));
                    if (com.nachtraben.soundstage.RigStaging.fault("no-matches-trips")) {
                        scheduler.onLoadFailed(url, new com.sedmelluq.discord.lavaplayer.tools.FriendlyException(
                                "staged: no matches treated as a failure",
                                com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON, null));
                    }
                });
            }

            @Override
            public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                settle.run();
                level.getServer().execute(() -> {
                    if (currentReceiver(key, scheduler) == null) return;
                    LOGGER.warn("Receiver at {} failed to load {}", pos, url, exception);
                    String refused = com.nachtraben.soundstage.core.PrivateNetworkAccess.messageFor(exception, allowPrivate);
                    String source = com.nachtraben.soundstage.core.SourceKey.forUrl(url);
                    broadcastMessage(pos, level, scheduler, Component.literal(refused != null ? refused
                                    : com.nachtraben.soundstage.core.LoadFailureMessage.forPlayer(source, exception.getMessage()))
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    if (refused != null) return;

                    scheduler.onLoadFailed(url, exception);

                    if (player != null && Players.level(player) == level
                            && Players.level(player).getServer().isSingleplayer()
                            && com.nachtraben.soundstage.core.LoadFailureMessage.looksLikeAuthFailure(source, exception.getMessage())) {
                        if (ytSource != null && !ytSource.getOauth2Handler().hasAccessToken()) {
                            com.nachtraben.soundstage.command.SoundstageCommand.triggerOAuthFlow(player, false);
                        }
                    }
                });
            }
        });
    }

    public int playingReceiverCount() {
        int playing = 0;
        for (TrackScheduler scheduler : trackSchedulers.values()) {
            if (scheduler.isActivelyPlaying()) playing++;
        }
        return playing;
    }

    public boolean mayStartPlaying(TrackScheduler scheduler, ServerPlayer player) {
        if (com.nachtraben.soundstage.RigStaging.fault("cap-ignored")) {
            return true;
        }
        if (com.nachtraben.soundstage.RigStaging.fault("cap-always-refuses")) {
            player.sendSystemMessage(Component.translatable("chat.soundstage.too_many_playing", 0)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        int cap = SoundstageConfig.maxPlayingReceivers();
        if (cap <= 0 || scheduler.isActivelyPlaying() || playingReceiverCount() < cap) return true;
        player.sendSystemMessage(Component.translatable("chat.soundstage.too_many_playing", cap)
                .withStyle(net.minecraft.ChatFormatting.RED));
        return false;
    }

    public static final int MAX_PENDING_LOADS = 8;
    private final com.nachtraben.soundstage.core.InFlightLimit<IdentityScopedKey<ServerLevel, BlockPos>> pendingLoads =
            new com.nachtraben.soundstage.core.InFlightLimit<>(MAX_PENDING_LOADS);

    private final Map<IdentityScopedKey<ServerLevel, BlockPos>, Long> lastActiveMillis = new ConcurrentHashMap<>();

    private void cleanupIdlePlayers() {
        try {
            sweepIdlePlayers();
        } catch (Throwable t) {
            LOGGER.error("Idle sweep failed; it will run again at its next interval", t);
        }
    }

    private void sweepIdlePlayers() {
        long now = System.currentTimeMillis();
        for (IdentityScopedKey<ServerLevel, BlockPos> key : new java.util.ArrayList<>(activePlayers.keySet())) {
            AudioPlayer player = activePlayers.get(key);
            if (player == null) continue;

            long since = lastActiveMillis.computeIfAbsent(key, ignored -> now);
            IdleReclaim.Decision decision = IdleReclaim.decide(
                    player.getPlayingTrack() != null, player.isPaused(), since, now);

            if (decision == IdleReclaim.Decision.ACTIVE) {
                lastActiveMillis.put(key, now);
                continue;
            }
            if (decision == IdleReclaim.Decision.WAITING) continue;

            ServerLevel level = key.scope();
            if (level.getServer() != null) {
                level.getServer().execute(() -> reclaimIfStillIdle(key, player));
            } else {
                reclaimIfStillIdle(key, player);
            }
        }
    }

    private void reclaimIfStillIdle(IdentityScopedKey<ServerLevel, BlockPos> key, AudioPlayer expectedPlayer) {
        if (activePlayers.get(key) != expectedPlayer) {
            return;
        }
        long now = System.currentTimeMillis();
        long since = lastActiveMillis.computeIfAbsent(key, ignored -> now);
        IdleReclaim.Decision current = IdleReclaim.decide(
                expectedPlayer.getPlayingTrack() != null, expectedPlayer.isPaused(), since, now);
        if (current == IdleReclaim.Decision.ACTIVE) {
            lastActiveMillis.put(key, now);
            return;
        }
        if (current == IdleReclaim.Decision.RECLAIM) {
            removePlayer(key);
        }
    }

    public YoutubeAudioSourceManager getYoutubeSource() {
        return ytSource;
    }
}
