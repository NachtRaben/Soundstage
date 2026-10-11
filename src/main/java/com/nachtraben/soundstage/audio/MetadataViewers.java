package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.block.IAudioReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MetadataViewers {

    private MetadataViewers() {}

    private static final Map<Key, Set<UUID>> VIEWERS = new ConcurrentHashMap<>();

    private record Key(ResourceKey<Level> dimension, long pos) {}

    private static Key keyOf(ServerLevel level, BlockPos pos) {
        return new Key(level.dimension(), pos.asLong());
    }

    public static void opened(ServerLevel level, BlockPos pos, ServerPlayer player) {
        VIEWERS.computeIfAbsent(keyOf(level, pos), k -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
    }

    public static void closed(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Set<UUID> viewers = VIEWERS.get(keyOf(level, pos));
        if (viewers != null) {
            viewers.remove(player.getUUID());
        }
    }

    public static void forgetPlayer(UUID playerId) {
        for (Set<UUID> viewers : VIEWERS.values()) {
            viewers.remove(playerId);
        }
    }

    public static void forgetAll() {
        VIEWERS.clear();
    }

    public static List<ServerPlayer> of(ServerLevel level, BlockPos pos) {
        Set<UUID> viewers = VIEWERS.get(keyOf(level, pos));
        if (viewers == null || viewers.isEmpty()) {
            return List.of();
        }

        List<ServerPlayer> out = new ArrayList<>(viewers.size());
        for (UUID id : viewers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.level() != level
                    || !IAudioReceiver.withinControlRange(player, pos)) {
                viewers.remove(id);
                continue;
            }
            out.add(player);
        }
        return out;
    }
}
