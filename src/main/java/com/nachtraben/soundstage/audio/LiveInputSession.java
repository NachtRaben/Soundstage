package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.core.LiveInputBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveInputSession {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    public enum End {
        STOPPED(true, null),
        LEFT(false, null),
        MOVED(false, null),
        WENT_QUIET(true, "chat.soundstage.share.ended.quiet"),
        RECEIVER_MOVED_ON(true, "chat.soundstage.share.ended.receiver"),
        PAUSED(true, "chat.soundstage.share.ended.paused"),
        RECEIVER_GONE(true, "chat.soundstage.share.ended.gone"),
        FAILED(true, "chat.soundstage.share.ended.failed"),
        SERVER_STOPPING(false, null);

        final boolean tellSharer;
        final String messageKey;

        End(boolean tellSharer, String messageKey) {
            this.tellSharer = tellSharer;
            this.messageKey = messageKey;
        }
    }

    private static final AtomicLong IDS = new AtomicLong();

    private final long id = IDS.incrementAndGet();
    private final UUID sharer;
    private final String sharerName;
    private final ServerLevel level;
    private final BlockPos pos;
    private final TrackScheduler scheduler;
    private final LiveInputBuffer buffer = new LiveInputBuffer(System.nanoTime());
    private final AtomicReference<End> ended = new AtomicReference<>();

    LiveInputSession(ServerPlayer sharer, ServerLevel level, BlockPos pos, TrackScheduler scheduler) {
        this.sharer = sharer.getUUID();
        //? if >=1.21.11 {
        /*this.sharerName = sharer.getGameProfile().name();
        *///?} else {
        this.sharerName = sharer.getGameProfile().getName();
        //?}
        this.level = level;
        this.pos = pos.immutable();
        this.scheduler = scheduler;
    }

    public long id() {
        return id;
    }

    public UUID sharer() {
        return sharer;
    }

    public String sharerName() {
        return sharerName;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    TrackScheduler scheduler() {
        return scheduler;
    }

    public LiveInputBuffer buffer() {
        return buffer;
    }

    public boolean isEnded() {
        return ended.get() != null;
    }

    public End endReason() {
        return ended.get();
    }

    void end(End reason) {
        if (!ended.compareAndSet(null, reason)) return;
        LOGGER.info("Share from {} on receiver {} ended: {} ({} packets received, {} refused, {} skipped, {} underruns)",
                sharerName, pos, reason, buffer.accepted(), buffer.refused(), buffer.skipped(), buffer.underruns());
        buffer.close();
        LiveInputSessions.forget(this);
        if (reason.tellSharer) LiveInputSessions.tellSharerEnded(this, reason);
    }
}
