package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.client.audio.ClientAudioReceiver;
import com.nachtraben.soundstage.network.VideoPacket;
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

    @FunctionalInterface
    public interface PresentedListener {
        void presented(BlockPos receiverPos, long ptsMillis, long nanoTime);
    }

    public static volatile PresentedListener presentedListener;

    private static final long AV_OFFSET_FAULT_MILLIS = 300L;

    private static long clockOffsetMillis() {
        if (com.nachtraben.soundstage.RigStaging.fault("av-offset")) {
            LoggerFactory.getLogger("Soundstage").warn(
                    "Soundstage: red control — every screen's clock is shifted by {} ms (av-offset)", AV_OFFSET_FAULT_MILLIS);
            return AV_OFFSET_FAULT_MILLIS;
        }
        return 0L;
    }

    static final long IDLE_RELEASE_NANOS = 30_000_000_000L;

    static final int MAX_RECEIVERS = 32;

    private static final Map<BlockPos, ClientVideoReceiver> receivers = new HashMap<>();
    private static boolean refusalReported;

    public static void handle(VideoPacket packet) {
        if (net.minecraft.client.Minecraft.getInstance().level == null) return;
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
            receiver = new ClientVideoReceiver(packet.receiverPos, clockOffsetMillis());
            receivers.put(receiver.pos, receiver);
        }
        if (packet.kind == VideoPacket.Kind.FRAME && com.nachtraben.soundstage.RigStaging.fault("drop-frames")) return;
        switch (packet.kind) {
            case START -> receiver.start(packet.generation, now);
            case FRAME -> receiver.frame(packet.generation, packet.millis, packet.flag, packet.data, now);
            case CLOCK -> receiver.heartbeat(packet.generation, packet.millis, packet.flag, now);
            case STOP -> receiver.stop(packet.generation, now);
        }
    }

    public static @Nullable Frame present(BlockPos receiverPos) {
        ClientVideoReceiver receiver = receivers.get(receiverPos);
        return receiver == null ? null : receiver.present(ClientAudioReceiver.renderFrame());
    }

    public static boolean catchingUp(BlockPos receiverPos) {
        ClientVideoReceiver receiver = receivers.get(receiverPos);
        return receiver != null && receiver.catchingUp(System.nanoTime());
    }

    public static long decodedUnits(BlockPos receiverPos) {
        ClientVideoReceiver receiver = receivers.get(receiverPos);
        return receiver == null ? -1 : receiver.decodedUnitCount();
    }

    public static void tick() {
        if (receivers.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<ClientVideoReceiver> it = receivers.values().iterator();
        while (it.hasNext()) {
            ClientVideoReceiver receiver = it.next();
            if (receiver.quietNanos(now) > IDLE_RELEASE_NANOS) {
                it.remove();
                receiver.close();
            }
        }
    }

    public static void clear() {
        for (ClientVideoReceiver receiver : receivers.values()) receiver.close();
        receivers.clear();
        refusalReported = false;
    }
}
