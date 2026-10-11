package com.nachtraben.soundstage.core.video;

import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

public final class CpuPressure {

    public static final double BUSY_SHARE = 0.10;

    public static final double BUSY_LOAD = 0.80;

    static final long MIN_SPAN_NANOS = 500_000_000L;

    static final long STALE_NANOS = 10_000_000_000L;

    public enum Source {
        CGROUP,
        SYSTEM,
        LOAD,
        NONE
    }

    public record Reading(Source source, double value) {
        public static final Reading NONE = new Reading(Source.NONE, Double.NaN);

        public boolean measured() {
            return source != Source.NONE;
        }

        public boolean busy() {
            return switch (source) {
                case CGROUP, SYSTEM -> value >= BUSY_SHARE;
                case LOAD -> value >= BUSY_LOAD;
                case NONE -> false;
            };
        }

        public String describe() {
            return switch (source) {
                case CGROUP ->
                        String.format(Locale.ROOT, "a task waited for a processor %.0f%% of the time", 100 * value);
                case SYSTEM -> String.format(Locale.ROOT, "a task on this machine waited for a processor %.0f%% of the "
                        + "time", 100 * value);
                case LOAD -> String.format(Locale.ROOT, "the processors were %.0f%% in use", 100 * value);
                case NONE -> "nothing measures the processors here";
            };
        }
    }

    private record Sample(long nanos, long totalMicros, double share) {}

    private final Path root;
    private final DoubleSupplier load;
    private final Map<Path, Sample> samples = new HashMap<>();

    CpuPressure(Path root, DoubleSupplier load) {
        this.root = root;
        this.load = load;
    }

    public static CpuPressure ofThisMachine() {
        return new CpuPressure(Path.of("/"), CpuPressure::jvmLoad);
    }

    public synchronized Reading read(Collection<Long> processes, long nowNanos) {
        Set<Path> files = new LinkedHashSet<>();
        addCgroup(files, "self");
        for (long pid : processes) addCgroup(files, Long.toString(pid));
        Path system = root.resolve("proc/pressure/cpu");
        Set<Path> kept = new LinkedHashSet<>(files);
        kept.add(system);
        samples.keySet().retainAll(kept);
        double worst = -1;
        for (Path file : files) worst = Math.max(worst, share(file, nowNanos));
        if (worst >= 0) return new Reading(Source.CGROUP, worst);
        double machine = share(system, nowNanos);
        if (machine >= 0) return new Reading(Source.SYSTEM, machine);
        double used = load.getAsDouble();
        if (used >= 0 && used <= 1) return new Reading(Source.LOAD, used);
        return Reading.NONE;
    }

    private void addCgroup(Set<Path> files, String process) {
        List<String> lines;
        try {
            lines = Files.readAllLines(root.resolve("proc").resolve(process).resolve("cgroup"));
        } catch (IOException | RuntimeException e) {
            return;
        }
        for (String line : lines) {
            if (!line.startsWith("0::")) continue;
            String path = line.substring(3).strip();
            while (path.startsWith("/")) path = path.substring(1);
            Path cgroup = root.resolve("sys/fs/cgroup").normalize();
            Path file = (path.isEmpty() ? cgroup : cgroup.resolve(path)).resolve("cpu.pressure").normalize();
            if (file.startsWith(cgroup)) files.add(file);
        }
    }

    private double share(Path file, long nowNanos) {
        double avg10 = -1;
        long total = -1;
        try {
            for (String line : Files.readAllLines(file)) {
                if (!line.startsWith("some ")) continue;
                for (String field : line.substring(5).strip().split("\\s+")) {
                    if (field.startsWith("avg10=")) avg10 = Double.parseDouble(field.substring(6)) / 100;
                    if (field.startsWith("total=")) total = Long.parseLong(field.substring(6));
                }
            }
        } catch (IOException | RuntimeException e) {
            return -1;
        }
        if (avg10 < 0 && total < 0) return -1;
        Sample previous = samples.get(file);
        if (previous != null && total >= 0) {
            long span = nowNanos - previous.nanos();
            if (span < MIN_SPAN_NANOS) return previous.share() >= 0 ? previous.share() : Math.max(0, avg10);
            if (span <= STALE_NANOS && total >= previous.totalMicros()) {
                double share = Math.min(1, (total - previous.totalMicros()) * 1000.0 / span);
                samples.put(file, new Sample(nowNanos, total, share));
                return share;
            }
        }
        double share = Math.max(0, Math.min(1, avg10));
        samples.put(file, new Sample(nowNanos, total, share));
        return share;
    }

    private static double jvmLoad() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean()
                    instanceof OperatingSystemMXBean os) {
                return os.getCpuLoad();
            }
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
        return -1;
    }
}
