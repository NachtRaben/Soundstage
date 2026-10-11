package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.core.video.DecodeWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class VideoDecodeWorkers {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");
    private static final int NICER = 10;

    private VideoDecodeWorkers() {
    }

    static final DecodeWorkers POOL = DecodeWorkers.pool("Soundstage-Video-Worker",
            DecodeWorkers.threadsFor(Runtime.getRuntime().availableProcessors()), VideoDecodeWorkers::lower);

    private static void lower(Thread thread) {
        thread.setPriority(Thread.MIN_PRIORITY);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return;
        try {
            String tid = Files.readSymbolicLink(Path.of("/proc/thread-self")).getFileName().toString();
            String stat = Files.readString(Path.of("/proc/thread-self/stat"));
            String[] fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
            int niceness = Math.min(19, Integer.parseInt(fields[16]) + NICER);
            Process renice = new ProcessBuilder("renice", "-n", String.valueOf(niceness), "-p", tid)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!renice.waitFor(2, TimeUnit.SECONDS)) renice.destroyForcibly();
        } catch (Exception e) {
            LOGGER.debug("Soundstage: a video decoding thread keeps the game's priority: {}", e.toString());
        }
    }
}
