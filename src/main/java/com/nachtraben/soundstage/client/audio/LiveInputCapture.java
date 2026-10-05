package com.nachtraben.soundstage.client.audio;

import com.nachtraben.soundstage.core.OpusCodec;
import com.nachtraben.soundstage.network.LiveAudioC2SPacket;
import dev.armadeus.armalib.platform.client.ClientNetwork;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTDisconnect;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.List;

public final class LiveInputCapture {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Soundstage");

    private static final long STALL_NANOS = 3_000_000_000L;

    public interface Source extends AutoCloseable {
        boolean read(short[] frame) throws InterruptedException;

        boolean lost();

        @Override
        void close();
    }

    public static List<String> devices() {
        java.util.function.Function<String, Source> override = LiveInputClient.sourceOverride;
        if (override != null) return List.of(LiveInputClient.RIG_DEVICE);
        List<String> names = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
        return names == null ? List.of() : names;
    }

    private final String device;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile boolean sending;
    private volatile float level;
    private volatile String failure;
    private volatile boolean ended;

    LiveInputCapture(String device, boolean sending) {
        this.device = device;
        this.sending = sending;
        this.thread = new Thread(this::run, "Soundstage-Share-Capture");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    String device() {
        return device;
    }

    void setSending(boolean sending) {
        this.sending = sending;
    }

    float level() {
        return level;
    }

    String failure() {
        return failure;
    }

    boolean ended() {
        return ended;
    }

    void stop() {
        running = false;
        thread.interrupt();
    }

    private void run() {
        short[] frame = new short[OpusCodec.FRAME_SIZE * OpusCodec.CHANNELS];
        OpusCodec.Encoder encoder = null;
        Source source = null;
        try {
            java.util.function.Function<String, Source> override = LiveInputClient.sourceOverride;
            source = override != null ? override.apply(device) : new OpenAlSource(device);
            long lastFrame = System.nanoTime();
            while (running) {
                if (source.lost()) {
                    failure = "lost";
                    break;
                }
                if (!source.read(frame)) {
                    if (System.nanoTime() - lastFrame > STALL_NANOS) {
                        failure = "lost";
                        break;
                    }
                    continue;
                }
                lastFrame = System.nanoTime();
                level = Math.max(peak(frame), level * 0.85f);
                if (sending) {
                    if (encoder == null) encoder = new OpusCodec.Encoder();
                    byte[] packet = encoder.encode(frame);
                    if (packet != null) send(packet);
                }
            }
        } catch (InterruptedException e) {
        } catch (Throwable t) {
            if (running) {
                LOGGER.warn("Sharing from {} failed", device == null ? "the default input" : device, t);
                failure = source == null ? "open" : "lost";
            }
        } finally {
            if (encoder != null) encoder.close();
            if (source != null) source.close();
            level = 0;
            ended = true;
        }
    }

    private static float peak(short[] frame) {
        int peak = 0;
        for (short s : frame) {
            int a = Math.abs(s);
            if (a > peak) peak = a;
        }
        return peak / 32768f;
    }

    private static void send(byte[] packet) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        ClientNetwork.sendToServer(new LiveAudioC2SPacket(packet));
    }

    private static final class OpenAlSource implements Source {
        private static final long LISTED_CHECK_NANOS = 1_000_000_000L;

        private final String device;
        private final long handle;
        private final boolean reportsDisconnect;
        private final ShortBuffer buffer = MemoryUtil.memAllocShort(OpusCodec.FRAME_SIZE * OpusCodec.CHANNELS);
        private long lastListedCheck = System.nanoTime();

        OpenAlSource(String device) {
            this.device = device;
            long opened = ALC11.alcCaptureOpenDevice(device, OpusCodec.SAMPLE_RATE, AL10.AL_FORMAT_STEREO16,
                    OpusCodec.SAMPLE_RATE / 4);
            if (opened == 0L) {
                MemoryUtil.memFree(buffer);
                throw new IllegalStateException("OpenAL could not open capture device " + device);
            }
            handle = opened;
            reportsDisconnect = ALC10.alcIsExtensionPresent(handle, "ALC_EXT_disconnect");
            ALC11.alcCaptureStart(handle);
        }

        @Override
        public boolean read(short[] frame) throws InterruptedException {
            if (ALC10.alcGetInteger(handle, ALC11.ALC_CAPTURE_SAMPLES) < OpusCodec.FRAME_SIZE) {
                Thread.sleep(5);
                return false;
            }
            buffer.clear();
            ALC11.alcCaptureSamples(handle, buffer, OpusCodec.FRAME_SIZE);
            buffer.get(0, frame);
            return true;
        }

        @Override
        public boolean lost() {
            if (reportsDisconnect && ALC10.alcGetInteger(handle, EXTDisconnect.ALC_CONNECTED) == 0) return true;
            long now = System.nanoTime();
            if (device == null || now - lastListedCheck < LISTED_CHECK_NANOS) return false;
            lastListedCheck = now;
            List<String> listed = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            return listed == null || !listed.contains(device);
        }

        @Override
        public void close() {
            ALC11.alcCaptureStop(handle);
            ALC11.alcCaptureCloseDevice(handle);
            MemoryUtil.memFree(buffer);
        }
    }
}
