package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.core.video.SubtitleChoice;
import dev.armadeus.armalib.platform.Operators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface IAudioReceiver {
    int MIN_VOLUME = 0;
    int MAX_VOLUME = 200;
    int LAVAPLAYER_MAX_VOLUME = 100;
    int DEFAULT_VOLUME = 50;
    int CONTROL_RANGE = 96;

    static boolean withinControlRange(Entity player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= (double) CONTROL_RANGE * CONTROL_RANGE;
    }

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

    SubtitleChoice.Memory audioChoices();

    @Nullable
    String audioLanguage();

    void setAudioLanguage(@Nullable String language);

    void setLevelling(boolean levelling);

    @Nullable
    UUID getOwnerUUID();

    void setOwnerUUID(@Nullable UUID uuid);

    default boolean mayControl(@Nullable UUID playerId, boolean operator) {
        if (!isLocked() || operator) return true;
        UUID owner = getOwnerUUID();
        return owner != null && owner.equals(playerId);
    }

    default boolean mayControl(Player player) {
        return mayControl(player.getUUID(), operator(player));
    }

    static boolean operator(Player player) {
        return Operators.isOperator(player);
    }

    default boolean mayToggleLock(@Nullable UUID playerId, boolean operator) {
        UUID owner = getOwnerUUID();
        return operator || (owner != null && owner.equals(playerId));
    }

    default boolean mayToggleLock(Player player) {
        return mayToggleLock(player.getUUID(), operator(player));
    }
}
