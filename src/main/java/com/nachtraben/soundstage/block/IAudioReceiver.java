package com.nachtraben.soundstage.block;

import net.minecraft.core.BlockPos;
import java.util.List;

public interface IAudioReceiver {
    int MIN_VOLUME = 0;
    int MAX_VOLUME = 200;
    int LAVAPLAYER_MAX_VOLUME = 100;
    int DEFAULT_VOLUME = 50;

    String getCurrentTrackData();
    void setCurrentTrackData(String data);
    long getCurrentTrackPosition();
    void setCurrentTrackPosition(long position);
    List<String> getQueueData();
    void setQueueData(List<String> data);
    boolean isPlaying();
    void setPlaying(boolean playing);
    int getVolume();
    void setVolume(int volume);
    int getLoopMode();
    void setLoopMode(int mode);
    boolean isShuffle();
    void setShuffle(boolean shuffle);
    List<BlockPos> getSpeakers();

    boolean isLocked();

    void setLocked(boolean locked);

    default boolean isPictureEnabled() {
        return true;
    }

    default void setPictureEnabled(boolean pictureEnabled) {
    }

    boolean isLevelling();

    void setLevelling(boolean levelling);

    @org.jetbrains.annotations.Nullable
    java.util.UUID getOwnerUUID();

    void setOwnerUUID(@org.jetbrains.annotations.Nullable java.util.UUID uuid);

    default boolean mayControl(@org.jetbrains.annotations.Nullable java.util.UUID playerId, boolean operator) {
        if (com.nachtraben.soundstage.RigStaging.fault("lock-ignored")) return true;
        if (com.nachtraben.soundstage.RigStaging.fault("never-obeys")) return false;
        if (!isLocked() || operator) return true;
        java.util.UUID owner = getOwnerUUID();
        return owner != null && owner.equals(playerId);
    }

    default boolean mayControl(net.minecraft.world.entity.player.Player player) {
        return mayControl(player.getUUID(), operator(player));
    }

    static boolean operator(net.minecraft.world.entity.player.Player player) {
        if (com.nachtraben.soundstage.RigStaging.fault("always-op")) return true;
        if (com.nachtraben.soundstage.RigStaging.fault("never-op")) return false;
        return dev.armadeus.armalib.platform.Operators.isOperator(player);
    }

    default boolean mayToggleLock(@org.jetbrains.annotations.Nullable java.util.UUID playerId, boolean operator) {
        if (com.nachtraben.soundstage.RigStaging.fault("lock-ignored")) return true;
        java.util.UUID owner = getOwnerUUID();
        return operator || (owner != null && owner.equals(playerId));
    }

    default boolean mayToggleLock(net.minecraft.world.entity.player.Player player) {
        return mayToggleLock(player.getUUID(), operator(player));
    }
}
