package com.nachtraben.soundstage.client.video;

import com.nachtraben.soundstage.network.SubtitleOptionsS2CPacket;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ClientSubtitleOptions {

    private ClientSubtitleOptions() {}

    private static final Map<BlockPos, SubtitleOptionsS2CPacket> receivers = new HashMap<>();

    public static void handle(SubtitleOptionsS2CPacket packet) {
        if (packet.options().isEmpty()) receivers.remove(packet.pos());
        else receivers.put(packet.pos(), packet);
    }

    public static @Nullable SubtitleOptionsS2CPacket get(@Nullable BlockPos receiverPos) {
        return receiverPos == null ? null : receivers.get(receiverPos);
    }

    public static void clear() {
        receivers.clear();
    }
}
