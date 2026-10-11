package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.core.video.ReceiverCues;
import com.nachtraben.soundstage.network.SubtitlePacket;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClientSubtitles {

    private ClientSubtitles() {}

    private static final Map<BlockPos, ReceiverCues> receivers = new HashMap<>();
    private static final Set<BlockPos> announced = new HashSet<>();

    public static void handle(SubtitlePacket packet) {
        receivers.computeIfAbsent(packet.receiverPos, pos -> new ReceiverCues())
                .apply(packet.generation, packet.reset, packet.cues);
        if (!packet.reset && !packet.cues.isEmpty() && announced.add(packet.receiverPos)) {
            LoggerFactory.getLogger("Soundstage").info("Soundstage: subtitles arriving for receiver {} ({} cues).",
                    packet.receiverPos.toShortString(), packet.cues.size());
        }
    }

    public static @Nullable String activeText(BlockPos receiverPos, long trackMillis) {
        ReceiverCues cues = receivers.get(receiverPos);
        return cues == null ? null : cues.activeText(trackMillis);
    }

    public static @Nullable Long startAt(BlockPos receiverPos, long trackMillis) {
        ReceiverCues cues = receivers.get(receiverPos);
        return cues == null ? null : cues.startAt(trackMillis);
    }

    public static @Nullable Integer generation(BlockPos receiverPos) {
        var cues = receivers.get(receiverPos);
        return cues == null ? null : cues.generation();
    }

    public static void clear() {
        receivers.clear();
    }
}
