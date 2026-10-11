package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.audio.video.SoundChoices;
import com.nachtraben.soundstage.audio.video.VideoCapableTrack;
import com.nachtraben.soundstage.audio.video.VideoRelay;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.FailureBreaker;
import com.nachtraben.soundstage.core.FailureBreakers;
import com.nachtraben.soundstage.core.LoadFailureMessage;
import com.nachtraben.soundstage.core.QueueLoopPolicy;
import com.nachtraben.soundstage.core.SeekTracker;
import com.nachtraben.soundstage.core.SourceKey;
import com.nachtraben.soundstage.core.StreamRedial;
import com.nachtraben.soundstage.core.TrackDuration;
import com.nachtraben.soundstage.core.TrackQueue;
import com.nachtraben.soundstage.core.YoutubeThumbnail;
import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.network.NetworkHandler;
import com.nachtraben.soundstage.network.NowPlayingS2CPacket;
import com.nachtraben.soundstage.network.SpeakerMetadataS2CPacket;
import com.nachtraben.soundstage.util.WorldAccess;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import com.sedmelluq.discord.lavaplayer.track.TrackMarkerHandler;
import dev.armadeus.armalib.platform.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

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
    private final IAudioReceiver receiver;

    @Nullable
    private final VideoRelay videoRelay;

    private final SoundChoices soundChoices =
            new SoundChoices() {
                @Override
                public String language() {
                    return receiver == null ? null : receiver.audioLanguage();
                }

                @Override
                public String trackChoice(String identifier) {
                    return TrackScheduler.this.trackChoice(identifier);
                }

                @Override
                public void offered() {
                    syncMetadata();
                }
            };

    private String trackChoice(String identifier) {
        if (receiver == null || identifier == null) return null;
        String choice = receiver.audioChoices().get(identifier);
        return SubtitleChoice.AUTO.equals(choice) ? null : choice;
    }

    private AudioChoice.Offer audioOffer(AudioTrack track) {
        if (receiver == null || track == null) return null;
        List<AudioChoice.Entry> entries =
                track instanceof VideoCapableTrack video ? video.audioEntries() : null;
        String language = receiver.audioLanguage();
        if ((entries == null || entries.isEmpty()) && language == null) return null;
        return AudioChoice.offer(entries, language,
                trackChoice(track.getIdentifier()));
    }

    public TrackScheduler(AudioPlayer player, BlockPos pos, ServerLevel level,
                          IAudioReceiver receiver,
                          @Nullable VideoRelay videoRelay) {
        this.player = player;
        this.pos = pos;
        this.level = level;
        this.receiver = receiver;
        this.videoRelay = videoRelay;
    }

    @Nullable
    public VideoRelay videoRelay() {
        return videoRelay;
    }

    boolean ownsReceiver(IAudioReceiver candidate) {
        return receiver != null && receiver == candidate;
    }

    private int loopMode = 0;

    private volatile boolean currentTrackFailed = false;
    private boolean shuffle = false;
    private long pendingSeek = -1;

    private volatile String lastAnnouncedIdentifier;
    private volatile boolean lastAnnouncedStill;

    private final FailureBreakers breakers =
            new FailureBreakers();

    private final AtomicInteger failedInARow =
            new AtomicInteger();

    private volatile String lastFailureDetail = "";
    private volatile String lastFailureSource = SourceKey.UNKNOWN;

    private static String sourceOf(AudioTrack track) {
        return track == null
                ? SourceKey.UNKNOWN
                : SourceKey.forUrl(track.getInfo().uri);
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
        if (loopMode == 1 && current != null && queue.isEmpty() && !(current instanceof LiveInputTrack)) {
            player.startTrack(current.makeClone(), false);
            player.setPaused(false);
            syncMetadata();
            return;
        }
        requeueCurrent(current,
                QueueLoopPolicy.Transition.SKIPPED);
        nextTrack();
    }

    private void requeueCurrent(AudioTrack track,
                                QueueLoopPolicy.Transition transition) {
        if (track != null && !(track instanceof LiveInputTrack)
                && QueueLoopPolicy.shouldRequeue(loopMode == 2, transition)) {
            queue.add(track.makeClone());
        }
    }

    public void startLiveShare(LiveInputTrack share) {
        AudioTrack current = player.getPlayingTrack();
        AudioTrack held = takeHeldStream();
        if (current == null) current = held;
        if (current != null && !(current instanceof LiveInputTrack)) {
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

    private void replayKeepingPicturePlace() {
        if (videoRelay != null) videoRelay.carryPlace(player.getPlayingTrack());
        try {
            replayCurrent();
        } finally {
            if (videoRelay != null) videoRelay.carryPlace(null);
        }
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
                QueueLoopPolicy.Transition.PLAY_NOW);
        clearSuspension();
        player.setPaused(false);
        player.startTrack(target.track(), false);
        syncMetadata();
        persistToReceiver();
        return true;
    }

    private final SeekTracker seekTracker =
            new SeekTracker();

    public void seek(long position) {
        AudioTrack track = player.getPlayingTrack();
        if (track != null && track.isSeekable()) {
            if (videoRelay != null) videoRelay.seeking(track);
            track.setPosition(position);
            seekTracker.requested(position);
            syncMetadata();
        }
    }

    public void advanceStill(AudioTrack track) {
        if (player.getPlayingTrack() != track || track == null || queue.isEmpty() || loopMode == 1
                || player.isPaused()) {
            return;
        }
        LOGGER.debug("Soundstage: receiver {} moves on from an image after {} s, to the next in its queue",
                pos.toShortString(), SoundstageConfig.imageDisplaySeconds());
        requeueCurrent(track, QueueLoopPolicy.Transition.FINISHED);
        movingOnFromImage = true;
        try {
            nextTrack();
        } finally {
            movingOnFromImage = false;
        }
        persistToReceiver();
    }

    private boolean movingOnFromImage;

    private boolean imageChange(AudioTrack track) {
        return movingOnFromImage && track instanceof VideoCapableTrack video && video.isStill();
    }

    public boolean chooseAudio(String choice) {
        AudioTrack track = player.getPlayingTrack();
        AudioChoice.Offer offer = audioOffer(track);
        if (!AudioChoice.accepts(offer, choice)) return false;
        String memoryKey = track.getIdentifier();
        if (choice.equals(AudioChoice.DEFAULT)) {
            receiver.setAudioLanguage(null);
            receiver.audioChoices().put(memoryKey, SubtitleChoice.AUTO);
        } else if (choice.startsWith(AudioChoice.LANGUAGE)) {
            receiver.setAudioLanguage(choice.substring(AudioChoice.LANGUAGE.length()));
            receiver.audioChoices().put(memoryKey, SubtitleChoice.AUTO);
        } else {
            receiver.audioChoices().put(memoryKey, choice);
        }
        VideoCapableTrack.PictureRetake retake =
                track instanceof VideoCapableTrack video
                        ? video.applyAudio() : VideoCapableTrack.PictureRetake.NONE;
        if (retake == VideoCapableTrack.PictureRetake.REPLAY) {
            LOGGER.info("Soundstage: receiver {} plays its track again from where it is, in the audio chosen",
                    pos.toShortString());
            replayKeepingPicturePlace();
            return true;
        }
        if (retake == VideoCapableTrack.PictureRetake.SEEK && track.isSeekable()) {
            LOGGER.info("Soundstage: receiver {} starts its copy again in the audio chosen", pos.toShortString());
            seek(track.getPosition());
            return true;
        }
        syncMetadata();
        return true;
    }

    public void retake(boolean surround, VideoCapableTrack.PictureRetake picture) {
        AudioTrack track = player.getPlayingTrack();
        if (track == null) return;
        if (picture == VideoCapableTrack.PictureRetake.REPLAY) {
            LOGGER.info("Soundstage: receiver {} plays its track again from where it is, for its picture",
                    pos.toShortString());
            replayKeepingPicturePlace();
            return;
        }
        if (!track.isSeekable()) return;
        boolean display = picture == VideoCapableTrack.PictureRetake.SEEK;
        LOGGER.info("Soundstage: receiver {} starts its copy again for {}", pos.toShortString(),
                display && surround ? "its picture and speakers linked part-way" : display ? "its picture"
                        : "speakers linked part-way");
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
        AudioTrack held = takeHeldStream();
        AudioTrack current = player.getPlayingTrack();
        player.setPaused(false);
        if (held != null) {
            player.startTrack(held, false);
        } else if (current != null && isLive(current)) {
            player.startTrack(current.makeClone(), false);
        } else if (current == null && mayStartQueue && !queue.isEmpty()) {
            nextTrack();
        }
        syncMetadata();
    }

    private static boolean isLive(AudioTrack track) {
        return !TrackDuration.isKnown(track.getDuration()) || !track.isSeekable();
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
        if (imageChange(track)) {
            LOGGER.debug("Receiver at {} moved on from an image: reason={} queue={}", pos, endReason, queue.size());
        } else {
            LOGGER.info("Receiver at {} track ended: reason={} mayStartNext={} loop={} failed={} queue={} position={} "
                            + "paused={}", pos, endReason, endReason.mayStartNext, loopMode, currentTrackFailed,
                    queue.size(), track == null ? -1 : track.getPosition(), player.isPaused());
        }

        if (videoRelay != null) videoRelay.detach(track, endReason == AudioTrackEndReason.CLEANUP);

        if (track instanceof LiveInputTrack share) {
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
                    && !TrackDuration.isKnown(track.getDuration());
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
                    >= FailureBreaker.TRIP_THRESHOLD) {
                currentTrackFailed = false;
                stopAfterFailedRun();
                persistToReceiver();
                return;
            }

            if (track != null && StreamRedial.shouldRedial(
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
                    AudioManager.get().broadcastMessage(pos, level, this, Component
                            .literal("Skipping a track that will not play; repeat is still on.")
                            .withStyle(ChatFormatting.YELLOW));
                }
                nextTrack();
            } else {
                if (failed && level != null) {
                    AudioManager.get().broadcastMessage(pos, level, this, Component
                            .literal("Skipped a track that failed to play.")
                            .withStyle(ChatFormatting.YELLOW));
                }
                requeueCurrent(track, failed
                        ? QueueLoopPolicy.Transition.FAILED
                        : QueueLoopPolicy.Transition.FINISHED);
                nextTrack();
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
        } else if (!(player.getPlayingTrack() instanceof LiveInputTrack now
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
                                 FriendlyException exception) {
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

    public void onLoadFailed(String url, FriendlyException exception) {
        String detail = exception.getMessage() == null ? "unknown error" : exception.getMessage();
        String source = sourceKeyFor(url);
        if (exception.severity == FriendlyException.Severity.FAULT) {
            trip(source, "Loading failed: " + detail);
        } else {
            recordFailure(source, "Loading failed: " + detail);
        }
    }

    private static String sourceKeyFor(String url) {
        return SourceKey.forUrl(url);
    }

    public void onLoadSucceeded(String url) {
        breakers.recordSuccess(SourceKey.forUrl(url));
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
                FailureBreaker.TRIP_THRESHOLD, detail);
        if (level != null) {
            AudioManager.get().broadcastMessage(pos, level, this, Component
                    .literal("Playback stopped: " + FailureBreaker.TRIP_THRESHOLD
                            + " tracks in a row would not play. "
                            + LoadFailureMessage.forPlayer(lastFailureSource, detail)
                            + " Queue kept; press Play to try again.")
                    .withStyle(ChatFormatting.RED));
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
        return LoadFailureMessage.suspended(source,
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
        if (track instanceof VideoCapableTrack video) {
            video.setSoundChoices(soundChoices);
        }
        if (videoRelay != null) videoRelay.attach(track, artworkFor(track.getInfo()));
        track.addMarker(new TrackMarker(SILENT_FAILURE_MILLIS, state -> {
            if (state == TrackMarkerHandler.MarkerState.REACHED
                    || state == TrackMarkerHandler.MarkerState.BYPASSED
                    || state == TrackMarkerHandler.MarkerState.LATE) {
                windowReachedBy = track;
            }
        }));
        reclaimed = false;
        reclaimedTrack = null;
        heldStream = null;
        currentTrackFailed = false;
        if (imageChange(track)) {
            LOGGER.debug("Receiver at {} shows the next image: {}", pos, track.getInfo().title);
        } else {
            LOGGER.info("Receiver at {} track started: {} (seekable={}, pendingSeek={})",
                    pos, track.getInfo().title, track.isSeekable(), pendingSeek);
        }
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
        boolean still = track instanceof VideoCapableTrack video && video.isStill();
        boolean pictureFollowsPicture = still && lastAnnouncedStill;
        lastAnnouncedIdentifier = identifier;
        lastAnnouncedStill = still;
        if (!pictureFollowsPicture) broadcastNowPlaying(track);
    }

    private void broadcastNowPlaying(AudioTrack track) {
        if (level == null || track == null) return;
        AudioTrackInfo info = track.getInfo();
        NowPlayingS2CPacket packet =
                new NowPlayingS2CPacket(pos,
                        info.title == null ? "" : info.title,
                        info.author == null ? "" : info.author,
                        artworkFor(info));
        AudioManager.get().broadcastToNearby(pos, level, this,
                player -> Network.sendToPlayer(player, packet));
    }

    private static String artworkFor(AudioTrackInfo info) {
        String derived = YoutubeThumbnail.forTrack(
                info.identifier, info.uri);
        if (!derived.isEmpty()) return derived;
        return info.artworkUrl == null ? "" : info.artworkUrl;
    }

    private volatile boolean tearingDown = false;

    private volatile AudioTrack heldStream;

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
                TrackDuration.reported(track.getDuration()));

        level.getServer().execute(() -> {
            if (!isActiveSession() || !AudioManager.get().isCurrentSession(level, pos, this)) {
                return;
            }
            if (WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof IAudioReceiver cbe) {
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
        if (WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof IAudioReceiver cbe) {
            AudioTrack current = player.getPlayingTrack();
            if (current instanceof LiveInputTrack) {
                cbe.setCurrentTrackData("");
                cbe.setCurrentTrackPosition(0);
            } else if (current != null) {
                cbe.setCurrentTrackData(AudioManager.get().encodeTrack(current));
                cbe.setCurrentTrackPosition(current.getPosition());
            } else if (!reclaimed) {
                cbe.setCurrentTrackData("");
                cbe.setCurrentTrackPosition(0);
            }
            List<String> dataList = new ArrayList<>();
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
            duration = TrackDuration.reported(track.getDuration());

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
        if (WorldAccess.blockEntityIfLoaded(level, pos)
                instanceof IAudioReceiver cbe) {
            isLocked = cbe.isLocked();
            pictureEnabled = cbe.isPictureEnabled();
            levelling = cbe.isLevelling();
            volume = cbe.getVolume();
        } else {
            return;
        }

        boolean mediaFolderEnabled = SoundstageConfig.enableMediaFolder();
        AudioChoice.Offer audio = audioOffer(track);
        SpeakerMetadataS2CPacket packet = new SpeakerMetadataS2CPacket(pos, title, artist, thumbnail, isPlaying, volume,
                queueTitles, queueIds, loopMode, shuffle, isLocked, position, duration, mediaFolderEnabled,
                pictureEnabled, levelling, audio);
        NetworkHandler.sendMetadataToTracking(level, pos, packet);
    }
}
