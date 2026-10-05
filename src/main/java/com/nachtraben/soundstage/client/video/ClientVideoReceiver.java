package com.nachtraben.soundstage.client.video;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.core.video.DecodedFrames;
import com.nachtraben.soundstage.core.video.H264FrameDecoder;
import com.nachtraben.soundstage.core.video.H264Headers;
import com.nachtraben.soundstage.core.video.VideoClock;
import com.nachtraben.soundstage.core.video.VideoUnitQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

final class ClientVideoReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger("Soundstage");

    static final int MAX_HELD = 3;
    static final int POOL_SIZE = 4;

    static final long MAX_CODED_PIXELS = 4096L * 1088;

    private static final long ADOPT_AFTER_NANOS = 2_000_000_000L;

    private static boolean listenerFailureReported;

    static final long SEEN_WINDOW_NANOS = 1_000_000_000L;

    private static final long IDLE_WAIT_MILLIS = 250;
    private static final long BUSY_WAIT_MILLIS = 10;

    final BlockPos pos;
    private final ResourceLocation textureId;
    private static final java.util.concurrent.atomic.AtomicLong TEXTURE_SERIAL = new java.util.concurrent.atomic.AtomicLong();
    private final VideoClock clock;

    private final Object lock = new Object();
    private volatile long lastSeenNanos = Long.MIN_VALUE / 2;
    private final java.util.concurrent.atomic.AtomicLong decodedUnits = new java.util.concurrent.atomic.AtomicLong();

    long decodedUnitCount() {
        return decodedUnits.get();
    }
    private final VideoUnitQueue units = new VideoUnitQueue();
    private final DecodedFrames<Slot> frames = new DecodedFrames<>();
    private final ArrayDeque<Slot> free = new ArrayDeque<>(POOL_SIZE);
    private int slots;
    private int epoch;
    private boolean closed;

    private boolean started;
    private int generation;
    private boolean active;
    private long lastAcceptedNanos;
    private VideoTexture texture;
    private ClientVideoReceivers.Frame shown;
    private long presentedRenderFrame = Long.MIN_VALUE;
    private ClientVideoReceivers.Frame presented;
    private long returnedNanos;
    private long shownPtsMillis;

    ClientVideoReceiver(BlockPos pos, long clockOffsetMillis) {
        this.pos = pos.immutable();
        this.textureId = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID,
                "video/" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + TEXTURE_SERIAL.incrementAndGet());
        this.clock = new VideoClock(clockOffsetMillis);
        Thread thread = new Thread(this::decodeLoop, "Soundstage-Video-Decode-" + this.pos.toShortString());
        thread.setDaemon(true);
        thread.start();
    }

    void start(int generation, long nowNanos) {
        boolean sameGeneration = started && active && generation == this.generation;
        started = true;
        this.generation = generation;
        active = true;
        lastAcceptedNanos = nowNanos;
        if (sameGeneration) {
            presented = null;
            presentedRenderFrame = Long.MIN_VALUE;
        } else {
            forgetPicture();
        }
        clock.start(nowNanos);
        synchronized (lock) {
            epoch++;
            units.clear();
            frames.clear(free::add);
            lock.notifyAll();
        }
    }

    void stop(int generation, long nowNanos) {
        if (!started || generation != this.generation) return;
        lastAcceptedNanos = nowNanos;
        if (!active) return;
        active = false;
        forgetPicture();
        synchronized (lock) {
            epoch++;
            units.clear();
            frames.clear(free::add);
            lock.notifyAll();
        }
    }

    void frame(int generation, long ptsMillis, boolean keyframe, byte[] data, long nowNanos) {
        if (!accepts(generation, nowNanos)) return;
        lastAcceptedNanos = nowNanos;
        if (data.length == 0) return;
        synchronized (lock) {
            units.offer(new VideoUnitQueue.Unit(ptsMillis, keyframe, data));
            lock.notifyAll();
        }
    }

    void heartbeat(int generation, long trackMillis, boolean paused, long nowNanos) {
        if (!accepts(generation, nowNanos)) return;
        lastAcceptedNanos = nowNanos;
        clock.heartbeat(trackMillis, paused, nowNanos);
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    long quietNanos(long nowNanos) {
        return nowNanos - lastAcceptedNanos;
    }

    private boolean accepts(int generation, long nowNanos) {
        if (started && generation == this.generation) return active;
        if (!started || nowNanos - lastAcceptedNanos > ADOPT_AFTER_NANOS) {
            start(generation, nowNanos);
            return true;
        }
        return false;
    }

    private void forgetPicture() {
        shown = null;
        shownPtsMillis = 0;
        presented = null;
        presentedRenderFrame = Long.MIN_VALUE;
    }

    ClientVideoReceivers.Frame present(long renderFrame) {
        if (renderFrame != presentedRenderFrame) {
            long now = System.nanoTime();
            if (!seen(now) && com.nachtraben.soundstage.RigStaging.fault("video-no-catchup")) {
                presentedRenderFrame = renderFrame;
                presented = presentNow();
                return presented;
            }
            boolean returning = !seen(now);
            if (returning) {
                forgetPicture();
                returnedNanos = now;
            }
            presentedRenderFrame = renderFrame;
            lastSeenNanos = now;
            if (returning) {
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
            presented = presentNow();
        }
        return presented;
    }

    boolean catchingUp(long nowNanos) {
        return active && returnedNanos != 0 && nowNanos - returnedNanos < NowPlayingCards.PICTURE_GRACE_NANOS;
    }

    private boolean seen(long nowNanos) {
        return com.nachtraben.soundstage.RigStaging.fault("video-decode-hidden")
                || nowNanos - lastSeenNanos < SEEN_WINDOW_NANOS;
    }

    private ClientVideoReceivers.Frame presentNow() {
        if (!active) return null;
        long now = System.nanoTime();
        clock.heard(heardMillis(), now);
        double clockMillis = clock.millisAt(now);
        Slot slot;
        long ptsMillis;
        synchronized (lock) {
            slot = frames.frameFor(clockMillis, free::add);
            ptsMillis = frames.shownPtsMillis();
            if (slot != null) lock.notifyAll();
        }
        shownPtsMillis = ptsMillis;
        if (slot == null) {
            if (shown != null && shown.ptsMillis() != shownPtsMillis) {
                shown = new ClientVideoReceivers.Frame(shown.texture(), shown.width(), shown.height(), shownPtsMillis);
            }
            return shown;
        }
        try {
            upload(slot, ptsMillis);
        } finally {
            synchronized (lock) {
                free.add(slot);
                lock.notifyAll();
            }
        }
        ClientVideoReceivers.PresentedListener listener = ClientVideoReceivers.presentedListener;
        if (listener != null) {
            try {
                listener.presented(pos, ptsMillis, System.nanoTime());
            } catch (RuntimeException e) {
                if (!listenerFailureReported) {
                    listenerFailureReported = true;
                    LOGGER.warn("Soundstage: the presented-frame listener threw; later failures are not logged", e);
                }
            }
        }
        return shown;
    }

    private double heardMillis() {
        ClientAudioReceiver audio = AudioDecoder.getReceiver(pos);
        if (audio == null) return Double.NaN;
        double audible = audio.audibleSequenceThisFrame();
        if (audible < 0) return Double.NaN;
        return audio.timeline().trackMillisAt(audible);
    }

    private void upload(Slot slot, long ptsMillis) {
        if (texture == null) {
            texture = new VideoTexture();
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        }
        if (com.nachtraben.soundstage.RigStaging.fault("swap-rb")) swapRedBlue(slot.pixels, slot.width * slot.height);
        texture.upload(slot.pixels, slot.width, slot.height);
        shown = new ClientVideoReceivers.Frame(textureId, slot.width, slot.height, ptsMillis);
        returnedNanos = 0;
    }

    private static void swapRedBlue(ByteBuffer rgba, int pixels) {
        for (int i = 0; i < pixels; i++) {
            int at = i * 4;
            byte red = rgba.get(at);
            rgba.put(at, rgba.get(at + 2));
            rgba.put(at + 2, red);
        }
    }

    void close() {
        synchronized (lock) {
            closed = true;
            units.clear();
            frames.clear(slot -> {});
            free.clear();
            lock.notifyAll();
        }
        active = false;
        forgetPicture();
        if (texture != null) {
            release(textureId, texture);
            texture = null;
        }
    }

    private static void release(ResourceLocation id, VideoTexture texture) {
        Runnable release = () -> {
            TextureManager manager = Minecraft.getInstance().getTextureManager();
            //? if >=1.21.11 {
            /*manager.release(id);
            *///?} else {
            if (manager.getTexture(id, null) == texture) manager.release(id);
            //?}
        };
        if (RenderSystem.isOnRenderThread()) release.run();
        //? if >=1.21.11 {
        /*else Minecraft.getInstance().execute(release);
        *///?} else {
        else RenderSystem.recordRenderCall(release::run);
        //?}
    }

    private void decodeLoop() {
        H264FrameDecoder decoder = null;
        int decoderEpoch = 0;
        boolean reported = false;
        boolean unseen = false;
        try {
            while (true) {
                VideoUnitQueue.Unit unit;
                Slot slot;
                int unitEpoch;
                synchronized (lock) {
                    while (true) {
                        if (closed) return;
                        long now = System.nanoTime();
                        double clockMillis = clock.millisAt(now);
                        frames.discardUnreachable(clockMillis, free::add);
                        long waitMillis;
                        if (!seen(now)) {
                            frames.clear(free::add);
                            units.keepFromLatestKeyframe(clockMillis);
                            unseen = true;
                            waitMillis = IDLE_WAIT_MILLIS;
                        } else if (frames.size() >= MAX_HELD || free.isEmpty() && slots >= POOL_SIZE) {
                            waitMillis = BUSY_WAIT_MILLIS;
                        } else {
                            unit = units.next(clockMillis);
                            if (unit != null) break;
                            double due = units.millisUntilDue(clockMillis);
                            if (units.isEmpty()) waitMillis = IDLE_WAIT_MILLIS;
                            else if (Double.isInfinite(due)) waitMillis = BUSY_WAIT_MILLIS;
                            else waitMillis = Math.max(1L, Math.min(BUSY_WAIT_MILLIS, (long) Math.ceil(due)));
                        }
                        lock.wait(waitMillis);
                    }
                    slot = free.pollFirst();
                    if (slot == null) {
                        slot = new Slot();
                        slots++;
                    }
                    unitEpoch = epoch;
                }
                if (decoder == null || unitEpoch != decoderEpoch || unseen) {
                    decoder = new H264FrameDecoder();
                    decoderEpoch = unitEpoch;
                    reported = false;
                    unseen = false;
                }
                decodedUnits.incrementAndGet();
                boolean kept = false;
                try {
                    String failure = null;
                    if (H264Headers.codedPixels(unit.data()) > MAX_CODED_PIXELS) {
                        failure = "its stream declares a picture larger than " + MAX_CODED_PIXELS + " pixels";
                    } else if (decodeInto(decoder, unit.data(), slot)) {
                        synchronized (lock) {
                            if (!closed && epoch == unitEpoch) kept = frames.add(unit.ptsMillis(), slot);
                        }
                    } else if (unit.keyframe()) {
                        failure = "a keyframe did not decode";
                    }
                    if (failure != null) {
                        synchronized (lock) {
                            if (epoch == unitEpoch) units.awaitKeyframe();
                        }
                        if (!reported) {
                            reported = true;
                            LOGGER.warn("Soundstage: video for receiver {} is not being shown: {}. "
                                            + "Later failures in this stream are not logged.",
                                    pos.toShortString(), failure);
                        }
                    }
                } finally {
                    if (!kept) {
                        synchronized (lock) {
                            free.add(slot);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
        } catch (Throwable t) {
            LOGGER.error("Soundstage: the video decoder for receiver {} stopped; its screens hold their last "
                    + "picture until the receiver is released", pos.toShortString(), t);
        }
    }

    private static boolean decodeInto(H264FrameDecoder decoder, byte[] data, Slot slot) {
        H264FrameDecoder.Picture picture = decoder.decode(data);
        if (picture == null) return false;
        int width = picture.width();
        int height = picture.height();
        if (width <= 0 || height <= 0 || (long) width * height > MAX_CODED_PIXELS) return false;
        try {
            slot.fit(width, height);
            picture.toRgba(slot.pixels);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final class Slot {
        ByteBuffer pixels;
        int width;
        int height;

        void fit(int width, int height) {
            int bytes = width * height * 4;
            if (pixels == null || pixels.capacity() < bytes) pixels = ByteBuffer.allocateDirect(bytes);
            this.width = width;
            this.height = height;
        }
    }
}
