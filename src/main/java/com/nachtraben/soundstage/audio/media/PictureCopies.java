package com.nachtraben.soundstage.audio.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PictureCopies {

    private static final Set<FfmpegRuns> RUNNING = ConcurrentHashMap.newKeySet();

    private PictureCopies() {
    }

    static void started(FfmpegRuns runs) {
        RUNNING.add(runs);
    }

    static void ended(FfmpegRuns runs) {
        RUNNING.remove(runs);
    }

    public static boolean allHeldBack() {
        for (FfmpegRuns runs : RUNNING) {
            if (!runs.heldBack()) return false;
        }
        return true;
    }

    public static List<Long> processes() {
        List<Long> pids = new ArrayList<>();
        for (FfmpegRuns runs : RUNNING) {
            long pid = runs.copyProcess();
            if (pid > 0) pids.add(pid);
        }
        return pids;
    }
}
