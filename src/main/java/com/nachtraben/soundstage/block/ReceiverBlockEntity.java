package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.audio.AudioManagerState;
import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.audio.TrackScheduler;
import com.nachtraben.soundstage.audio.video.ScreenDisplays;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.client.render.StaticMesh;
import com.nachtraben.soundstage.core.video.AudioChoice;
import com.nachtraben.soundstage.core.video.PictureStepMemory;
import com.nachtraben.soundstage.core.video.SubtitleChoice;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import dev.armadeus.armalib.platform.block.SaveView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReceiverBlockEntity extends BlockEntity implements IAudioReceiver {

    public final transient StaticMesh renderCache =
            new StaticMesh();
    private String url = "";
    private boolean isPlaying = false;
    private int volume = IAudioReceiver.DEFAULT_VOLUME;
    private int loopMode = 0;
    private boolean shuffle = false;
    private String currentTrackData = "";
    private long currentTrackPosition = 0;
    private final List<String> queueData = new ArrayList<>();
    private boolean stateRestored = false;
    private final List<BlockPos> speakers = new CopyOnWriteArrayList<>();
    private final List<BlockPos> screens = new CopyOnWriteArrayList<>();
    private UUID ownerUUID = null;
    private boolean isLocked = false;
    private boolean pictureEnabled = true;
    private boolean levelling = false;
    private SpeakerFinish finish =
            SpeakerFinish.DEFAULT;

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECEIVER_BLOCK_ENTITY.get(), pos, state);
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
        finish.save(out);
        out.putString("url", url);
        out.putBoolean("isPlaying", isPlaying);
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
        out.putBoolean("pictureEnabled", pictureEnabled);
        out.putBoolean("levelLoudness", levelling);
        AudioLanguageField.save(out, audioLanguage);

        long[] speakerPosLongs = new long[speakers.size()];
        for (int i = 0; i < speakers.size(); i++) {
            speakerPosLongs[i] = speakers.get(i).asLong();
        }
        out.putLongArray("speakers", speakerPosLongs);

        long[] screenPosLongs = new long[screens.size()];
        for (int i = 0; i < screens.size(); i++) {
            screenPosLongs[i] = screens.get(i).asLong();
        }
        out.putLongArray("screens", screenPosLongs);
    }

    private void load(SaveView.Reader in) {
        finish = SpeakerFinish.load(in,
                SpeakerFinish.DEFAULT);
        url = in.getString("url", "");
        isPlaying = in.getBoolean("isPlaying", false);
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
        pictureEnabled = in.getBoolean("pictureEnabled", true);
        levelling = in.getBoolean("levelLoudness", false);
        audioLanguage = AudioLanguageField.load(in);

        speakers.clear();
        for (long pos : in.getLongArray("speakers")) {
            speakers.add(BlockPos.of(pos));
        }

        screens.clear();
        for (long pos : in.getLongArray("screens")) {
            screens.add(BlockPos.of(pos));
        }
    }

    public String getUrl() { return url; }
    public void setUrl(String url) {
        if (Objects.equals(this.url, url)) return;
        this.url = url; setChanged(); syncToClients();
    }
    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing; setChanged(); syncToClients();
    }
    public int getVolume() { return volume; }
    public void setVolume(int volume) {
        int clamped = Mth.clamp(
                volume, IAudioReceiver.MIN_VOLUME, IAudioReceiver.MAX_VOLUME);
        if (this.volume == clamped) return;
        this.volume = clamped; setChanged(); syncToClients();
    }
    public int getLoopMode() { return loopMode; }
    public void setLoopMode(int loopMode) {
        if (this.loopMode == loopMode) return;
        this.loopMode = loopMode; setChanged(); syncToClients();
    }
    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean shuffle) {
        if (this.shuffle == shuffle) return;
        this.shuffle = shuffle; setChanged(); syncToClients();
    }
    public String getCurrentTrackData() { return currentTrackData; }
    public void setCurrentTrackData(String currentTrackData) { this.currentTrackData = currentTrackData; setChanged(); }
    public long getCurrentTrackPosition() { return currentTrackPosition; }
    public void setCurrentTrackPosition(long currentTrackPosition) {
        this.currentTrackPosition = currentTrackPosition;
        setChanged();
    }
    public List<String> getQueueData() { return queueData; }
    public void setQueueData(List<String> data) { this.queueData.clear(); this.queueData.addAll(data); setChanged(); }
    public List<BlockPos> getSpeakers() { return speakers; }
    public List<BlockPos> getScreens() { return screens; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; setChanged(); }
    public boolean isLocked() { return isLocked; }
    private final transient SubtitleChoice.Memory subtitleChoices =
            new SubtitleChoice.Memory();

    public SubtitleChoice.Memory subtitleChoices() { return subtitleChoices; }

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

    private final transient PictureStepMemory pictureSteps =
            new PictureStepMemory();

    public PictureStepMemory pictureSteps() { return pictureSteps; }

    private transient volatile String controllerLanguage;

    public String controllerLanguage() { return controllerLanguage; }

    public void setControllerLanguage(String language) { this.controllerLanguage = language; }

    public boolean isPictureEnabled() { return pictureEnabled; }
    public void setPictureEnabled(boolean pictureEnabled) {
        if (this.pictureEnabled == pictureEnabled) return;
        this.pictureEnabled = pictureEnabled; setChanged(); syncToClients();
    }
    public boolean isLevelling() { return levelling; }
    public void setLevelling(boolean levelling) {
        if (this.levelling == levelling) return;
        this.levelling = levelling; setChanged(); syncToClients();
    }

    public SpeakerFinish getFinish() { return finish; }

    public void setFinish(SpeakerFinish finish) {
        if (this.finish.equals(finish)) return;
        this.finish = finish;
        setChanged();
        syncToClients();
    }

    //? if >=1.20.5 {
    @Override
    //? if >=1.21.11 {
    /*protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter input) {
    *///?} else {
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
    //?}
        super.applyImplicitComponents(input);
        var stored = input.get(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_FINISH.get());
        if (stored != null) {
            finish = stored;
        }
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_FINISH.get(), finish);
    }

    @Override
    //? if >=1.21.11 {
    /*public void removeComponentsFromTag(net.minecraft.world.level.storage.ValueOutput tag) {
        super.removeComponentsFromTag(tag);
        tag.discard("casing");
        tag.discard("grille");
    *///?} else {
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("casing");
        tag.remove("grille");
    //?}
    }
    //?}
    public void setLocked(boolean locked) {
        if (this.isLocked == locked) return;
        this.isLocked = locked; setChanged(); syncToClients();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    //? if >=1.20.5 {
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    //?} else {
    /*public CompoundTag getUpdateTag() {
    *///?}
        CompoundTag tag = new CompoundTag();
        SaveView.Writer out = SaveView.writer(tag);
        out.putString("url", url);
        out.putBoolean("isPlaying", isPlaying);
        out.putInt("volume", volume);
        out.putInt("loopMode", loopMode);
        out.putBoolean("shuffle", shuffle);
        out.putBoolean("isLocked", isLocked);
        out.putBoolean("pictureEnabled", pictureEnabled);
        out.putBoolean("levelLoudness", levelling);
        AudioLanguageField.save(out, audioLanguage);
        finish.save(out);
        if (ownerUUID != null) {
            out.putUuid("ownerUUID", ownerUUID);
        }
        long[] speakerPosLongs = new long[speakers.size()];
        for (int i = 0; i < speakers.size(); i++) {
            speakerPosLongs[i] = speakers.get(i).asLong();
        }
        out.putLongArray("speakers", speakerPosLongs);

        long[] screenPosLongs = new long[screens.size()];
        for (int i = 0; i < screens.size(); i++) {
            screenPosLongs[i] = screens.get(i).asLong();
        }
        out.putLongArray("screens", screenPosLongs);
        return tag;
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public static final int METER_AUDIENCE_RANGE = 64;

    private void publishMeterAudience(ServerLevel level, BlockPos pos) {
        double reachSqr = (double) METER_AUDIENCE_RANGE * METER_AUDIENCE_RANGE;
        List<ServerPlayer> listeners = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < reachSqr) {
                listeners.add(player);
            }
        }
        SpeakerAudience.publish(level, pos, listeners);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide()) {
            if (level.getGameTime() % 4 == 0) {
                publishMeterAudience((ServerLevel) level, pos);
            }

            if (level.getGameTime() % 200 == 0) {
                SpeakerAudience.pruneExpired(level.getGameTime());
                ScreenDisplays.pruneExpired(level.getGameTime());
            }

            if (!stateRestored) {
                stateRestored = true;
                if (!currentTrackData.isEmpty() || !queueData.isEmpty()) {
                    AudioManager.get().getOrCreatePlayer(pos, (ServerLevel) level);
                } else if (isPlaying) {
                    setPlaying(false);
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

            if (level.getGameTime() % 40 == 0) {
                boolean changed = false;
                List<BlockPos> toRemove = new ArrayList<>();
                for (BlockPos sp : speakers) {
                    if (!level.isLoaded(sp)) continue;
                    if (!(level.getBlockEntity(sp) instanceof SpeakerBlockEntity speaker)
                            || !pos.equals(speaker.getReceiverPos())) {
                        toRemove.add(sp);
                    }
                }
                if (!toRemove.isEmpty()) {
                    speakers.removeAll(toRemove);
                    changed = true;
                }

                List<BlockPos> screensToRemove = new ArrayList<>();
                for (BlockPos sp : screens) {
                    if (!level.isLoaded(sp)) continue;
                    if (!(level.getBlockEntity(sp) instanceof ScreenBlockEntity screen)
                            || !pos.equals(screen.getReceiverPos())) {
                        screensToRemove.add(sp);
                    }
                }
                if (!screensToRemove.isEmpty()) {
                    screens.removeAll(screensToRemove);
                    changed = true;
                }

                if (changed) {
                    setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            AudioDecoder.removeReceiver(getBlockPos());
            renderCache.close();
        }
        if (level != null && !level.isClientSide()) {
            SpeakerAudience.forget(level, getBlockPos());
            AudioManagerState
                    .removePlayer(getBlockPos(), (ServerLevel) level);
        }
    }

    //? if >=1.21.11 {
    /*@Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) ReceiverBlock.onReceiverRemoved(level, pos);
    }
    *///?}
}
