package com.nachtraben.soundstage.client.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LinuxShareOutput {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    public static final String SINK_NAME = "Soundstage";
    public static final String COMMAND =
            "pactl load-module module-null-sink sink_name=Soundstage sink_properties=device.description=Soundstage";
    private static final long PACTL_TIMEOUT_SECONDS = 5;

    public static volatile String sinkNameOverride;

    public enum Result { CREATED, ALREADY_THERE, NO_PACTL, FAILED }

    private static volatile String createdModule;

    private LinuxShareOutput() {
    }

    public static boolean applies() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    public static String sinkName() {
        String override = sinkNameOverride;
        return override != null ? override : SINK_NAME;
    }

    public static String monitorName() {
        return "Monitor of " + sinkName();
    }

    public static CompletableFuture<Result> create() {
        CompletableFuture<Result> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> result.complete(createNow()), "Soundstage-Share-Output");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    private static Result createNow() {
        String name = sinkName();
        try {
            if (sinkExists(name)) return Result.ALREADY_THERE;
            String defaultBefore = defaultSink();
            Pactl load = pactl("load-module", "module-null-sink", "sink_name=" + name,
                    "sink_properties=device.description=" + name);
            if (load.exit != 0) {
                LOGGER.warn("pactl could not create the {} output (exit {}): {}", name, load.exit, load.output);
                return Result.FAILED;
            }
            createdModule = load.output.trim();
            String defaultAfter = defaultSink();
            if (defaultBefore != null && !defaultBefore.equals(defaultAfter)) {
                pactl("set-default-sink", defaultBefore);
                LOGGER.info("Creating the {} output moved the default output; put it back to {}", name, defaultBefore);
            }
            LOGGER.info("Created the {} output for sharing (module {})", name, createdModule);
            return Result.CREATED;
        } catch (IOException e) {
            LOGGER.info("Could not run pactl to create the {} output: {}", name, e.getMessage());
            return Result.NO_PACTL;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    public static void removeIfCreated() {
        String module = createdModule;
        if (module == null) return;
        createdModule = null;
        try {
            Pactl unload = pactl("unload-module", module);
            if (unload.exit == 0) {
                LOGGER.info("Removed the {} output (module {})", sinkName(), module);
            } else {
                LOGGER.warn("pactl could not remove module {} (exit {}): {}", module, unload.exit, unload.output);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not run pactl to remove module {}: {}", module, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean sinkExists(String name) throws IOException, InterruptedException {
        Pactl list = pactl("list", "short", "sinks");
        if (list.exit != 0) return false;
        for (String line : list.output.split("\n")) {
            String[] columns = line.split("\t");
            if (columns.length > 1 && columns[1].equals(name)) return true;
        }
        return false;
    }

    private static String defaultSink() throws IOException, InterruptedException {
        Pactl info = pactl("info");
        for (String line : info.output.split("\n")) {
            if (line.startsWith("Default Sink:")) return line.substring("Default Sink:".length()).trim();
        }
        return null;
    }

    private record Pactl(int exit, String output) {}

    private static Pactl pactl(String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(List.of(prepend("pactl", args))).redirectErrorStream(true);
        builder.environment().put("LC_ALL", "C");
        Process process = builder.start();
        if (!process.waitFor(PACTL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("pactl did not answer within " + PACTL_TIMEOUT_SECONDS + " s");
        }
        return new Pactl(process.exitValue(),
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static String[] prepend(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }
}
