package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.network.DisplayInfoS2CPacket;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ClientDisplayInfo {

    private ClientDisplayInfo() {}

    public record Info(int generation, String title, String author, @Nullable String artworkUrl,
                        long durationMillis, boolean stream, boolean pictureExpected) {}

    private static final Map<BlockPos, Info> receivers = new HashMap<>();

    public static void handle(DisplayInfoS2CPacket packet) {
        if (packet.clear) {
            receivers.remove(packet.receiverPos);
            return;
        }
        Info existing = receivers.get(packet.receiverPos);
        if (existing != null && existing.generation() >= packet.generation) return;
        receivers.put(packet.receiverPos, new Info(packet.generation, packet.title, packet.author,
                packet.artworkUrl, packet.durationMillis, packet.stream, packet.pictureExpected));
    }

    public static @Nullable Info get(BlockPos receiverPos) {
        return receivers.get(receiverPos);
    }

    public static void clear() {
        receivers.clear();
    }
}
