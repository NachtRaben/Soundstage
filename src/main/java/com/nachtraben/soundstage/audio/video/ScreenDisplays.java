package com.nachtraben.soundstage.audio.video;

import com.nachtraben.soundstage.core.video.PictureSize;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenDisplays {

    private ScreenDisplays() {}

    private static final long SNAPSHOT_TTL_TICKS = 40;

    private record Key(ResourceKey<Level> dimension, long pos) {}

    private record Snapshot(long gameTime, PictureSize.Display display) {}

    private static final Map<Key, Snapshot> DISPLAYS = new ConcurrentHashMap<>();

    private static Key keyOf(Level level, BlockPos pos) {
        return new Key(level.dimension(), pos.asLong());
    }

    public static void publish(ServerLevel level, BlockPos pos, int widthCells, int heightCells, boolean fill) {
        DISPLAYS.put(keyOf(level, pos), new Snapshot(level.getGameTime(),
                new PictureSize.Display(widthCells, heightCells, fill)));
    }

    public static Optional<PictureSize.Display> displayFor(ServerLevel level, BlockPos pos) {
        Key key = keyOf(level, pos);
        Snapshot snapshot = DISPLAYS.get(key);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (level.getGameTime() - snapshot.gameTime() > SNAPSHOT_TTL_TICKS) {
            DISPLAYS.remove(key, snapshot);
            return Optional.empty();
        }
        return Optional.of(snapshot.display());
    }

    public static void forget(Level level, BlockPos pos) {
        DISPLAYS.remove(keyOf(level, pos));
    }

    public static void forgetAll() {
        DISPLAYS.clear();
    }

    public static void pruneExpired(long gameTime) {
        Iterator<Map.Entry<Key, Snapshot>> it = DISPLAYS.entrySet().iterator();
        while (it.hasNext()) {
            if (gameTime - it.next().getValue().gameTime() > SNAPSHOT_TTL_TICKS) {
                it.remove();
            }
        }
    }
}
