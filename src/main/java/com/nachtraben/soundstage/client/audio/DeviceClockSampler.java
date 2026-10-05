package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.core.DeviceClockMap;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTDeviceClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeviceClockSampler {

    private DeviceClockSampler() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    public record DeviceTime(long heardOffsetNanos, long latencyNanos) {}

    static final long RETRY_NANOS = 5_000_000_000L;

    private static final boolean TRACE = Boolean.getBoolean("soundstage.synctrace");
    private static final long TRACE_INTERVAL_NANOS = 5_000_000_000L;

    private static final DeviceClockMap MAP = new DeviceClockMap();
    private static final long[] READING = new long[2];
    private static long device;
    private static boolean described;
    private static long retryAtNanos;
    private static int refusals;
    private static int lastRefusal;
    private static long lastTraceNanos;

    private static volatile DeviceTime current;
    private static final String READING_STATUS = "reading";
    private static volatile String status = "not read yet";
    private static volatile long readingLatencyNanos;

    public static DeviceTime current() {
        return current;
    }

    public static String status() {
        String words = status;
        return words == READING_STATUS
                ? String.format(java.util.Locale.ROOT, "reading, latency %.3f ms", readingLatencyNanos / 1e6)
                : words;
    }

    public static void sample() {
        if (com.nachtraben.soundstage.RigStaging.fault("no-device-clock-sampler")) {
            current = null;
            status = "forced off by the rig";
            return;
        }
        if (!JitterAudioStream.probeClockReadSupport()) {
            status = "unsupported: the device has no ALC_SOFT_device_clock or the context no AL_SOFT_source_latency";
            return;
        }
        long context = ALC10.alcGetCurrentContext();
        long active = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        if (active == 0L) return;
        if (active != device) {
            device = active;
            MAP.reset();
            described = false;
            current = null;
            retryAtNanos = 0L;
            refusals = 0;
        }
        if (retryAtNanos != 0L && System.nanoTime() < retryAtNanos) return;
        ALC10.alcGetError(active);
        SOFTDeviceClock.alcGetInteger64vSOFT(active, SOFTDeviceClock.ALC_DEVICE_CLOCK_LATENCY_SOFT, READING);
        long now = System.nanoTime();
        int error = ALC10.alcGetError(active);
        if (error != ALC10.ALC_NO_ERROR) {
            refusals++;
            lastRefusal = error;
            retryAtNanos = now + RETRY_NANOS;
            status = "refused: ALC error " + error + " (" + refusals + " time" + (refusals == 1 ? "" : "s")
                    + "), retrying every " + RETRY_NANOS / 1_000_000_000L + " s";
            if (refusals == 1) {
                LOGGER.warn("Soundstage: the audio device refused its clock and latency (ALC error {}); speaker "
                        + "animation and screens follow the fill estimate until it answers, asked again every {} s",
                        error, RETRY_NANOS / 1_000_000_000L);
            }
            return;
        }
        if (refusals > 0) {
            LOGGER.info("Soundstage: the audio device reports its clock again, after {} refusal(s), the last ALC error {}",
                    refusals, lastRefusal);
            refusals = 0;
        }
        retryAtNanos = 0L;
        long reported = READING[1];
        long latency = reported;
        if (!described) {
            described = true;
            LOGGER.info("SOUNDSTAGE-CLOCK: device latency — OpenAL reports {} ms; clamped to {} ms",
                    String.format(java.util.Locale.ROOT, "%.3f", reported / 1e6),
                    DeviceClockMap.MAX_LATENCY_NANOS / 1_000_000L);
        }
        MAP.offer(READING[0], latency, now);
        long offset = MAP.heardOffsetNanos();
        if (offset != DeviceClockMap.UNKNOWN) {
            long mapLatency = MAP.latencyNanos();
            DeviceTime published = current;
            if (published == null || published.heardOffsetNanos() != offset
                    || published.latencyNanos() != mapLatency) {
                current = new DeviceTime(offset, mapLatency);
            }
            readingLatencyNanos = mapLatency;
            status = READING_STATUS;
        }
        if (TRACE && now - lastTraceNanos > TRACE_INTERVAL_NANOS) {
            lastTraceNanos = now;
            LOGGER.info("SOUNDSTAGE-CLOCK: device map — this reading {} ms behind the envelope; latency {} ms",
                    String.format(java.util.Locale.ROOT, "%.3f",
                            (offset - (READING[0] - MAP.latencyNanos() - now)) / 1e6),
                    String.format(java.util.Locale.ROOT, "%.3f", MAP.latencyNanos() / 1e6));
        }
    }
}
