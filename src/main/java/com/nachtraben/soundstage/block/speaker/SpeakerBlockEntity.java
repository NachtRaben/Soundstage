package com.nachtraben.soundstage.block.speaker;

import dev.armadeus.armalib.platform.block.SaveView;

import com.nachtraben.soundstage.client.audio.AudioDecoder;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpeakerBlockEntity extends BlockEntity implements dev.armadeus.armalib.platform.block.LoadAware {
    public final transient com.nachtraben.soundstage.client.render.StaticMesh renderCache =
            new com.nachtraben.soundstage.client.render.StaticMesh();

    @Getter
    private String url = "";
    private boolean isPlaying = false;
    @Nullable
    private BlockPos receiverPos = null;
    @Getter
    private ChannelMode channelMode = ChannelMode.MONO;
    @Getter
    private float pitch = 0.0f;
    @Getter
    private float yawOffset = 0.0f;

    public static final int MIN_RANGE = 8;
    public static final int MAX_RANGE = 128;
    public static final int DEFAULT_RANGE = 32;

    @Getter
    private int range = DEFAULT_RANGE;

    public static final int RANGE_DELIVERY_MARGIN = 8;

    private static final int AUDIENCE_RECHECK_TICKS = 4;

    public static final int MIN_DELAY_MS = 0;
    public static final int MAX_DELAY_MS = com.nachtraben.soundstage.core.PcmRing.MAX_DELAY_FRAMES * 20;

    @Getter
    private int delayMs = 0;

    public static final int MIN_VOLUME_PERCENT = 0;
    public static final int MAX_VOLUME_PERCENT = 100;
    public static final int DEFAULT_VOLUME_PERCENT = 100;

    @Getter
    private int volumePercent = DEFAULT_VOLUME_PERCENT;

    private SpeakerFinish finish;

    private final long settingsPublicationId =
            com.nachtraben.soundstage.audio.SpeakerAudience.nextPublicationId();

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER_BLOCK_ENTITY.get(), pos, state);
        if (state.getBlock() instanceof AbstractSpeakerBlock asb) {
            this.channelMode = asb.getValidModes()[0];
        }
        this.finish = defaultFinish();
    }
    
    protected SpeakerBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (state.getBlock() instanceof AbstractSpeakerBlock asb) {
            this.channelMode = asb.getValidModes()[0];
        }
        this.finish = defaultFinish();
    }

    protected SpeakerFinish defaultFinish() {
        return SpeakerFinish.DEFAULT;
    }

    public SpeakerFinish getFinish() { return finish; }

    public void setFinish(SpeakerFinish finish) {
        if (this.finish.equals(finish)) return;
        this.finish = finish;
        setChanged();
    }

    //? if >=1.20.5 {
    @Override
    //? if >=1.21.11 {
    /*protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter input) {
    *///?} else {
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
    //?}
        super.applyImplicitComponents(input);
        SpeakerFinish stored = input.get(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_FINISH.get());
        var storedSettings = input.get(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_SETTINGS.get());
        if (storedSettings != null) applySettings(storedSettings);
        if (stored != null) {
            finish = stored;
        }
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_FINISH.get(), finish);
        components.set(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_SETTINGS.get(), settings());
    }

    @Override
    //? if >=1.21.11 {
    /*public void removeComponentsFromTag(net.minecraft.world.level.storage.ValueOutput tag) {
        super.removeComponentsFromTag(tag);
        tag.discard(SpeakerFinish.CASING_KEY);
        tag.discard(SpeakerFinish.GRILLE_KEY);
    *///?} else {
    public void removeComponentsFromTag(@NotNull CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(SpeakerFinish.CASING_KEY);
        tag.remove(SpeakerFinish.GRILLE_KEY);
    //?}
    }
    //?}

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
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(SaveView.writer(tag));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
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
        out.putString("url", url);
        out.putBoolean("isPlaying", isPlaying);
        if (receiverPos != null) {
            out.putLong("receiverPos", receiverPos.asLong());
        }
        out.putString("channelMode", channelMode.name());
        out.putFloat("pitch", pitch);
        out.putFloat("yawOffset", yawOffset);
        out.putInt("range", range);
        out.putInt("delayMs", delayMs);
        out.putInt("volumePercent", volumePercent);
        finish.save(out);
    }

    private void load(SaveView.Reader in) {
        url = in.getString("url", "");
        isPlaying = in.getBoolean("isPlaying", false);
        Long storedReceiver = in.getLongOrNull("receiverPos");
        receiverPos = storedReceiver == null ? null : BlockPos.of(storedReceiver);
        String storedMode = in.getStringOrNull("channelMode");
        if (storedMode != null) {
            try {
                channelMode = ChannelMode.valueOf(storedMode);
            } catch (Exception e) {
                channelMode = ChannelMode.MONO;
            }
        }
        pitch = boundedAngle(in.getFloat("pitch", 0f), MIN_PITCH, MAX_PITCH);
        yawOffset = boundedAngle(in.getFloat("yawOffset", 0f), MIN_YAW_OFFSET, MAX_YAW_OFFSET);
        range = Mth.clamp(in.getInt("range", DEFAULT_RANGE), MIN_RANGE, MAX_RANGE);
        delayMs = Mth.clamp(in.getInt("delayMs", 0), MIN_DELAY_MS, MAX_DELAY_MS);
        volumePercent = Mth.clamp(in.getInt("volumePercent", DEFAULT_VOLUME_PERCENT),
                MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT);
        finish = SpeakerFinish.load(in, defaultFinish());
    }

    public void setUrl(String url) { this.url = url; setChanged(); }

    public void setPlaying(boolean playing) { isPlaying = playing; setChanged(); }
    public boolean isPlaying() { return isPlaying; }

    transient boolean danceAnnouncing = false;

    void setPlayingSynced(boolean playing) {
        if (isPlaying == playing) return;
        isPlaying = playing;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state,
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
    @Nullable
    public BlockPos getReceiverPos() { return receiverPos; }
    public void setReceiverPos(@Nullable BlockPos pos) {
        this.receiverPos = pos;
        if (pos != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(serverLevel.getBlockState(worldPosition).getBlock()).getPath();
            com.nachtraben.soundstage.audio.SpeakerAudience.publishAcoustics(
                    serverLevel, worldPosition,
                    com.nachtraben.soundstage.core.SpeakerAcoustics.forBlockPath(path));
            com.nachtraben.soundstage.audio.SpeakerAudience.publishVolume(serverLevel, worldPosition,
                    settingsPublicationId, volumePercent);
            com.nachtraben.soundstage.audio.SpeakerAudience.publishChannel(serverLevel, worldPosition,
                    settingsPublicationId, channelMode);
        }
        setChanged();
    }
    public SpeakerSettings settings() {
        return new SpeakerSettings(channelMode, pitch, yawOffset, range, delayMs, volumePercent);
    }

    public void applySettings(SpeakerSettings settings) {
        this.channelMode = settings.channelMode();
        setPitch(settings.pitch());
        setYawOffset(settings.yawOffset());
        setRange(settings.range());
        setDelayMs(settings.delayMs());
        setVolumePercent(settings.volumePercent());
        setChanged();
    }

    public void setChannelMode(ChannelMode channelMode) {
        this.channelMode = channelMode;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.nachtraben.soundstage.audio.SpeakerAudience.publishChannel(serverLevel, worldPosition,
                    settingsPublicationId, channelMode);
        }
        setChanged();
    }
    public void setPitch(float pitch) { this.pitch = boundedAngle(pitch, MIN_PITCH, MAX_PITCH); setChanged(); }
    public void setYawOffset(float yawOffset) {
        this.yawOffset = boundedAngle(yawOffset, MIN_YAW_OFFSET, MAX_YAW_OFFSET);
        setChanged();
    }

    public static final float MIN_PITCH = -90.0f;
    public static final float MAX_PITCH = 90.0f;
    public static final float MIN_YAW_OFFSET = -180.0f;
    public static final float MAX_YAW_OFFSET = 180.0f;

    private static float boundedAngle(float value, float min, float max) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : 0.0f;
    }
    public void setRange(int range) { this.range = Mth.clamp(range, MIN_RANGE, MAX_RANGE); setChanged(); }
    public void setDelayMs(int delayMs) { this.delayMs = Mth.clamp(delayMs, MIN_DELAY_MS, MAX_DELAY_MS); setChanged(); }
    public void setVolumePercent(int volumePercent) {
        int clamped = Mth.clamp(volumePercent, MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT);
        if (this.volumePercent == clamped) return;
        this.volumePercent = clamped;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.nachtraben.soundstage.audio.SpeakerAudience.publishVolume(serverLevel, worldPosition,
                    settingsPublicationId, clamped);
            com.nachtraben.soundstage.audio.SpeakerAudience.publishChannel(serverLevel, worldPosition,
                    settingsPublicationId, channelMode);
        }
        setChanged();
    }
    public int getDelayFrames() { return delayMs / 20; }

    public static void serverTick(@NotNull net.minecraft.world.level.Level level, @NotNull BlockPos pos,
                                  @NotNull SpeakerBlockEntity speaker) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (Math.floorMod(level.getGameTime() + pos.asLong(), AUDIENCE_RECHECK_TICKS) != 0) {
            return;
        }

        double reach = com.nachtraben.soundstage.RigStaging.fault("range-ignored")
                ? Double.MAX_VALUE : speaker.getRange() + RANGE_DELIVERY_MARGIN;
        double reachSqr = reach * reach;
        java.util.List<net.minecraft.server.level.ServerPlayer> listeners = new java.util.ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < reachSqr) {
                listeners.add(player);
            }
        }
        if (com.nachtraben.soundstage.RigStaging.fault("sticky-audience")) {
            listeners.addAll(com.nachtraben.soundstage.audio.SpeakerAudience.collect(serverLevel, java.util.List.of(pos)));
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(serverLevel.getBlockState(pos).getBlock()).getPath();
        com.nachtraben.soundstage.audio.SpeakerAudience.publish(serverLevel, pos, listeners,
                com.nachtraben.soundstage.core.SpeakerAcoustics.forBlockPath(path));
        com.nachtraben.soundstage.audio.SpeakerAudience.publishVolume(serverLevel, pos,
                speaker.settingsPublicationId, speaker.volumePercent);
        com.nachtraben.soundstage.audio.SpeakerAudience.publishChannel(serverLevel, pos,
                speaker.settingsPublicationId, speaker.channelMode);
        SpeakerDance.serverTick(serverLevel, pos, speaker);
    }

    public static void clientTick(@NotNull net.minecraft.world.level.Level level, @NotNull BlockPos pos,
                                  @NotNull SpeakerBlockEntity speaker) {
        if (!level.isClientSide()) {
            return;
        }
        SpeakerDance.clientTick(level, pos, speaker);
    }

    @Override
    public void onLoad() {
        //? if neoforge
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(getBlockState().getBlock()).getPath();
            com.nachtraben.soundstage.audio.SpeakerAudience.publishAcoustics(serverLevel, worldPosition,
                    com.nachtraben.soundstage.core.SpeakerAcoustics.forBlockPath(path));
            com.nachtraben.soundstage.audio.SpeakerAudience.publishVolume(serverLevel, worldPosition,
                    settingsPublicationId, volumePercent);
            com.nachtraben.soundstage.audio.SpeakerAudience.publishChannel(serverLevel, worldPosition,
                    settingsPublicationId, channelMode);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            AudioDecoder.removeSpeaker(getBlockPos());
            renderCache.close();
        }
        if (level != null && !level.isClientSide()) {
            com.nachtraben.soundstage.audio.SpeakerAudience.forget(level, getBlockPos());
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(getBlockState().getBlock()).getPath();
            com.nachtraben.soundstage.audio.SpeakerAudience.forgetAcoustics(level, getBlockPos(),
                    com.nachtraben.soundstage.core.SpeakerAcoustics.forBlockPath(path));
            com.nachtraben.soundstage.audio.SpeakerAudience.forgetVolume(
                    level, getBlockPos(), settingsPublicationId);
            com.nachtraben.soundstage.audio.SpeakerAudience.forgetChannel(
                    level, getBlockPos(), settingsPublicationId);
        }
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    //? if >=1.20.5 {
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
    //?} else {
    /*public @NotNull CompoundTag getUpdateTag() {
    *///?}
        //? if >=1.21.11 {
        /*CompoundTag tag = saveCustomOnly(registries);
        *///?} else if >=1.20.5 {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        //?} else {
        /*CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        *///?}
        return tag;
    }

    //? if >=1.21.11 {
    /*@Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) AbstractSpeakerBlock.onSpeakerRemoved(level, pos);
    }
    *///?}
}
