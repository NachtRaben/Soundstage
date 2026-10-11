package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.network.VideoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ClientVideoReceivers {

    private ClientVideoReceivers() {}

    public record Frame(ResourceLocation texture, int width, int height, long ptsMillis) {}

    static final long IDLE_RELEASE_NANOS = 30_000_000_000L;

    static final int MAX_RECEIVERS = 32;

    private static final Map<BlockPos, ClientVideoReceiver> receivers = new HashMap<>();
    private static ClientVideoReceiver[] decoding = new ClientVideoReceiver[0];
    private static boolean refusalReported;

    public static void handle(VideoPacket packet) {
        if (Minecraft.getInstance().level == null) return;
        long now = System.nanoTime();
        ClientVideoReceiver receiver = receivers.get(packet.receiverPos);
        if (receiver == null) {
            if (packet.kind == VideoPacket.Kind.STOP) return;
            if (receivers.size() >= MAX_RECEIVERS) {
                if (!refusalReported) {
                    refusalReported = true;
                    LoggerFactory.getLogger("Soundstage").warn(
                            "Soundstage: video from more than {} receivers at once; the rest are not shown",
                            MAX_RECEIVERS);
                }
                return;
            }
            receiver = new ClientVideoReceiver(packet.receiverPos);
            receivers.put(receiver.pos, receiver);
            decoding = receivers.values().toArray(new ClientVideoReceiver[0]);
        }
        switch (packet.kind) {
            case START -> receiver.start(packet.generation, now);
            case FRAME -> receiver.frame(packet.generation, packet.millis, packet.flag, packet.data, now);
            case CLOCK -> receiver.heartbeat(packet.generation, packet.millis, packet.flag, now);
            case STOP -> receiver.stop(packet.generation, now);
        }
    }

    public static @Nullable Frame present(BlockPos receiverPos, boolean inView) {
        long renderFrame = ClientAudioReceiver.renderFrame();
        ClientVideoReceiver receiver = receivers.get(receiverPos);
        return receiver == null ? null : receiver.present(renderFrame, inView);
    }

    public static void endRenderFrame() {
        timeFrame(ClientAudioReceiver.renderFrame());
    }

    static long gameFrameNanos() {
        return gameFrameNanos;
    }

    static long quietFrameNanos() {
        return quietFrames >= SMOOTHING_FRAMES ? quietFrameNanos : 0;
    }

    private static final int SMOOTHING_FRAMES = 16;

    private static volatile long gameFrameNanos;
    private static volatile long quietFrameNanos;
    private static int gameFrames;
    private static volatile int quietFrames;
    private static long timedRenderFrame = Long.MIN_VALUE;
    private static long lastFrameNanos;

    private static void timeFrame(long renderFrame) {
        long now = System.nanoTime();
        long interval = now - lastFrameNanos;
        boolean consecutive = renderFrame == timedRenderFrame + 1;
        timedRenderFrame = renderFrame;
        lastFrameNanos = now;
        if (!consecutive || interval <= 0 || interval > 500_000_000L) return;
        gameFrames = Math.min(gameFrames + 1, SMOOTHING_FRAMES);
        gameFrameNanos = average(gameFrameNanos, interval, gameFrames);
        ClientVideoReceiver[] all = decoding;
        for (int i = 0; i < all.length; i++) {
            if (all[i].everyPicture) return;
        }
        int quiet = Math.min(quietFrames + 1, SMOOTHING_FRAMES);
        quietFrameNanos = average(quietFrameNanos, interval, quiet);
        quietFrames = quiet;
    }

    private static long average(long average, long interval, int frames) {
        return average + (interval - average) / frames;
    }

    public static boolean catchingUp(BlockPos receiverPos) {
        ClientVideoReceiver receiver = receivers.get(receiverPos);
        return receiver != null && receiver.catchingUp(System.nanoTime());
    }

    public static void tick() {
        if (receivers.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<ClientVideoReceiver> it = receivers.values().iterator();
        while (it.hasNext()) {
            ClientVideoReceiver receiver = it.next();
            receiver.tick(now);
            if (receiver.quietNanos(now) > IDLE_RELEASE_NANOS) {
                it.remove();
                receiver.close();
                decoding = receivers.values().toArray(new ClientVideoReceiver[0]);
            }
        }
    }

    public static void clear() {
        for (ClientVideoReceiver receiver : receivers.values()) receiver.close();
        receivers.clear();
        decoding = new ClientVideoReceiver[0];
        refusalReported = false;
        gameFrameNanos = 0;
        quietFrameNanos = 0;
        gameFrames = 0;
        quietFrames = 0;
    }
}
