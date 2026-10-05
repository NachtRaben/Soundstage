package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.network.NetworkHandler;
import com.nachtraben.soundstage.network.SpeakerMetadataS2CPacket;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import com.nachtraben.soundstage.core.TrackQueue;

public class TrackScheduler extends AudioEventAdapter {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    public static final class QueueEntry {
        private final TrackQueue.Entry delegate;
        QueueEntry(TrackQueue.Entry delegate) { this.delegate = delegate; }
        public long id() { return delegate.id(); }
        public AudioTrack track() { return delegate.track(); }
        public String encoded() { return delegate.encoded(); }
    }

    @lombok.Getter
    private final AudioPlayer player;

    private final TrackQueue queue = new TrackQueue(t -> AudioManager.get().encodeTrack(t));

    private final BlockPos pos;
    private final ServerLevel level;
    private final com.nachtraben.soundstage.block.IAudioReceiver receiver;

    @org.jetbrains.annotations.Nullable
    private final com.nachtraben.soundstage.audio.video.VideoRelay videoRelay;

    public TrackScheduler(AudioPlayer player, BlockPos pos, ServerLevel level,
                          com.nachtraben.soundstage.block.IAudioReceiver receiver,
                          @org.jetbrains.annotations.Nullable com.nachtraben.soundstage.audio.video.VideoRelay videoRelay) {
        this.player = player;
        this.pos = pos;
        this.level = level;
        this.receiver = receiver;
        this.videoRelay = videoRelay;
    }

    @org.jetbrains.annotations.Nullable
    public com.nachtraben.soundstage.audio.video.VideoRelay videoRelay() {
        return videoRelay;
    }

    boolean ownsReceiver(com.nachtraben.soundstage.block.IAudioReceiver candidate) {
        return receiver != null && receiver == candidate;
    }

    private int loopMode = 0;

    private volatile boolean currentTrackFailed = false;
    private boolean shuffle = false;
    private long pendingSeek = -1;

    private volatile String lastAnnouncedIdentifier;

    private final com.nachtraben.soundstage.core.FailureBreakers breakers =
            new com.nachtraben.soundstage.core.FailureBreakers();

    private final java.util.concurrent.atomic.AtomicInteger failedInARow =
            new java.util.concurrent.atomic.AtomicInteger();

    private volatile String lastFailureDetail = "";
    private volatile String lastFailureSource = com.nachtraben.soundstage.core.SourceKey.UNKNOWN;

    private static String sourceOf(AudioTrack track) {
        return track == null
                ? com.nachtraben.soundstage.core.SourceKey.UNKNOWN
                : com.nachtraben.soundstage.core.SourceKey.forUrl(track.getInfo().uri);
    }

    private static final long SILENT_FAILURE_MILLIS = 3000;

    private volatile AudioTrack windowReachedBy;

    public int getLoopMode() { return loopMode; }
    public void setLoopMode(int mode) { this.loopMode = mode; }
    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean shuffle) { this.shuffle = shuffle; }
    public void setPendingSeek(long position) {
        this.pendingSeek = position;
        if (position > 0) {
            seekTracker.requested(position);
        }
    }

    public void queue(AudioTrack track, boolean unpause) {
        if (!isActiveSession()) {
            return;
        }
        if (unpause && player.getPlayingTrack() == null) {
            player.setPaused(false);
        }
        if (!player.startTrack(track, true)) {
            queue.add(track);
        }
        syncMetadata();
    }

    public void queue(AudioTrack track) {
        queue(track, true);
    }

    private QueueEntry pollNext() {
        TrackQueue.Entry next = queue.pollNext(shuffle);
        return next == null ? null : new QueueEntry(next);
    }

    public void nextTrack() {
        QueueEntry next = pollNext();
        if (next != null) {
            player.setPaused(false);
        }
        player.startTrack(next == null ? null : next.track(), false);
        syncMetadata();
    }

    public void skipTrack() {
        AudioTrack current = player.getPlayingTrack();
        AudioTrack held = takeHeldStream();
        if (current == null) current = held;
        if (loopMode == 1 && current != null && queue.isEmpty() && !(current instanceof LiveInputTrack)
                && !com.nachtraben.soundstage.RigStaging.fault("skip-no-redial")) {
            player.startTrack(current.makeClone(), false);
            player.setPaused(false);
            syncMetadata();
            return;
        }
        if (!com.nachtraben.soundstage.RigStaging.fault("skip-not-requeued")) {
            requeueCurrent(current,
                    com.nachtraben.soundstage.core.QueueLoopPolicy.Transition.SKIPPED);
        }
        nextTrack();
    }

    private void requeueCurrent(AudioTrack track,
                                com.nachtraben.soundstage.core.QueueLoopPolicy.Transition transition) {
        if (track != null && (!(track instanceof LiveInputTrack) || com.nachtraben.soundstage.RigStaging.fault("share-requeued"))
                && com.nachtraben.soundstage.core.QueueLoopPolicy.shouldRequeue(loopMode == 2, transition)) {
            queue.add(track.makeClone());
        }
    }

    public void startLiveShare(LiveInputTrack share) {
        AudioTrack current = player.getPlayingTrack();
        AudioTrack held = takeHeldStream();
        if (current == null) current = held;
        if (current != null && !(current instanceof LiveInputTrack) && !com.nachtraben.soundstage.RigStaging.fault("interrupted-dropped")) {
            AudioTrack resume = current.makeClone();
            if (current.isSeekable()) resume.setPosition(current.getPosition());
            queue.move(queue.add(resume).id(), 0);
        }
        clearSuspension();
        player.setPaused(false);
        player.startTrack(share, false);
        syncMetadata();
        persistToReceiver();
    }

    public void replayCurrent() {
        AudioTrack current = player.getPlayingTrack();
        if (current == null || current instanceof LiveInputTrack) {
            syncMetadata();
            return;
        }
        AudioTrack again = current.makeClone();
        if (current.isSeekable()) again.setPosition(current.getPosition());
        player.startTrack(again, false);
        syncMetadata();
    }

    public void clearQueue() {
        queue.clear();
        syncMetadata();
        persistToReceiver();
    }

    public boolean moveEntry(long entryId, int targetIndex) {
        if (!queue.move(entryId, targetIndex)) return false;
        syncMetadata();
        persistToReceiver();
        return true;
    }

    public boolean removeEntry(long entryId) {
        if (!queue.remove(entryId)) return false;
        syncMetadata();
        persistToReceiver();
        return true;
    }

    public boolean playNow(long entryId) {
        TrackQueue.Entry target = queue.take(entryId);
        if (target == null) return false;
        requeueCurrent(player.getPlayingTrack(),
                com.nachtraben.soundstage.core.QueueLoopPolicy.Transition.PLAY_NOW);
        clearSuspension();
        player.setPaused(false);
        player.startTrack(target.track(), false);
        syncMetadata();
        persistToReceiver();
        return true;
    }

    private final com.nachtraben.soundstage.core.SeekTracker seekTracker =
            new com.nachtraben.soundstage.core.SeekTracker();

    public void seek(long position) {
        AudioTrack track = player.getPlayingTrack();
        if (track != null && track.isSeekable()) {
            if (videoRelay != null) videoRelay.seeking(track);
            track.setPosition(position);
            seekTracker.requested(position);
            syncMetadata();
        }
    }

    public void retakeForSurround() {
        AudioTrack track = player.getPlayingTrack();
        if (track == null || !track.isSeekable()) return;
        LOGGER.info("Soundstage: receiver {} takes up its film's surround track for speakers linked part-way",
                pos.toShortString());
        seek(track.getPosition());
    }

    private long lastReportedPosition = 0;

    private long reportedPosition(AudioTrack track) {
        long actual = track.getPosition();
        if (actual == 0 && lastReportedPosition > 0 && track.getDuration() > 0) {
            return lastReportedPosition;
        }
        lastReportedPosition = seekTracker.reportedPosition(actual);
        return lastReportedPosition;
    }

    public void stop() {
        queue.clear();
        player.stopTrack();
        player.setPaused(false);
        reclaimed = false;
        reclaimedTrack = null;
        heldStream = null;
        if (videoRelay != null) videoRelay.endPicture();
        syncMetadata();
    }

    public void resume(boolean mayStartQueue) {
        if (com.nachtraben.soundstage.RigStaging.fault("never-resumed")) return;
        AudioTrack held = takeHeldStream();
        AudioTrack current = player.getPlayingTrack();
        player.setPaused(false);
        if (held != null && !com.nachtraben.soundstage.RigStaging.fault("resume-drops-held-stream")) {
            heldStreamsDialled.incrementAndGet();
            player.startTrack(held, false);
        } else if (held != null) {
        } else if (current != null && isLive(current)) {
            player.startTrack(current.makeClone(), false);
        } else if (current == null && mayStartQueue && !queue.isEmpty()) {
            nextTrack();
        }
        syncMetadata();
    }

    private static boolean isLive(AudioTrack track) {
        return !com.nachtraben.soundstage.core.TrackDuration.isKnown(track.getDuration()) || !track.isSeekable();
    }

    public List<QueueEntry> getQueue() {
        List<QueueEntry> out = new ArrayList<>();
        for (TrackQueue.Entry e : queue.snapshot()) out.add(new QueueEntry(e));
        return out;
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (!isActiveSession()) {
            return;
        }
        LOGGER.info("Receiver at {} track ended: reason={} mayStartNext={} loop={} failed={} queue={} position={} paused={}",
                pos, endReason, endReason.mayStartNext, loopMode, currentTrackFailed, queue.size(),
                track == null ? -1 : track.getPosition(), player.isPaused());

        if (videoRelay != null) videoRelay.detach(track, endReason == AudioTrackEndReason.CLEANUP);

        if (track instanceof LiveInputTrack share && !com.nachtraben.soundstage.RigStaging.fault("share-judged-as-track")) {
            onShareEnded(share, endReason);
            return;
        }

        if (endReason == AudioTrackEndReason.CLEANUP) {
            reclaimed = true;
            persistSnapshot(track);
            return;
        }

        if (endReason.mayStartNext) {
            long playedTime = track == null ? 0 : Math.max(0, track.getPosition());
            if (track != null && track == windowReachedBy) playedTime = Math.max(playedTime, SILENT_FAILURE_MILLIS);
            boolean live = track != null
                    && !com.nachtraben.soundstage.core.TrackDuration.isKnown(track.getDuration());
            boolean endedEarly = endReason == AudioTrackEndReason.FINISHED
                    && track != null
                    && playedTime < SILENT_FAILURE_MILLIS
                    && (live || track.getDuration() > SILENT_FAILURE_MILLIS);

            boolean failedBeforeAudio = endedEarly || endReason == AudioTrackEndReason.LOAD_FAILED;
            if (failedBeforeAudio && !currentTrackFailed) {
                recordFailure(sourceOf(track), endedEarly
                        ? "Tracks are ending immediately without playing."
                        : "A track failed before it played.");
            } else if (endReason == AudioTrackEndReason.FINISHED && !currentTrackFailed) {
                breakers.recordSuccess(sourceOf(track));
            }

            boolean failed = currentTrackFailed || failedBeforeAudio;
            if (playedTime >= SILENT_FAILURE_MILLIS) {
                failedInARow.set(0);
            } else if (failed && failedInARow.incrementAndGet()
                    >= com.nachtraben.soundstage.core.FailureBreaker.TRIP_THRESHOLD
                    && !com.nachtraben.soundstage.RigStaging.fault("no-stop")) {
                currentTrackFailed = false;
                stopAfterFailedRun();
                persistToReceiver();
                return;
            }

            if (track != null && com.nachtraben.soundstage.core.StreamRedial.shouldRedial(
                    live, playedTime, SILENT_FAILURE_MILLIS)) {
                currentTrackFailed = false;
                if (player.isPaused()) {
                    heldStream = track.makeClone();
                    persistSnapshot(track);
                    LOGGER.info("Receiver at {} holding live stream for resume after {} ms (reason={})",
                            pos, playedTime, endReason);
                } else {
                    LOGGER.info("Receiver at {} reconnecting live stream after {} ms of playback (reason={})",
                            pos, playedTime, endReason);
                    player.startTrack(track.makeClone(), false);
                    player.setPaused(false);
                }
                syncMetadata();
                persistToReceiver();
                return;
            }

            currentTrackFailed = false;

            if (loopMode == 1 && !failed) {
                player.startTrack(track.makeClone(), false);
            } else if (loopMode == 1) {
                if (level != null) {
                    AudioManager.get().broadcastMessage(pos, level, this, net.minecraft.network.chat.Component
                            .literal("Skipping a track that will not play; repeat is still on.")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
                }
                nextTrack();
            } else {
                if (failed && level != null) {
                    AudioManager.get().broadcastMessage(pos, level, this, net.minecraft.network.chat.Component
                            .literal("Skipped a track that failed to play.")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
                }
                if (failed && com.nachtraben.soundstage.RigStaging.fault("restore-skip-blocked")) {
                    persistToReceiver();
                    return;
                }
                requeueCurrent(track, failed
                        ? com.nachtraben.soundstage.core.QueueLoopPolicy.Transition.FAILED
                        : com.nachtraben.soundstage.core.QueueLoopPolicy.Transition.FINISHED);
                if (!(failed && com.nachtraben.soundstage.RigStaging.fault("no-skip"))) {
                    nextTrack();
                }
            }
        } else {
            syncMetadata();
        }
        persistToReceiver();
    }

    private void onShareEnded(LiveInputTrack share, AudioTrackEndReason endReason) {
        currentTrackFailed = false;
        if (endReason == AudioTrackEndReason.CLEANUP) {
            share.session().end(LiveInputSession.End.PAUSED);
        } else if (com.nachtraben.soundstage.RigStaging.fault("share-clone-ends") || !(player.getPlayingTrack() instanceof LiveInputTrack now
                && now.session() == share.session())) {
            share.session().end(LiveInputSession.End.RECEIVER_MOVED_ON);
        }
        if (endReason.mayStartNext) {
            nextTrack();
        } else {
            syncMetadata();
        }
        persistToReceiver();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track,
                                 com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
        if (!isActiveSession()) {
            return;
        }
        String detail = exception.getMessage() == null ? "unknown error" : exception.getMessage();
        if (track != player.getPlayingTrack()) {
            lastFailureSource = sourceOf(track);
            lastFailureDetail = "Playback failed: " + detail;
            return;
        }
        if (track instanceof LiveInputTrack) return;
        currentTrackFailed = true;
        recordFailure(sourceOf(track), "Playback failed: " + detail);
        syncMetadata();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        if (!isActiveSession() || track instanceof LiveInputTrack) {
            return;
        }
        currentTrackFailed = true;
        recordFailure(sourceOf(track), "A track stopped responding and was skipped.");
        syncMetadata();
    }

    public void onLoadFailed(String url, com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
        if (com.nachtraben.soundstage.RigStaging.fault("breaker-never-trips")) {
            return;
        }
        String detail = exception.getMessage() == null ? "unknown error" : exception.getMessage();
        String source = sourceKeyFor(url);
        if (exception.severity == com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT) {
            trip(source, "Loading failed: " + detail);
        } else {
            recordFailure(source, "Loading failed: " + detail);
        }
    }

    private static String sourceKeyFor(String url) {
        if (com.nachtraben.soundstage.RigStaging.fault("breaker-not-scoped")) {
            return "rig-shared-service";
        }
        return com.nachtraben.soundstage.core.SourceKey.forUrl(url);
    }

    public void onLoadSucceeded(String url) {
        breakers.recordSuccess(com.nachtraben.soundstage.core.SourceKey.forUrl(url));
    }

    private void recordFailure(String source, String detail) {
        lastFailureSource = source;
        lastFailureDetail = detail;
        if (breakers.recordFailure(source, detail, false, System.currentTimeMillis())) {
            onTripped(source, detail);
        }
    }

    private void trip(String source, String detail) {
        lastFailureSource = source;
        lastFailureDetail = detail;
        breakers.recordFailure(source, detail, true, System.currentTimeMillis());
        onTripped(source, detail);
    }

    private void onTripped(String source, String detail) {
        LOGGER.warn("Receiver at {} suspended automatic {} loads after failures: {}", pos, source, detail);
    }

    private void stopAfterFailedRun() {
        failedInARow.set(0);
        player.setPaused(true);
        String detail = lastFailureDetail;
        LOGGER.warn("Receiver at {} stopped after {} tracks in a row failed to play; the last: {}", pos,
                com.nachtraben.soundstage.core.FailureBreaker.TRIP_THRESHOLD, detail);
        if (level != null) {
            AudioManager.get().broadcastMessage(pos, level, this, net.minecraft.network.chat.Component
                    .literal("Playback stopped: " + com.nachtraben.soundstage.core.FailureBreaker.TRIP_THRESHOLD
                            + " tracks in a row would not play. "
                            + com.nachtraben.soundstage.core.LoadFailureMessage.forPlayer(lastFailureSource, detail)
                            + " Queue kept; press Play to try again.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        syncMetadata();
    }

    public boolean isLoadingSuspended(String source) {
        return breakers.isSuspended(source, System.currentTimeMillis());
    }

    public boolean isLoadingSuspendedForUrl(String url) {
        return isLoadingSuspended(sourceKeyFor(url));
    }

    public String loadingSuspendedReason(String url) {
        String source = sourceKeyFor(url);
        return com.nachtraben.soundstage.core.LoadFailureMessage.suspended(source,
                breakers.reason(source), breakers.remainingSeconds(source, System.currentTimeMillis()));
    }

    public void clearSuspension() {
        breakers.clearAll();
        failedInARow.set(0);
    }

    public int queueSize() {
        return queue.size();
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        if (!isActiveSession()) {
            return;
        }
        if (videoRelay != null) videoRelay.attach(track, artworkFor(track.getInfo()));
        track.addMarker(new com.sedmelluq.discord.lavaplayer.track.TrackMarker(SILENT_FAILURE_MILLIS, state -> {
            if (state == com.sedmelluq.discord.lavaplayer.track.TrackMarkerHandler.MarkerState.REACHED
                    || state == com.sedmelluq.discord.lavaplayer.track.TrackMarkerHandler.MarkerState.BYPASSED
                    || state == com.sedmelluq.discord.lavaplayer.track.TrackMarkerHandler.MarkerState.LATE) {
                windowReachedBy = track;
            }
        }));
        reclaimed = false;
        reclaimedTrack = null;
        heldStream = null;
        currentTrackFailed = false;
        LOGGER.info("Receiver at {} track started: {} (seekable={}, pendingSeek={})",
                pos, track.getInfo().title, track.isSeekable(), pendingSeek);
        seekTracker.clear();
        lastReportedPosition = 0;
        if (pendingSeek >= 0) {
            if (track.isSeekable()) {
                track.setPosition(pendingSeek);
                seekTracker.requested(pendingSeek);
            }
            pendingSeek = -1;
        }
        syncMetadata();
        persistToReceiver();
        announceIfChanged(track);
    }

    private void announceIfChanged(AudioTrack track) {
        if (track == null || player.isPaused()) return;
        String identifier = track.getIdentifier();
        if (identifier != null && identifier.equals(lastAnnouncedIdentifier)) return;
        lastAnnouncedIdentifier = identifier;
        broadcastNowPlaying(track);
    }

    private void broadcastNowPlaying(AudioTrack track) {
        if (level == null || track == null) return;
        com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo info = track.getInfo();
        com.nachtraben.soundstage.network.NowPlayingS2CPacket packet =
                new com.nachtraben.soundstage.network.NowPlayingS2CPacket(pos,
                        info.title == null ? "" : info.title,
                        info.author == null ? "" : info.author,
                        artworkFor(info));
        AudioManager.get().broadcastToNearby(pos, level, this,
                player -> dev.armadeus.armalib.platform.network.Network.sendToPlayer(player, packet));
    }

    private static String artworkFor(com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo info) {
        String derived = com.nachtraben.soundstage.core.YoutubeThumbnail.forTrack(
                info.identifier, info.uri);
        if (!derived.isEmpty()) return derived;
        return info.artworkUrl == null ? "" : info.artworkUrl;
    }

    private volatile boolean tearingDown = false;

    private volatile AudioTrack heldStream;

    public boolean hasHeldStream() {
        return heldStream != null;
    }

    public int heldStreamsDialled() {
        return heldStreamsDialled.get();
    }

    private final java.util.concurrent.atomic.AtomicInteger heldStreamsDialled =
            new java.util.concurrent.atomic.AtomicInteger();

    public AudioTrack takeHeldStream() {
        AudioTrack held = heldStream;
        heldStream = null;
        return held;
    }

    public void beginTeardown() {
        tearingDown = true;
    }

    private boolean isActiveSession() {
        return !tearingDown && level != null && AudioManager.get().isRegisteredSession(level, pos, this);
    }

    private void persistSnapshot(AudioTrack track) {
        if (track == null || level == null) {
            LOGGER.warn("Receiver at {} was reclaimed but could not be snapshotted (track={}, level={}).",
                    pos, track != null, level != null);
            return;
        }
        String encoded = AudioManager.get().encodeTrack(track);
        long trackPosition = track.getPosition();

        reclaimedTrack = new ReclaimedTrack(
                track.getInfo().title,
                track.getInfo().author,
                artworkFor(track.getInfo()),
                trackPosition,
                com.nachtraben.soundstage.core.TrackDuration.reported(track.getDuration()));

        level.getServer().execute(() -> {
            if (!isActiveSession() || !AudioManager.get().isCurrentSession(level, pos, this)) {
                return;
            }
            if (com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof com.nachtraben.soundstage.block.IAudioReceiver cbe) {
                cbe.setCurrentTrackData(encoded);
                cbe.setCurrentTrackPosition(trackPosition);
                LOGGER.info("Receiver at {} reclaimed while idle; snapshotted at {}ms for revival.",
                        pos, trackPosition);
            } else {
                LOGGER.warn("Receiver at {} was reclaimed but its block entity is gone; nothing saved.", pos);
            }
        });
    }

    private volatile boolean reclaimed = false;

    public boolean isReclaimed() {
        return reclaimed;
    }

    private record ReclaimedTrack(String title, String artist, String thumbnail,
                                  long position, long duration) {}

    private volatile ReclaimedTrack reclaimedTrack = null;

    private void onServerThread(Runnable task) {
        level.getServer().execute(task);
    }

    public void persistToReceiver() {
        onServerThread(this::persistToReceiverNow);
    }

    private void persistToReceiverNow() {
        if (!isActiveSession() || !AudioManager.get().isCurrentSession(level, pos, this)) {
            return;
        }
        if (com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof com.nachtraben.soundstage.block.IAudioReceiver cbe) {
            com.sedmelluq.discord.lavaplayer.track.AudioTrack current = player.getPlayingTrack();
            if (current instanceof LiveInputTrack && !com.nachtraben.soundstage.RigStaging.fault("share-saved")) {
                cbe.setCurrentTrackData("");
                cbe.setCurrentTrackPosition(0);
            } else if (current != null) {
                cbe.setCurrentTrackData(AudioManager.get().encodeTrack(current));
                cbe.setCurrentTrackPosition(current.getPosition());
            } else if (!reclaimed) {
                cbe.setCurrentTrackData("");
                cbe.setCurrentTrackPosition(0);
            }
            java.util.List<String> dataList = new java.util.ArrayList<>();
            for (QueueEntry entry : getQueue()) {
                if (!entry.encoded().isEmpty()) dataList.add(entry.encoded());
            }
            cbe.setQueueData(dataList);
            cbe.setLoopMode(loopMode);
            cbe.setShuffle(shuffle);
            cbe.setPlaying(isActivelyPlaying());
        }
    }

    public boolean isActivelyPlaying() {
        return player.getPlayingTrack() != null && !player.isPaused();
    }

    public void syncMetadata() {
        onServerThread(this::syncMetadataNow);
    }

    private void syncMetadataNow() {
        if (!isActiveSession() || !AudioManager.get().isCurrentSession(level, pos, this)) {
            return;
        }
        AudioTrack track = player.getPlayingTrack();
        String title = "";
        String artist = "";
        String thumbnail = "";
        boolean isPlaying = isActivelyPlaying();
        
        List<String> queueTitles = new ArrayList<>();
        List<Long> queueIds = new ArrayList<>();
        for (QueueEntry entry : getQueue()) {
            queueTitles.add(entry.track().getInfo().title);
            queueIds.add(entry.id());
        }

        long position = 0;
        long duration = 0;

        if (track != null) {
            title = track.getInfo().title;
            artist = track.getInfo().author;
            position = reportedPosition(track);
            duration = com.nachtraben.soundstage.core.TrackDuration.reported(track.getDuration());
            
            thumbnail = artworkFor(track.getInfo());
        } else {
            ReclaimedTrack held = reclaimedTrack;
            if (held != null) {
                title = held.title();
                artist = held.artist();
                thumbnail = held.thumbnail();
                position = held.position();
                duration = held.duration();
            }
        }

        boolean isLocked;
        boolean pictureEnabled;
        boolean levelling;
        int volume;
        if (com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof com.nachtraben.soundstage.block.IAudioReceiver cbe) {
            isLocked = cbe.isLocked();
            pictureEnabled = cbe.isPictureEnabled();
            levelling = cbe.isLevelling();
            volume = cbe.getVolume();
        } else {
            return;
        }

        boolean mediaFolderEnabled = com.nachtraben.soundstage.config.SoundstageConfig.enableMediaFolder();
        SpeakerMetadataS2CPacket packet = new SpeakerMetadataS2CPacket(pos, title, artist, thumbnail, isPlaying, volume, queueTitles, queueIds, loopMode, shuffle, isLocked, position, duration, mediaFolderEnabled, pictureEnabled, levelling);
        NetworkHandler.sendMetadataToTracking(level, pos, packet);
    }
}
