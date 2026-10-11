package com.nachtraben.soundstage.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class AudioManagerState {

    private AudioManagerState() {}

    private static volatile boolean created;

    static void markCreated() {
        created = true;
    }

    public static boolean created() {
        return created;
    }

    @Nullable
    public static TrackScheduler scheduler(BlockPos pos, ServerLevel level) {
        return created ? AudioManager.get().getScheduler(pos, level) : null;
    }

    public static void removePlayer(BlockPos pos, ServerLevel level) {
        if (created) AudioManager.get().removePlayer(pos, level);
    }
}
