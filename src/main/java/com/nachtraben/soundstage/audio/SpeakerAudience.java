package com.nachtraben.soundstage.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpeakerAudience {

    private SpeakerAudience() {}

    private static final long SNAPSHOT_TTL_TICKS = 40;

    private record Key(ResourceKey<Level> dimension, long pos) {}

    private record Snapshot(long gameTime, List<ServerPlayer> listeners) {}

    private static final Map<Key, Snapshot> AUDIENCES = new ConcurrentHashMap<>();

    private static Key keyOf(Level level, BlockPos pos) {
        return new Key(level.dimension(), pos.asLong());
    }

    private static final java.util.Map<Key, com.nachtraben.soundstage.core.SpeakerAcoustics> ACOUSTICS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record VolumeSnapshot(long publicationId, int percent) {}

    private static final java.util.Map<Key, VolumeSnapshot> VOLUMES =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_PUBLICATION_ID =
            new java.util.concurrent.atomic.AtomicLong();

    public static long nextPublicationId() {
        return NEXT_PUBLICATION_ID.incrementAndGet();
    }

    public static int volumeFor(ServerLevel level, BlockPos pos) {
        VolumeSnapshot snapshot = VOLUMES.get(keyOf(level, pos));
        return snapshot == null ? 100 : snapshot.percent();
    }

    public static void publishVolume(ServerLevel level, BlockPos pos, long publicationId,
                                     int percent) {
        VOLUMES.put(keyOf(level, pos), new VolumeSnapshot(publicationId,
                Math.max(0, Math.min(100, percent))));
    }

    public static void forgetVolume(Level level, BlockPos pos, long publicationId) {
        VOLUMES.computeIfPresent(keyOf(level, pos), (key, snapshot) ->
                snapshot.publicationId() == publicationId ? null : snapshot);
    }

    private record ChannelSnapshot(long publicationId, com.nachtraben.soundstage.core.ChannelMode mode) {}

    private static final java.util.Map<Key, ChannelSnapshot> CHANNELS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void publishChannel(ServerLevel level, BlockPos pos, long publicationId,
                                      com.nachtraben.soundstage.core.ChannelMode mode) {
        if (mode != null) CHANNELS.put(keyOf(level, pos), new ChannelSnapshot(publicationId, mode));
    }

    public static void forgetChannel(Level level, BlockPos pos, long publicationId) {
        CHANNELS.computeIfPresent(keyOf(level, pos), (key, snapshot) ->
                snapshot.publicationId() == publicationId ? null : snapshot);
    }

    public static boolean wantsSurround(ServerLevel level, List<BlockPos> speakers) {
        boolean left = false, right = false;
        for (BlockPos pos : speakers) {
            ChannelSnapshot snapshot = CHANNELS.get(keyOf(level, pos));
            if (snapshot == null) continue;
            switch (snapshot.mode()) {
                case CENTER -> { return true; }
                case SURROUND_LEFT -> left = true;
                case SURROUND_RIGHT -> right = true;
                default -> { }
            }
        }
        return left && right;
    }

    public static com.nachtraben.soundstage.core.SpeakerAcoustics acousticsFor(ServerLevel level, BlockPos pos) {
        return ACOUSTICS.get(keyOf(level, pos));
    }

    public static void publishAcoustics(ServerLevel level, BlockPos pos,
                                        com.nachtraben.soundstage.core.SpeakerAcoustics acoustics) {
        if (acoustics != null) ACOUSTICS.put(keyOf(level, pos), acoustics);
    }

    public static void publish(ServerLevel level, BlockPos pos, List<ServerPlayer> listeners,
                               com.nachtraben.soundstage.core.SpeakerAcoustics acoustics) {
        if (acoustics != null) ACOUSTICS.put(keyOf(level, pos), acoustics);
        publish(level, pos, listeners);
    }

    public static void publish(ServerLevel level, BlockPos pos, List<ServerPlayer> listeners) {
        AUDIENCES.put(keyOf(level, pos), new Snapshot(level.getGameTime(), List.copyOf(listeners)));
    }

    public static void forget(Level level, BlockPos pos) {
        if (com.nachtraben.soundstage.RigStaging.fault("no-forget")) return;
        AUDIENCES.remove(keyOf(level, pos));
    }

    public static void forgetAcoustics(Level level, BlockPos pos,
                                       com.nachtraben.soundstage.core.SpeakerAcoustics published) {
        if (published == null) return;
        Key key = keyOf(level, pos);
        ACOUSTICS.remove(key, published);
    }

    public static void forgetAll() {
        AUDIENCES.clear();
        ACOUSTICS.clear();
        VOLUMES.clear();
        CHANNELS.clear();
    }

    public static Set<ServerPlayer> collect(ServerLevel level, Collection<BlockPos> emitters) {
        if (com.nachtraben.soundstage.RigStaging.fault("union-drops-speaker") && emitters.size() > 1) {
            emitters = java.util.List.of(emitters.iterator().next());
        }
        long now = level.getGameTime();
        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        for (BlockPos pos : emitters) {
            Key key = keyOf(level, pos);
            Snapshot snapshot = AUDIENCES.get(key);
            if (snapshot == null) {
                continue;
            }
            if (now - snapshot.gameTime() > SNAPSHOT_TTL_TICKS) {
                AUDIENCES.remove(key, snapshot);
                continue;
            }
            recipients.addAll(snapshot.listeners());
        }
        return recipients;
    }

    public static void pruneExpired(long gameTime) {
        Iterator<Map.Entry<Key, Snapshot>> it = AUDIENCES.entrySet().iterator();
        while (it.hasNext()) {
            if (gameTime - it.next().getValue().gameTime() > SNAPSHOT_TTL_TICKS) {
                it.remove();
            }
        }
    }
}
