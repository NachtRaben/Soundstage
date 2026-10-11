package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.config.SoundstageAppearance;
import com.nachtraben.soundstage.core.SpectrumBands;
import com.nachtraben.soundstage.core.video.NowPlayingCardPainter;
import dev.armadeus.armalib.ui.ArtworkCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class NowPlayingCards {

    public static final long PICTURE_GRACE_NANOS = 3_000_000_000L;
    static final long IDLE_RELEASE_NANOS = 10_000_000_000L;
    static final int MAX_PAINTS_PER_SECOND = 30;
    static final long PAUSED_AFTER_NANOS = 500_000_000L;
    private static final double[] REST = new double[NowPlayingCardPainter.BARS];

    private static final Map<Key, Card> CARDS = new HashMap<>();
    private static final ExecutorService PAINTER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Soundstage-NowPlaying");
        thread.setDaemon(true);
        return thread;
    });
    private static int nextId;
    private static Font font;

    private NowPlayingCards() {
    }

    private record Key(BlockPos receiver, int aspectMilli) {}

    public static @Nullable ClientVideoReceivers.Frame frame(BlockPos receiverPos, int displayWidthCells,
                                                            int displayHeightCells) {
        ClientDisplayInfo.Info info = ClientDisplayInfo.get(receiverPos);
        if (info == null) return null;
        long now = System.nanoTime();
        double aspect = NowPlayingCardPainter.cardAspect(displayWidthCells, displayHeightCells);
        Card card = CARDS.computeIfAbsent(new Key(receiverPos, (int) Math.round(aspect * 1000)),
                key -> new Card(aspect));
        card.lastAskedNanos = now;
        if (card.generation != info.generation()) {
            card.generation = info.generation();
            card.infoSeenNanos = now;
            card.artworkApplied = false;
            card.trackDirty = true;
        }
        if (info.pictureExpected() && now - card.infoSeenNanos < PICTURE_GRACE_NANOS) return null;
        if (ClientVideoReceivers.catchingUp(receiverPos)) return null;

        long frame = ClientAudioReceiver.renderFrame();
        if (card.frameDone != frame) {
            card.frameDone = frame;
            int tallest = Math.max(card.tallestAsking, displayHeightCells);
            card.tallestAsking = 0;
            card.update(receiverPos, info, tallest, now);
        }
        card.tallestAsking = Math.max(card.tallestAsking, displayHeightCells);
        return card.uploaded
                ? new ClientVideoReceivers.Frame(card.textureId, card.width, card.height, Long.MIN_VALUE)
                : null;
    }

    public static void tick() {
        if (CARDS.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<Card> it = CARDS.values().iterator();
        while (it.hasNext()) {
            Card card = it.next();
            if (now - card.lastAskedNanos > IDLE_RELEASE_NANOS) {
                card.release();
                it.remove();
            }
        }
    }

    public static void clear() {
        for (Card card : CARDS.values()) card.release();
        CARDS.clear();
    }

    private static Font font() {
        if (font != null) return font;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(
                    ResourceLocation.fromNamespaceAndPath("soundstage", "font/noto_sans_medium.ttf"));
            if (resource.isPresent()) {
                try (var in = resource.get().open()) {
                    font = Font.createFont(Font.TRUETYPE_FONT, in);
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("Soundstage").warn(
                    "Soundstage: the now-playing card's font did not load ({}); using the platform's", e.toString());
        }
        if (font == null) font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return font;
    }

    private static final class Card {
        final ResourceLocation textureId =
                ResourceLocation.fromNamespaceAndPath("soundstage", "now_playing/" + nextId++);
        final double aspect;
        VideoTexture texture;
        int width, height;
        boolean uploaded;
        int tallestAsking;

        int generation = Integer.MIN_VALUE;
        long infoSeenNanos, lastAskedNanos, lastPaintNanos, lastSampleNanos;
        long frameDone = -1;
        boolean trackDirty, artworkApplied;
        BufferedImage artwork;

        final SpectrumBands.Leveller leveller = new SpectrumBands.Leveller(NowPlayingCardPainter.BARS);
        final SpectrumBands.Smoother smoother = new SpectrumBands.Smoother(NowPlayingCardPainter.BARS);
        final float[] mono = new float[SpectrumBands.FFT_SIZE];

        NowPlayingCardPainter painter;
        final AtomicBoolean painting = new AtomicBoolean();
        final AtomicReference<ByteBuffer> ready = new AtomicReference<>();
        final AtomicReference<ByteBuffer> spare = new AtomicReference<>();
        volatile boolean released;

        Card(double aspect) {
            this.aspect = aspect;
        }

        void update(BlockPos receiverPos, ClientDisplayInfo.Info info, int displayHeightCells, long now) {
            ByteBuffer painted = ready.getAndSet(null);
            if (painted != null) {
                if (texture == null) {
                    texture = VideoTexture.create();
                    Minecraft.getInstance().getTextureManager().register(textureId, texture);
                }
                if (painted.capacity() == width * height * 4) {
                    texture.upload(painted, width, height);
                    uploaded = true;
                }
                ByteBuffer old = spare.getAndSet(painted);
                if (old != null && old != painted) MemoryUtil.memFree(old);
            }

            if (!artworkApplied && info.artworkUrl() != null
                    && SoundstageAppearance.trackChangeToastArtwork()) {
                BufferedImage image = ArtworkCache.copyImage(info.artworkUrl());
                ArtworkCache.Art art = ArtworkCache.get(info.artworkUrl());
                if (image != null && art != null) {
                    var inside = art.content();
                    artwork = image.getSubimage(inside.x(), inside.y(), inside.width(), inside.height());
                    artworkApplied = true;
                    trackDirty = true;
                }
            }

            double dt = lastSampleNanos == 0 ? 0 : (now - lastSampleNanos) / 1e9;
            lastSampleNanos = now;
            double[] heights = REST;
            long position = 0;
            boolean paused = true;
            ClientAudioReceiver audio = AudioDecoder.getReceiver(receiverPos);
            if (audio != null) {
                paused = now - audio.lastPacketNanos > PAUSED_AFTER_NANOS;
                double audible = audio.audibleSequenceThisFrame();
                if (audible >= 0) {
                    double millis = audio.timeline().trackMillisAt(audible);
                    if (!Double.isNaN(millis)) position = (long) millis;
                    if (!paused && heardSamples(audio, (long) Math.floor(audible))) {
                        heights = leveller.heights(
                                SpectrumBands.bandsDb(mono, 48_000, NowPlayingCardPainter.BARS), dt);
                    }
                }
            }
            smoother.update(heights, dt);

            int wantHeight = Math.max(360, Math.min(1080, displayHeightCells * 180)) & ~1;
            int wantWidth = (int) Math.round(wantHeight * aspect) & ~1;
            if (now - lastPaintNanos < 1_000_000_000L / MAX_PAINTS_PER_SECOND) return;
            if (!painting.compareAndSet(false, true)) return;
            lastPaintNanos = now;

            boolean resize = wantHeight != height || wantWidth != width;
            if (resize) {
                height = wantHeight;
                width = wantWidth;
                uploaded = false;
            }
            int w = width, h = height;
            boolean newTrack = trackDirty || resize;
            trackDirty = false;
            String title = info.title(), author = info.author();
            BufferedImage art = artwork;
            double[] bars = smoother.heights().clone();
            long duration = info.durationMillis();
            boolean stream = info.stream();
            boolean isPaused = paused;
            long pos = position;
            PAINTER.execute(() -> {
                try {
                    if (painter == null || painter.width() != w || painter.height() != h) {
                        painter = new NowPlayingCardPainter(w, h, font());
                        painter.setTrack(title, author, art);
                    } else if (newTrack) {
                        painter.setTrack(title, author, art);
                    }
                    int[] argb = painter.paint(bars, pos, duration, stream, isPaused);
                    ByteBuffer out = spare.getAndSet(null);
                    if (out == null || out.capacity() != w * h * 4) {
                        if (out != null) MemoryUtil.memFree(out);
                        out = MemoryUtil.memAlloc(w * h * 4).order(ByteOrder.nativeOrder());
                    }
                    out.clear();
                    for (int p : argb) {
                        out.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
                    }
                    out.flip();
                    ByteBuffer previous = ready.getAndSet(out);
                    if (previous != null) MemoryUtil.memFree(previous);
                    if (released) {
                        ByteBuffer orphan = ready.getAndSet(null);
                        if (orphan != null) MemoryUtil.memFree(orphan);
                        ByteBuffer orphanSpare = spare.getAndSet(null);
                        if (orphanSpare != null) MemoryUtil.memFree(orphanSpare);
                    }
                } catch (RuntimeException e) {
                    LoggerFactory.getLogger("Soundstage").warn("Soundstage: a now-playing card failed to paint", e);
                } finally {
                    painting.set(false);
                }
            });
        }

        private boolean heardSamples(ClientAudioReceiver audio, long sequence) {
            int filled = 0;
            for (long seq = sequence; filled < mono.length && seq > sequence - 4; seq--) {
                byte[] pcm = audio.ring.get(seq);
                if (pcm == null) return false;
                ShortBuffer stereo = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                int frames = stereo.remaining() / 2;
                for (int i = frames - 1; i >= 0 && filled < mono.length; i--) {
                    float left = stereo.get(i * 2) / 32768f, right = stereo.get(i * 2 + 1) / 32768f;
                    mono[mono.length - 1 - filled] = (left + right) * 0.5f;
                    filled++;
                }
            }
            return filled == mono.length;
        }

        void release() {
            released = true;
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(textureId);
                texture = null;
            }
            uploaded = false;
            ByteBuffer r = ready.getAndSet(null);
            if (r != null) MemoryUtil.memFree(r);
            ByteBuffer s = spare.getAndSet(null);
            if (s != null) MemoryUtil.memFree(s);
        }
    }
}
