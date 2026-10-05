package com.nachtraben.soundstage.block.speaker;

import com.nachtraben.soundstage.audio.TrackScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public final class SpeakerDance {

    private SpeakerDance() {}

    private static final int EVENT_INTERVAL_TICKS = 20;

    private static final double JUKEBOX_EVENT_RADIUS = 10.0;

    private static final double PARROT_RADIUS = 3.46;

    private static boolean wasAnnouncing(SpeakerBlockEntity speaker) {
        return speaker.danceAnnouncing;
    }

    static void serverTick(ServerLevel level, BlockPos pos, SpeakerBlockEntity speaker) {
        boolean playing = isProducingAudio(level, speaker);
        speaker.setPlayingSynced(playing);

        if (Math.floorMod(level.getGameTime() + pos.asLong(), EVENT_INTERVAL_TICKS) != 0) return;
        if (!playing && !wasAnnouncing(speaker)) return;
        speaker.danceAnnouncing = playing;

        BlockState state = level.getBlockState(pos);
        level.gameEvent(playing ? GameEvent.JUKEBOX_PLAY : GameEvent.JUKEBOX_STOP_PLAY, pos,
                GameEvent.Context.of(state));

        double radius = Math.max(speaker.getRange(), JUKEBOX_EVENT_RADIUS);
        for (Allay allay : level.getEntitiesOfClass(Allay.class, new AABB(pos).inflate(radius))) {
            allay.setJukeboxPlaying(pos, playing);
        }
    }

    static void clientTick(Level level, BlockPos pos, SpeakerBlockEntity speaker) {
        boolean playing = speaker.isPlaying();
        if (Math.floorMod(level.getGameTime() + pos.asLong(), EVENT_INTERVAL_TICKS) != 0) return;
        if (!playing && !wasAnnouncing(speaker)) return;
        speaker.danceAnnouncing = playing;

        double radius = Math.max(speaker.getRange(), PARROT_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos).inflate(radius))) {
            entity.setRecordPlayingNearby(pos, playing);
        }
    }

    private static boolean isProducingAudio(ServerLevel level, SpeakerBlockEntity speaker) {
        BlockPos receiverPos = speaker.getReceiverPos();
        if (receiverPos == null) return false;
        TrackScheduler scheduler = com.nachtraben.soundstage.audio.AudioManagerState.scheduler(receiverPos, level);
        return scheduler != null && scheduler.isActivelyPlaying();
    }
}
