package com.nachtraben.soundstage.audio.video;

import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.audio.media.PictureCopies;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.config.SoundstageConfig;
import com.nachtraben.soundstage.core.TrackDuration;
import com.nachtraben.soundstage.core.video.CpuPressure;
import com.nachtraben.soundstage.core.video.CueTrack;
import com.nachtraben.soundstage.core.video.PictureAdmission;
import com.nachtraben.soundstage.core.video.PictureSize;
import com.nachtraben.soundstage.core.video.PictureSlots;
import com.nachtraben.soundstage.core.video.PictureStepMemory;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.core.video.SubtitleCue;
import com.nachtraben.soundstage.core.video.SurroundBuffer;
import com.nachtraben.soundstage.core.video.VideoAccessUnit;
import com.nachtraben.soundstage.core.video.VideoRelayState;
import com.nachtraben.soundstage.network.DisplayInfoS2CPacket;
import com.nachtraben.soundstage.network.SubtitleOptionsS2CPacket;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class VideoRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    private static final long PARK_NANOS = 20_000_000L;

    private static final PictureSlots PLACES = new PictureSlots();

    private static final PictureAdmission ADMISSION =
            new PictureAdmission();

    private static final CpuPressure PROCESSORS =
            CpuPressure.ofThisMachine();

    private static final PictureAdmission.Room ROOM =
            new PictureAdmission.Room() {
                @Override
                public boolean copiesHeldBack() {
                    return PictureCopies.allHeldBack();
                }

                @Override
                public CpuPressure.Reading processors() {
                    return PROCESSORS.read(PictureCopies.processes(),
                            System.nanoTime());
                }
            };

    static final long CARRY_NANOS = 10_000_000_000L;

    private final Object placeLock = new Object();
    private boolean placesClosed;
    private PictureAdmission.Kind lastAnswer;
    private AudioTrack carryFrom;
    private boolean carrying;
    private TrackSink carriedTo;
    private long carryUntilNanos;

    private final ServerLevel level;
    private final BlockPos pos;
    private final List<BlockPos> screens;
    private final VideoRelayState<ServerPlayer> state;
    private final Map<UUID, ServerPlayer> watcherPlayers = new HashMap<>();
    private final Thread thread;
    private volatile boolean running;
    private final SurroundBuffer surround =
            new SurroundBuffer();

    private long deliveryFailures;
    private long pollFailures;
    private boolean tooLargeReported;
    private int subtitlesGeneration = -1;
    private int subtitlesCueIndex;
    private int subtitlesCuesVersion = -1;

    private final SubtitleChoice.Memory subtitleMemory;
    private final Supplier<String> playerLanguage;
    private final PictureStepMemory steps;
    private SubtitleOptionsS2CPacket lastSentSubtitleOptions;
    private final Set<ServerPlayer> subtitleOptionsTold = new HashSet<>();

    private volatile TrackSink currentSink;

    private final BooleanSupplier wantsPicture;

    private record DisplaySnapshot(int generation, String title, String author, @Nullable String artworkUrl,
                                    long durationMillis, boolean stream, boolean pictureExpected,
                                    @Nullable TrackSink sink) {
        DisplaySnapshot withoutPicture(int newGeneration) {
            return new DisplaySnapshot(newGeneration, title, author, artworkUrl, durationMillis, stream, false, sink);
        }
    }

    private final AtomicReference<DisplaySnapshot> currentDisplay =
            new AtomicReference<>();
    private final AtomicInteger displayGenerationCounter = new AtomicInteger();
    private DisplaySnapshot lastSentDisplay;
    private final Set<ServerPlayer> displayInfoTold = new HashSet<>();

    private VideoRelay(AudioPlayer player, ServerLevel level, BlockPos pos, List<BlockPos> screens,
                        BooleanSupplier wantsPicture, SubtitleChoice.Memory subtitleMemory,
                        Supplier<String> playerLanguage, PictureStepMemory steps) {
        this.subtitleMemory = subtitleMemory;
        this.playerLanguage = playerLanguage;
        this.steps = steps;
        this.level = level;
        this.pos = pos.immutable();
        this.screens = screens;
        this.wantsPicture = wantsPicture;
        this.state = new VideoRelayState<>(VideoPacket.MAX_DATA, player::isPaused, ServerPlayer::getUUID);
        this.thread = new Thread(this::relay, "Soundstage-Video-" + this.pos.toShortString());
        this.thread.setDaemon(true);
    }

    public static @Nullable VideoRelay forReceiver(AudioPlayer player, ServerLevel level, BlockPos pos,
                                                   @Nullable IAudioReceiver receiver) {
        return receiver instanceof ReceiverBlockEntity withScreens
                ? new VideoRelay(player, level, pos, withScreens.getScreens(),
                        () -> SoundstageConfig.enableVideo() && withScreens.isPictureEnabled(),
                        withScreens.subtitleChoices(), withScreens::controllerLanguage, withScreens.pictureSteps())
                : null;
    }

    public SurroundBuffer surround() {
        return surround;
    }

    public void start() {
        running = true;
        thread.start();
    }

    public void stop() {
        running = false;
        synchronized (placeLock) {
            placesClosed = true;
            carrying = false;
            carriedTo = null;
            PLACES.release(this);
        }
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
        long duration = TrackDuration.reported(track.getDuration());
        currentDisplay.set(new DisplaySnapshot(displayGenerationCounter.incrementAndGet(),
                info.title, info.author, artworkUrl, duration, info.isStream, pictureExpected, sink));
        if (sink == null) {
            dropPlace();
            state.endPicture();
        } else {
            VideoCapableTrack video = (VideoCapableTrack) track;
            video.setPictureTarget(pictureTarget());
            state.attach(track, track::getPosition);
            synchronized (placeLock) {
                currentSink = sink;
                if (carrying) {
                    carrying = false;
                    carriedTo = sink;
                    carryUntilNanos = System.nanoTime() + CARRY_NANOS;
                }
            }
            video.setVideoSink(sink);
        }
    }

    private void noPictureComing(TrackSink sink) {
        DisplaySnapshot display = currentDisplay.get();
        if (display == null || display.sink() != sink || !display.pictureExpected()) return;
        currentDisplay.compareAndSet(display, display.withoutPicture(displayGenerationCounter.incrementAndGet()));
    }

    public void detach(AudioTrack track, boolean reclaimed) {
        if (track instanceof VideoCapableTrack video) video.setVideoSink(null);
        if (!keepPlaceFor(track)) dropPlace();
        state.detach(track, reclaimed);
        if (!reclaimed) currentDisplay.set(null);
    }

    public void carryPlace(@Nullable AudioTrack from) {
        synchronized (placeLock) {
            carryFrom = from;
        }
    }

    private boolean keepPlaceFor(AudioTrack track) {
        synchronized (placeLock) {
            boolean keep = track != null && track == carryFrom && !placesClosed && PLACES.holds(this);
            carryFrom = null;
            if (!keep) return false;
            currentSink = null;
            carrying = true;
            carriedTo = null;
            carryUntilNanos = System.nanoTime() + CARRY_NANOS;
            return true;
        }
    }

    void expireCarriedPlace(long nowNanos) {
        synchronized (placeLock) {
            if ((!carrying && carriedTo == null) || nowNanos - carryUntilNanos < 0) return;
            carrying = false;
            carriedTo = null;
            PLACES.release(this);
        }
        LOGGER.debug("Soundstage: receiver {} gives back the picture place its replayed track did not take up.",
                pos.toShortString());
    }

    public void seeking(AudioTrack track) {
        if (track instanceof VideoCapableTrack video) video.setPictureTarget(pictureTarget());
        state.seeking(track);
    }

    private void dropPlace() {
        synchronized (placeLock) {
            currentSink = null;
            carrying = false;
            carriedTo = null;
            PLACES.release(this);
        }
    }

    public boolean chooseSubtitles(String choice) {
        TrackSink sink = currentSink;
        if (sink == null || !sink.offersSubtitles()) return false;
        subtitleMemory.put(sink.identifier(), choice);
        sink.applyChoice(choice);
        return true;
    }

    public void watch(ServerPlayer player, boolean watching) {
        if (!running) return;
        UUID id = player.getUUID();
        synchronized (watcherPlayers) {
            if (watching && watcherPlayers.containsKey(id)) {
                watcherPlayers.put(id, player);
                return;
            }
        }
        if (watching && !audience().contains(player)) return;
        synchronized (watcherPlayers) {
            if (watching) watcherPlayers.put(id, player);
            else watcherPlayers.remove(id);
            state.watch(id, watching);
        }
    }

    public void endPicture() {
        dropPlace();
        state.endPicture();
        currentDisplay.set(null);
    }

    private PictureSize.Target pictureTarget() {
        if (screens.isEmpty()) return PictureSize.Target.of(List.of());
        Set<PictureSize.Display> displays = new LinkedHashSet<>();
        for (BlockPos screen : screens) ScreenDisplays.displayFor(level, screen).ifPresent(displays::add);
        return displays.isEmpty() ? PictureSize.Target.UNKNOWN : PictureSize.Target.of(List.copyOf(displays));
    }

    private void relay() {
        List<VideoRelayState.Send<ServerPlayer>> sends = new ArrayList<>();
        while (running) {
            try {
                sends.clear();
                forgetDepartedWatchers();
                expireCarriedPlace(System.nanoTime());
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
        synchronized (watcherPlayers) {
            watcherPlayers.clear();
        }
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

    private void forgetDepartedWatchers() {
        synchronized (watcherPlayers) {
            Iterator<Map.Entry<UUID, ServerPlayer>> it = watcherPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ServerPlayer> watcher = it.next();
                ServerPlayer player = watcher.getValue();
                if (player.hasDisconnected() || player.level() != level) {
                    it.remove();
                    state.forgetWatcher(watcher.getKey());
                }
            }
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
            for (ServerPlayer player : send.to()) {
                try {
                    Network.sendToPlayer(player, packet);
                } catch (RuntimeException e) {
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
            if (sink != null && send.generation() == subtitlesGeneration
                    && sink.cuesVersion() != subtitlesCuesVersion) {
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
                List<SubtitleCue> chunk =
                        due.subList(from, Math.min(due.size(), from + SubtitlePacket.MAX_CUES_PER_PACKET));
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
        private SubtitleSource appliedSource;
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
            releasePicturePlace();
            noPictureComing(this);
        }

        @Override
        public boolean takePicturePlace() {
            synchronized (placeLock) {
                if (placesClosed || currentSink != this) return false;
                if (carriedTo == this) carriedTo = null;
                int ceiling = SoundstageConfig.maxPicturesEncoding();
                int before = PLACES.taken();
                PictureAdmission.Answer answer =
                        PLACES.take(VideoRelay.this, ceiling, ADMISSION, System.nanoTime(), ROOM);
                tell(answer, before);
                return answer.admitted();
            }
        }

        private void tell(PictureAdmission.Answer answer, int before) {
            PictureAdmission.Kind kind = answer.kind();
            if (kind == lastAnswer) return;
            lastAnswer = kind;
            String measure = answer.reading() == null ? "" : "; " + answer.reading().describe();
            switch (kind) {
                case ADMITTED -> {
                    if (PLACES.taken() > before) {
                        LOGGER.info("Soundstage: receiver {} may encode its picture, {} of them now{}.",
                                pos.toShortString(), PLACES.taken(), measure);
                    }
                }
                case CEILING -> LOGGER.info("Soundstage: receiver {} waits to encode its picture: {} are encoding, as "
                        + "many as maxPicturesEncoding allows.", pos.toShortString(), before);
                case SETTLING -> LOGGER.info("Soundstage: receiver {} waits to encode its picture: another one started "
                        + "under {} s ago.", pos.toShortString(),
                        PictureAdmission.SETTLE_NANOS / 1_000_000_000L);
                case COPY_BEHIND -> LOGGER.info("Soundstage: receiver {} waits to encode its picture: a picture being "
                        + "made is not yet well ahead of playback.", pos.toShortString());
                case BUSY -> LOGGER.info("Soundstage: receiver {} waits to encode its picture: the processors are "
                        + "busy{}.", pos.toShortString(), measure);
                case NO_MEASURE -> LOGGER.info("Soundstage: receiver {} waits to encode its picture: nothing measures "
                        + "this server's processors, so no more than {} encode at once.", pos.toShortString(),
                        PictureAdmission.WITHOUT_MEASURE);
            }
        }

        @Override
        public void releasePicturePlace() {
            synchronized (placeLock) {
                if (currentSink != this) return;
                if (carriedTo == this) carriedTo = null;
                PLACES.release(VideoRelay.this);
            }
        }

        @Override
        public int rememberedStep() {
            return steps.remembered(System.nanoTime());
        }

        @Override
        public void stepped(int height) {
            steps.stepped(height, System.nanoTime());
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
        public SurroundBuffer surround() {
            return surround;
        }

        @Override
        public void subtitles(CueTrack cues) {
            this.cues = cues;
        }

        @Override
        public void subtitleSource(SubtitleSource source) {
            this.subtitleSource = source;
            applyChoice(subtitleMemory.get(identifier()));
        }

        @Override
        public String playerLanguage() {
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
            String chosen = choice == null ? SubtitleChoice.AUTO : choice;
            String auto = source.autoKey(playerLanguage());
            int version;
            synchronized (this) {
                if (source == appliedSource && chosen.equals(this.choice) && Objects.equals(auto, autoKey)) return;
                appliedSource = source;
                this.choice = chosen;
                this.autoKey = auto;
                version = ++cuesVersion;
                cues = null;
            }
            SubtitleChoice.resolve(chosen, source.options(), auto).ifPresent(key ->
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
