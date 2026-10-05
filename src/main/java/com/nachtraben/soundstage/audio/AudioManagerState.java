package com.nachtraben.soundstage.audio;

public final class AudioManagerState {

    private AudioManagerState() {}

    private static volatile boolean created;

    static void markCreated() {
        created = true;
    }

    public static boolean created() {
        return created;
    }

    @org.jetbrains.annotations.Nullable
    public static TrackScheduler scheduler(net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerLevel level) {
        return created ? AudioManager.get().getScheduler(pos, level) : null;
    }

    public static void removePlayer(net.minecraft.core.BlockPos pos, net.minecraft.server.level.ServerLevel level) {
        if (created) AudioManager.get().removePlayer(pos, level);
    }
}
