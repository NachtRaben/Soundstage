package com.nachtraben.soundstage.core.video;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PictureWatchers {

    public static final long KEEP_BEHIND_MILLIS = 1000;

    public static final long MAX_CACHED_BYTES = 4L * 1024 * 1024;

    static final int REPLAY_BURST = 2;
    static final long REPLAY_INTERVAL_NANOS = 2_000_000_000L;

    public record Picture(long ptsMillis, boolean keyframe, byte[] data) {}

    private final Set<UUID> watching = new HashSet<>();
    private final Map<UUID, Budget> budgets = new HashMap<>();

    private final ArrayDeque<Picture> cache = new ArrayDeque<>();
    private long cachedBytes;
    private boolean cacheValid;

    public boolean request(UUID player, boolean watches) {
        return watches ? watching.add(player) : watching.remove(player);
    }

    public boolean watching(UUID player) {
        return watching.contains(player);
    }

    public void forget(UUID player) {
        watching.remove(player);
        budgets.remove(player);
    }

    public boolean mayReplay(UUID player, long nowNanos) {
        Budget budget = budgets.computeIfAbsent(player, id -> new Budget(nowNanos));
        budget.refill(nowNanos);
        if (budget.replays < 1) return false;
        budget.replays--;
        return true;
    }

    public void sent(long ptsMillis, boolean keyframe, boolean afterGap, byte[] data) {
        if (keyframe) {
            if (!cacheValid) dropPictures();
            cacheValid = true;
        } else if (afterGap || !cacheValid) {
            dropPictures();
            return;
        }
        if (cachedBytes + data.length > MAX_CACHED_BYTES) {
            dropPictures();
            return;
        }
        cache.addLast(new Picture(ptsMillis, keyframe, data));
        cachedBytes += data.length;
    }

    public void keepFrom(long positionMillis) {
        int newest = -1;
        int index = 0;
        for (Picture picture : cache) {
            if (index > 0 && picture.keyframe() && picture.ptsMillis() <= positionMillis - KEEP_BEHIND_MILLIS) {
                newest = index;
            }
            index++;
        }
        for (int i = 0; i < newest; i++) cachedBytes -= cache.pollFirst().data().length;
    }

    public void dropPictures() {
        cache.clear();
        cachedBytes = 0;
        cacheValid = false;
    }

    public List<Picture> pictures() {
        return cacheValid ? new ArrayList<>(cache) : List.of();
    }

    public void clear() {
        watching.clear();
        budgets.clear();
        dropPictures();
    }

    private static final class Budget {
        double replays = REPLAY_BURST;
        long refilledNanos;

        Budget(long nowNanos) {
            refilledNanos = nowNanos;
        }

        void refill(long nowNanos) {
            long elapsed = nowNanos - refilledNanos;
            if (elapsed <= 0) return;
            replays = Math.min(REPLAY_BURST, replays + (double) elapsed / REPLAY_INTERVAL_NANOS);
            refilledNanos = nowNanos;
        }
    }
}
