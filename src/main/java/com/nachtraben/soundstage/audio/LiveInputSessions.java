package com.nachtraben.soundstage.audio;

import com.nachtraben.soundstage.network.LiveInputStateS2CPacket;
import dev.armadeus.armalib.platform.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveInputSessions {

    private static final Map<UUID, LiveInputSession> BY_SHARER = new ConcurrentHashMap<>();

    private LiveInputSessions() {
    }

    public static void offer(UUID sharer, byte[] opusPacket) {
        LiveInputSession session = BY_SHARER.get(sharer);
        if (session != null) {
            session.buffer().offer(opusPacket, System.nanoTime());
        }
    }

    static LiveInputSession begin(ServerPlayer sharer, ServerLevel level, BlockPos pos, TrackScheduler scheduler) {
        LiveInputSession session = new LiveInputSession(sharer, level, pos, scheduler);
        LiveInputSession previous = BY_SHARER.put(session.sharer(), session);
        if (previous != null) {
            previous.end(LiveInputSession.End.MOVED);
        }
        return session;
    }

    public static void stop(UUID sharer, LiveInputSession.End reason) {
        LiveInputSession session = BY_SHARER.get(sharer);
        if (session != null) {
            session.end(reason);
        }
    }

    static void endAllFor(TrackScheduler scheduler, LiveInputSession.End reason) {
        for (LiveInputSession session : List.copyOf(BY_SHARER.values())) {
            if (session.scheduler() == scheduler) {
                session.end(reason);
            }
        }
    }

    public static void forgetAll() {
        for (LiveInputSession session : List.copyOf(BY_SHARER.values())) {
            session.end(LiveInputSession.End.SERVER_STOPPING);
        }
        BY_SHARER.clear();
    }

    public static LiveInputSession of(UUID sharer) {
        return BY_SHARER.get(sharer);
    }

    public static int active() {
        return BY_SHARER.size();
    }

    static void forget(LiveInputSession session) {
        BY_SHARER.remove(session.sharer(), session);
    }

    static void tellSharerEnded(LiveInputSession session, LiveInputSession.End reason) {
        MinecraftServer server = session.level().getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(session.sharer());
            if (player == null) return;
            Network.sendToPlayer(player,
                    LiveInputStateS2CPacket.ended(session.pos(), session.id()));
            if (reason.messageKey != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(reason.messageKey)
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        });
    }
}
