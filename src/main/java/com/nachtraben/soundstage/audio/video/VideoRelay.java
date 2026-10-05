package com.nachtraben.soundstage.audio.video;

import com.nachtraben.soundstage.network.SubtitleOptionsS2CPacket;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.DisplayResolution;
import com.nachtraben.soundstage.core.video.SubtitleCue;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import com.nachtraben.soundstage.core.video.VideoRelayState;
import com.nachtraben.soundstage.network.DisplayInfoS2CPacket;
import com.nachtraben.soundstage.network.SubtitlePacket;
import com.nachtraben.soundstage.network.VideoPacket;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.armadeus.armalib.platform.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class VideoRelay {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final long PARK_NANOS = 20_000_000L;

    private static final java.util.concurrent.atomic.AtomicLong STARTS_SENT = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong PICTURE_FAILURES = new java.util.concurrent.atomic.AtomicLong();

    public static long startsSent() {
        return STARTS_SENT.get();
    }

    public static long pictureDeliveryFailures() {
        return PICTURE_FAILURES.get();
    }

    private final ServerLevel level;
    private final BlockPos pos;
    private final List<BlockPos> screens;
    private final VideoRelayState<ServerPlayer> state;
    private final Thread thread;
    private volatile boolean running;
    private final com.nachtraben.soundstage.core.video.SurroundBuffer surround =
            new com.nachtraben.soundstage.core.video.SurroundBuffer();

    private long deliveryFailures;
    private long pollFailures;
    private boolean tooLargeReported;
    private int subtitlesGeneration = -1;
    private int subtitlesCueIndex;
    private int subtitlesCuesVersion = -1;

    private final SubtitleChoice.Memory subtitleMemory;
    private final java.util.function.Supplier<String> playerLanguage;
    private SubtitleOptionsS2CPacket lastSentSubtitleOptions;
    private final Set<ServerPlayer> subtitleOptionsTold = new HashSet<>();

    private volatile TrackSink currentSink;

    private final java.util.function.BooleanSupplier wantsPicture;

    private record DisplaySnapshot(int generation, String title, String author, @Nullable String artworkUrl,
                                    long durationMillis, boolean stream, boolean pictureExpected,
                                    @Nullable TrackSink sink) {
        DisplaySnapshot withoutPicture(int newGeneration) {
            return new DisplaySnapshot(newGeneration, title, author, artworkUrl, durationMillis, stream, false, sink);
        }
    }

    private final java.util.concurrent.atomic.AtomicReference<DisplaySnapshot> currentDisplay =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final AtomicInteger displayGenerationCounter = new AtomicInteger();
    private DisplaySnapshot lastSentDisplay;
    private final Set<ServerPlayer> displayInfoTold = new HashSet<>();

    private VideoRelay(AudioPlayer player, ServerLevel level, BlockPos pos, List<BlockPos> screens,
                        java.util.function.BooleanSupplier wantsPicture, SubtitleChoice.Memory subtitleMemory,
                        java.util.function.Supplier<String> playerLanguage) {
        this.subtitleMemory = subtitleMemory;
        this.playerLanguage = playerLanguage;
        this.level = level;
        this.pos = pos.immutable();
        this.screens = screens;
        this.wantsPicture = wantsPicture;
        this.state = new VideoRelayState<>(VideoPacket.MAX_DATA, player::isPaused);
        this.thread = new Thread(this::relay, "Soundstage-Video-" + this.pos.toShortString());
        this.thread.setDaemon(true);
    }

    public static @Nullable VideoRelay forReceiver(AudioPlayer player, ServerLevel level, BlockPos pos,
                                                   @Nullable IAudioReceiver receiver) {
        return receiver instanceof ReceiverBlockEntity withScreens
                ? new VideoRelay(player, level, pos, withScreens.getScreens(),
                        () -> SoundstageConfig.enableVideo() && withScreens.isPictureEnabled(), withScreens.subtitleChoices(),
                        withScreens::controllerLanguage)
                : null;
    }

    static VideoRelay of(AudioPlayer player, ServerLevel level, BlockPos pos, List<BlockPos> screens) {
        return of(player, level, pos, screens, SoundstageConfig::enableVideo);
    }

    static VideoRelay of(AudioPlayer player, ServerLevel level, BlockPos pos, List<BlockPos> screens,
                         java.util.function.BooleanSupplier wantsPicture) {
        return new VideoRelay(player, level, pos, screens, wantsPicture, new SubtitleChoice.Memory(), () -> null);
    }

    public com.nachtraben.soundstage.core.video.SurroundBuffer surround() {
        return surround;
    }

    public void start() {
        running = true;
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread.getState() == Thread.State.NEW) {
            state.close(new ArrayList<>());
            return;
        }
        LockSupport.unpark(thread);
    }

    public void attach(AudioTrack track, @Nullable String artworkUrl) {
        TrackSink sink = track instanceof VideoCapableTrack ? new TrackSink(track) : null;
        boolean pictureExpected = sink != null && wantsPicture.getAsBoolean();
        AudioTrackInfo info = track.getInfo();
        long duration = com.nachtraben.soundstage.core.TrackDuration.reported(track.getDuration());
        currentDisplay.set(new DisplaySnapshot(displayGenerationCounter.incrementAndGet(),
                info.title, info.author, artworkUrl, duration, info.isStream, pictureExpected, sink));
        if (sink == null) {
            currentSink = null;
            state.endPicture();
        } else {
            VideoCapableTrack video = (VideoCapableTrack) track;
            video.setTargetHeight(targetHeight());
            state.attach(track, track::getPosition);
            currentSink = sink;
            video.setVideoSink(sink);
        }
    }

    private void noPictureComing(TrackSink sink) {
        if (com.nachtraben.soundstage.RigStaging.fault("picture-unannounced")) return;
        DisplaySnapshot display = currentDisplay.get();
        if (display == null || display.sink() != sink || !display.pictureExpected()) return;
        currentDisplay.compareAndSet(display, display.withoutPicture(displayGenerationCounter.incrementAndGet()));
    }

    public void detach(AudioTrack track, boolean reclaimed) {
        if (track instanceof VideoCapableTrack video) video.setVideoSink(null);
        currentSink = null;
        state.detach(track, reclaimed);
        if (!reclaimed) currentDisplay.set(null);
    }

    public void seeking(AudioTrack track) {
        if (track instanceof VideoCapableTrack video) video.setTargetHeight(targetHeight());
        state.seeking(track);
    }

    public boolean chooseSubtitles(String choice) {
        TrackSink sink = currentSink;
        if (sink == null || !sink.offersSubtitles()) return false;
        if (com.nachtraben.soundstage.RigStaging.fault("subtitle-choice-ignored")) return true;
        subtitleMemory.put(sink.identifier(), choice);
        sink.applyChoice(choice);
        return true;
    }

    public void endPicture() {
        currentSink = null;
        state.endPicture();
        currentDisplay.set(null);
    }

    private int targetHeight() {
        int tallest = screens.isEmpty() ? 0 : DisplayResolution.UNKNOWN;
        for (BlockPos screen : screens) {
            OptionalInt height = ScreenDisplays.heightCellsFor(level, screen);
            if (height.isPresent()) tallest = Math.max(tallest, height.getAsInt());
        }
        return DisplayResolution.targetHeight(tallest, SoundstageConfig.maxVideoHeight());
    }

    private void relay() {
        List<VideoRelayState.Send<ServerPlayer>> sends = new ArrayList<>();
        while (running) {
            try {
                sends.clear();
                state.poll(System.nanoTime(), state.wantsAudience() ? audience() : List.<ServerPlayer>of(), sends);
                deliver(sends);
                reportTooLarge();
                pollDisplayInfo();
                pollSubtitleOptions();
            } catch (RuntimeException e) {
                if (pollFailures++ == 0) {
                    LOGGER.error("Video relay for receiver {} failed; it carries on, and later failures are "
                            + "counted, not logged", pos, e);
                }
            }
            LockSupport.parkNanos(PARK_NANOS);
        }
        sends.clear();
        state.close(sends);
        deliver(sends);
        closeDisplayInfo();
        closeSubtitleOptions();
        long tooLarge = state.tooLargeUnits();
        long shed = state.shedUnits();
        if (tooLarge > 0 || shed > 0 || deliveryFailures > 0 || pollFailures > 0) {
            LOGGER.info("Soundstage: video relay for receiver {} stopped: {} pictures too large to send, {} shed "
                            + "from a full queue, {} failed deliveries, {} failed polls",
                    pos.toShortString(), tooLarge, shed, deliveryFailures, pollFailures);
        }
    }

    private Collection<ServerPlayer> audience() {
        if (screens.isEmpty()) return List.of();
        Set<ServerPlayer> near = SpeakerAudience.collect(level, screens);
        near.removeIf(player -> player.hasDisconnected() || player.level() != level);
        return near;
    }

    private void pollDisplayInfo() {
        DisplaySnapshot snapshot = currentDisplay.get();
        if (snapshot == null) {
            closeDisplayInfo();
            return;
        }
        if (lastSentDisplay == null || lastSentDisplay.generation() != snapshot.generation()) {
            displayInfoTold.clear();
            lastSentDisplay = snapshot;
        }
        for (ServerPlayer player : audience()) {
            if (displayInfoTold.add(player)) {
                sendDisplayInfo(new DisplayInfoS2CPacket(pos, snapshot.generation(), snapshot.title(),
                        snapshot.author(), snapshot.artworkUrl(), snapshot.durationMillis(),
                        snapshot.stream(), snapshot.pictureExpected(), false), player);
            }
        }
    }

    private void closeDisplayInfo() {
        if (lastSentDisplay == null) return;
        DisplayInfoS2CPacket clear = DisplayInfoS2CPacket.clear(pos, lastSentDisplay.generation());
        for (ServerPlayer player : displayInfoTold) {
            sendDisplayInfo(clear, player);
        }
        displayInfoTold.clear();
        lastSentDisplay = null;
    }

    private void pollSubtitleOptions() {
        TrackSink sink = currentSink;
        SubtitleOptionsS2CPacket now = sink == null ? null : sink.optionsPacket(pos);
        if (now == null) {
            closeSubtitleOptions();
            return;
        }
        if (!now.equals(lastSentSubtitleOptions)) {
            subtitleOptionsTold.clear();
            lastSentSubtitleOptions = now;
        }
        for (ServerPlayer player : audience()) {
            if (subtitleOptionsTold.add(player)) sendSubtitleOptions(now, player);
        }
    }

    private void closeSubtitleOptions() {
        if (lastSentSubtitleOptions == null) return;
        SubtitleOptionsS2CPacket clear = SubtitleOptionsS2CPacket.clear(pos);
        for (ServerPlayer player : subtitleOptionsTold) sendSubtitleOptions(clear, player);
        subtitleOptionsTold.clear();
        lastSentSubtitleOptions = null;
    }

    private void sendSubtitleOptions(SubtitleOptionsS2CPacket packet, ServerPlayer player) {
        try {
            Network.sendToPlayer(player, packet);
        } catch (RuntimeException e) {
            if (deliveryFailures++ == 0) {
                LOGGER.error("Subtitle options delivery for receiver {} failed; later failures are counted, "
                        + "not logged", pos, e);
            }
        }
    }

    private void sendDisplayInfo(DisplayInfoS2CPacket packet, ServerPlayer player) {
        try {
            Network.sendToPlayer(player, packet);
        } catch (RuntimeException e) {
            if (deliveryFailures++ == 0) {
                LOGGER.error("Display info delivery for receiver {} failed; later failures are counted, "
                        + "not logged", pos, e);
            }
        }
    }

    private void deliver(List<VideoRelayState.Send<ServerPlayer>> sends) {
        for (VideoRelayState.Send<ServerPlayer> send : sends) {
            VideoPacket packet = switch (send.kind()) {
                case START -> VideoPacket.start(pos, send.generation(), 0, 0);
                case FRAME -> VideoPacket.frame(pos, send.generation(), send.millis(), send.flag(), send.data());
                case CLOCK -> VideoPacket.clock(pos, send.generation(), send.millis(), send.flag());
                case STOP -> VideoPacket.stop(pos, send.generation());
            };
            if (send.kind() == VideoRelayState.Kind.START) STARTS_SENT.addAndGet(send.to().size());
            for (ServerPlayer player : send.to()) {
                try {
                    Network.sendToPlayer(player, packet);
                } catch (RuntimeException e) {
                    PICTURE_FAILURES.incrementAndGet();
                    if (deliveryFailures++ == 0) {
                        LOGGER.error("Video delivery for receiver {} failed; later failures are counted, not "
                                + "logged", pos, e);
                    }
                }
            }
            deliverSubtitles(send);
        }
    }

    private static final long SUBTITLE_WINDOW_MILLIS = 10_000;

    private void deliverSubtitles(VideoRelayState.Send<ServerPlayer> send) {
        if (send.to().isEmpty()) return;
        if (send.kind() == VideoRelayState.Kind.START) {
            if (send.generation() != subtitlesGeneration) {
                subtitlesGeneration = send.generation();
                subtitlesCueIndex = 0;
            }
            TrackSink starting = currentSink;
            if (starting != null) subtitlesCuesVersion = starting.cuesVersion();
            sendSubtitlePacket(SubtitlePacket.reset(pos, send.generation()), send.to());
        } else if (send.kind() == VideoRelayState.Kind.CLOCK) {
            TrackSink sink = currentSink;
            if (sink != null && send.generation() == subtitlesGeneration && sink.cuesVersion() != subtitlesCuesVersion) {
                subtitlesCuesVersion = sink.cuesVersion();
                subtitlesCueIndex = 0;
                sendSubtitlePacket(SubtitlePacket.reset(pos, send.generation()), send.to());
            }
            CueTrack track = sink == null ? null : sink.cues;
            if (track == null || track.cues().isEmpty()) return;
            if (send.generation() != subtitlesGeneration) {
                return;
            }
            List<SubtitleCue> all = track.cues();
            List<SubtitleCue> due = null;
            long horizon = send.millis() + SUBTITLE_WINDOW_MILLIS;
            while (subtitlesCueIndex < all.size() && all.get(subtitlesCueIndex).startMs() <= horizon) {
                SubtitleCue cue = all.get(subtitlesCueIndex++);
                if (cue.endMs() <= send.millis()) continue;
                if (due == null) due = new ArrayList<>();
                due.add(cue);
            }
            for (int from = 0; due != null && from < due.size(); from += SubtitlePacket.MAX_CUES_PER_PACKET) {
                List<SubtitleCue> chunk = due.subList(from, Math.min(due.size(), from + SubtitlePacket.MAX_CUES_PER_PACKET));
                sendSubtitlePacket(SubtitlePacket.cues(pos, send.generation(), chunk), send.to());
            }
        }
    }

    private void sendSubtitlePacket(SubtitlePacket packet, List<ServerPlayer> to) {
        for (ServerPlayer player : to) {
            try {
                Network.sendToPlayer(player, packet);
            } catch (RuntimeException e) {
                if (deliveryFailures++ == 0) {
                    LOGGER.error("Subtitle delivery for receiver {} failed; later failures are counted, not "
                            + "logged", pos, e);
                }
            }
        }
    }

    private void reportTooLarge() {
        if (tooLargeReported || state.tooLargeUnits() == 0) return;
        tooLargeReported = true;
        LOGGER.warn("Soundstage: a picture of {} bytes for receiver {} is more than one video packet carries ({}); "
                        + "it was dropped, and its screens wait for the next keyframe. Any more are counted, "
                        + "not logged.",
                state.firstTooLargeBytes(), pos.toShortString(), VideoPacket.MAX_DATA);
    }

    private final class TrackSink implements VideoSink {
        private final AudioTrack track;
        private volatile CueTrack cues;
        private volatile SubtitleSource subtitleSource;
        private volatile String choice = SubtitleChoice.AUTO;
        private volatile String autoKey;
        private int cuesVersion;

        TrackSink(AudioTrack track) {
            this.track = track;
        }

        @Override
        public void begin(int generation, long startMillis) {
            state.begin(track, generation, startMillis);
        }

        @Override
        public void accept(int generation, long ptsMillis, VideoAccessUnit unit) {
            state.accept(track, generation, ptsMillis, unit.keyframe(), unit.annexB());
        }

        @Override
        public void end(int generation) {
            state.end(track, generation);
        }

        @Override
        public void abandon() {
            state.abandon(track);
            noPictureComing(this);
        }

        @Override
        public boolean hasScreens() {
            return !screens.isEmpty();
        }

        @Override
        public boolean wantsPicture() {
            return wantsPicture.getAsBoolean();
        }

        @Override
        public com.nachtraben.soundstage.core.video.SurroundBuffer surround() {
            return surround;
        }

        @Override
        public void subtitles(CueTrack cues) {
            this.cues = cues;
        }

        @Override
        public void subtitleSource(SubtitleSource source) {
            if (com.nachtraben.soundstage.RigStaging.fault("subtitles-not-offered")) return;
            this.subtitleSource = source;
            applyChoice(subtitleMemory.get(identifier()));
        }

        @Override
        public String playerLanguage() {
            if (com.nachtraben.soundstage.RigStaging.fault("subtitle-language-ignored")) return null;
            return playerLanguage.get();
        }

        boolean offersSubtitles() {
            return subtitleSource != null;
        }

        String identifier() {
            return track.getIdentifier();
        }

        synchronized int cuesVersion() {
            return cuesVersion;
        }

        void applyChoice(String choice) {
            SubtitleSource source = subtitleSource;
            if (source == null) return;
            int version;
            synchronized (this) {
                this.choice = choice == null ? SubtitleChoice.AUTO : choice;
                version = ++cuesVersion;
                cues = null;
            }
            String auto = source.autoKey(playerLanguage());
            this.autoKey = auto;
            SubtitleChoice.resolve(this.choice, source.options(), auto).ifPresent(key ->
                    source.start(key, delivered -> {
                        synchronized (this) {
                            if (cuesVersion == version) cues = delivered;
                        }
                    }, () -> {
                        synchronized (this) {
                            return cuesVersion == version;
                        }
                    }));
        }

        SubtitleOptionsS2CPacket optionsPacket(BlockPos receiverPos) {
            SubtitleSource source = subtitleSource;
            if (source == null) return null;
            return new SubtitleOptionsS2CPacket(receiverPos, source.options(), choice, autoKey);
        }
    }
}
