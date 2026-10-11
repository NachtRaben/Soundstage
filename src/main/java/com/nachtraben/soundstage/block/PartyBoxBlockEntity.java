package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.audio.AudioManagerState;
import com.nachtraben.soundstage.audio.TrackScheduler;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import dev.armadeus.armalib.platform.block.SaveView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PartyBoxBlockEntity extends SpeakerBlockEntity implements IAudioReceiver {
    private String currentTrackData = "";
    private long currentTrackPosition = 0;
    private final List<String> queueData = new ArrayList<>();
    private int volume = IAudioReceiver.DEFAULT_VOLUME;
    private int loopMode = 0;
    private boolean shuffle = false;
    private boolean isLocked = false;
    private boolean levelling = false;
    private UUID ownerUUID = null;
    private boolean stateRestored = false;

    public PartyBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_BOX_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected SpeakerFinish defaultFinish() {
        return SpeakerFinish.DEFAULT
                .withGrille(Blocks.BLACK_WOOL);
    }

    //? if <1.21.11 {
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }
    //?}

    //? if >=1.21.11 {
    /*@Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        save(SaveView.writer(output));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        load(SaveView.reader(input));
    }
    *///?} else if >=1.20.5 {
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(SaveView.writer(tag));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(SaveView.reader(tag));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        save(SaveView.writer(tag));
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        load(SaveView.reader(tag));
    }
    *///?}

    private void save(SaveView.Writer out) {
        out.putInt("volume", volume);
        out.putInt("loopMode", loopMode);
        out.putBoolean("shuffle", shuffle);
        out.putString("currentTrackData", currentTrackData);
        out.putLong("currentTrackPosition", currentTrackPosition);
        out.putStringList("queueData", queueData);
        if (ownerUUID != null) {
            out.putUuid("ownerUUID", ownerUUID);
        }
        out.putBoolean("isLocked", isLocked);
        out.putBoolean("levelLoudness", levelling);
        AudioLanguageField.save(out, audioLanguage);
    }

    private void load(SaveView.Reader in) {
        volume = Mth.clamp(
                in.getInt("volume", IAudioReceiver.DEFAULT_VOLUME),
                IAudioReceiver.MIN_VOLUME, IAudioReceiver.MAX_VOLUME);
        loopMode = in.getInt("loopMode", 0);
        shuffle = in.getBoolean("shuffle", false);
        currentTrackData = in.getString("currentTrackData", "");
        currentTrackPosition = in.getLong("currentTrackPosition", 0L);
        queueData.clear();
        queueData.addAll(in.getStringList("queueData"));
        ownerUUID = in.getUuid("ownerUUID");
        isLocked = in.getBoolean("isLocked", false);
        levelling = in.getBoolean("levelLoudness", false);
        audioLanguage = AudioLanguageField.load(in);
    }

    @Override
    //? if >=1.20.5 {
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    //?} else {
    /*public net.minecraft.nbt.CompoundTag getUpdateTag() {
    *///?}
        //? if >=1.20.5 {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = super.getUpdateTag();
        *///?}
        tag.remove("queueData");
        tag.remove("currentTrackData");
        return tag;
    }

    public String getCurrentTrackData() { return currentTrackData; }
    public void setCurrentTrackData(String currentTrackData) { this.currentTrackData = currentTrackData; setChanged(); }

    @Override
    public BlockPos getReceiverPos() { return this.getBlockPos(); }

    public long getCurrentTrackPosition() { return currentTrackPosition; }
    public void setCurrentTrackPosition(long currentTrackPosition) {
        this.currentTrackPosition = currentTrackPosition;
        setChanged();
    }
    public List<String> getQueueData() { return queueData; }
    public void setQueueData(List<String> urls) { this.queueData.clear(); this.queueData.addAll(urls); setChanged(); }
    public int getVolume() { return volume; }
    public void setVolume(int volume) {
        int clamped = Mth.clamp(
                volume, IAudioReceiver.MIN_VOLUME, IAudioReceiver.MAX_VOLUME);
        if (this.volume == clamped) return;
        this.volume = clamped;
        setChanged();
        syncToClients();
    }
    public int getLoopMode() { return loopMode; }
    public void setLoopMode(int loopMode) {
        if (this.loopMode == loopMode) return;
        this.loopMode = loopMode;
        setChanged();
        syncToClients();
    }
    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean shuffle) {
        if (this.shuffle == shuffle) return;
        this.shuffle = shuffle;
        setChanged();
        syncToClients();
    }
    public boolean isLevelling() { return levelling; }

    private final transient SubtitleChoice.Memory audioChoices =
            new SubtitleChoice.Memory();

    public SubtitleChoice.Memory audioChoices() { return audioChoices; }

    private volatile String audioLanguage;

    public String audioLanguage() { return audioLanguage; }

    public void setAudioLanguage(String language) {
        String tag = AudioChoice.languageTag(language);
        if (Objects.equals(audioLanguage, tag)) return;
        audioLanguage = tag;
        setChanged();
        syncToClients();
    }
    public void setLevelling(boolean levelling) {
        if (this.levelling == levelling) return;
        this.levelling = levelling;
        setChanged();
        syncToClients();
    }

    @Override
    public void setPlaying(boolean playing) {
        if (isPlaying() == playing) return;
        super.setPlaying(playing);
        syncToClients();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }
    public List<BlockPos> getSpeakers() { return Collections.singletonList(getBlockPos()); }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            AudioManagerState
                    .removePlayer(getBlockPos(), (ServerLevel) level);
        }
    }
    public boolean isLocked() { return isLocked; }

    public void setLocked(boolean locked) {
        if (this.isLocked == locked) return;
        this.isLocked = locked;
        setChanged();
        syncToClients();
    }

    @Nullable
    public UUID getOwnerUUID() { return ownerUUID; }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PartyBoxBlockEntity be) {
        if (!level.isClientSide()) {
            if (!be.stateRestored) {
                be.stateRestored = true;
                if (!be.currentTrackData.isEmpty() || !be.queueData.isEmpty()) {
                    AudioManager.get().getOrCreatePlayer(pos, (ServerLevel) level);
                } else if (be.isPlaying()) {
                    be.setPlaying(false);
                }
            }
            if (level.getGameTime() % 20 == 0) {
                TrackScheduler scheduler = AudioManager.get()
                        .getScheduler(pos, (ServerLevel) level);
                if (scheduler != null) {
                    scheduler.syncMetadata();
                }
            }
            if (level.getGameTime() % 100 == 0) {
                TrackScheduler scheduler = AudioManager.get()
                        .getScheduler(pos, (ServerLevel) level);
                if (scheduler != null) {
                    scheduler.persistToReceiver();
                }
            }
        }
    }
}
