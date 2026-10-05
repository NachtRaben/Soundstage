package com.nachtraben.soundstage.audio.media;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MediaBrowseIo {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Soundstage-Media-Browse");
        thread.setDaemon(true);
        return thread;
    });

    private MediaBrowseIo() {}

    static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }
}
