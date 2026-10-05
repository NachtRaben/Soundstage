package com.nachtraben.soundstage.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.nachtraben.soundstage.SoundstageInit;

import java.util.List;
import java.util.ArrayList;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

public class SpeakerMetadataS2CPacket
implements CustomPacketPayload
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, "speaker_metadata_s2c");
    public static final Type<SpeakerMetadataS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SpeakerMetadataS2CPacket> STREAM_CODEC = StreamCodec.ofMember(
        SpeakerMetadataS2CPacket::write, SpeakerMetadataS2CPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final BlockPos pos;
    private final String trackTitle;
    private final String artist;
    private final String thumbnailUrl;
    private final boolean playing;
    private final int volume;
    private final List<String> queue;
    private final List<Long> queueIds;
    private final int loopMode;
    private final boolean shuffle;
    private final boolean locked;
    private final long position;
    private final long duration;
    private final boolean mediaFolderEnabled;
    private final boolean pictureEnabled;
    private final boolean levelling;

    public SpeakerMetadataS2CPacket(BlockPos pos, String trackTitle, String artist, String thumbnailUrl, boolean playing, int volume, List<String> queue, List<Long> queueIds, int loopMode, boolean shuffle, boolean locked, long position, long duration, boolean mediaFolderEnabled, boolean pictureEnabled, boolean levelling) {
        this.pos = pos;
        this.trackTitle = trackTitle;
        this.artist = artist;
        this.thumbnailUrl = thumbnailUrl;
        this.playing = playing;
        this.volume = volume;
        this.queue = queue == null ? new ArrayList<>() : queue;
        this.queueIds = queueIds == null ? new ArrayList<>() : queueIds;
        this.loopMode = loopMode;
        this.shuffle = shuffle;
        this.locked = locked;
        this.position = position;
        this.duration = duration;
        this.mediaFolderEnabled = mediaFolderEnabled;
        this.pictureEnabled = pictureEnabled;
        this.levelling = levelling;
    }

    public SpeakerMetadataS2CPacket(BlockPos pos, String trackTitle, String artist, String thumbnailUrl, boolean playing, int volume, List<String> queue, List<Long> queueIds, int loopMode, boolean shuffle, boolean locked, long position, long duration, boolean mediaFolderEnabled) {
        this(pos, trackTitle, artist, thumbnailUrl, playing, volume, queue, queueIds, loopMode, shuffle, locked, position, duration, mediaFolderEnabled, true, false);
    }

    public SpeakerMetadataS2CPacket(BlockPos pos, String trackTitle, String artist, String thumbnailUrl, boolean playing, int volume, List<String> queue, List<Long> queueIds, int loopMode, boolean shuffle, boolean locked, long position, long duration) {
        this(pos, trackTitle, artist, thumbnailUrl, playing, volume, queue, queueIds, loopMode, shuffle, locked, position, duration, false, true, false);
    }

    public SpeakerMetadataS2CPacket(BlockPos pos, String trackTitle, String artist, String thumbnailUrl, boolean playing, int volume, List<String> queue) {
        this(pos, trackTitle, artist, thumbnailUrl, playing, volume, queue, new ArrayList<>(), 0, false, false, 0, 0, false, true, false);
    }

    public SpeakerMetadataS2CPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.trackTitle = buf.readUtf();
        this.artist = buf.readUtf();
        this.thumbnailUrl = buf.readUtf();
        this.playing = buf.readBoolean();
        this.volume = buf.readInt();
        int size = buf.readInt();
        this.queue = new ArrayList<>(size);
        this.queueIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.queue.add(buf.readUtf());
            this.queueIds.add(buf.readLong());
        }
        this.loopMode = buf.readInt();
        this.shuffle = buf.readBoolean();
        this.locked = buf.readBoolean();
        this.position = buf.readLong();
        this.duration = buf.readLong();
        this.mediaFolderEnabled = buf.readBoolean();
        this.pictureEnabled = buf.readBoolean();
        this.levelling = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(trackTitle != null ? trackTitle : "");
        buf.writeUtf(artist != null ? artist : "");
        buf.writeUtf(thumbnailUrl != null ? thumbnailUrl : "");
        buf.writeBoolean(playing);
        buf.writeInt(volume);
        buf.writeInt(queue.size());
        for (int i = 0; i < queue.size(); i++) {
            buf.writeUtf(queue.get(i));
            buf.writeLong(i < queueIds.size() ? queueIds.get(i) : -1L);
        }
        buf.writeInt(loopMode);
        buf.writeBoolean(shuffle);
        buf.writeBoolean(locked);
        buf.writeLong(position);
        buf.writeLong(duration);
        buf.writeBoolean(mediaFolderEnabled);
        buf.writeBoolean(pictureEnabled);
        buf.writeBoolean(levelling);
    }

    public BlockPos getPos() { return pos; }
    public String getTrackTitle() { return trackTitle; }
    public String getArtist() { return artist; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public boolean isPlaying() { return playing; }
    public int getVolume() { return volume; }
    public List<String> getQueue() { return queue; }
    public List<Long> getQueueIds() { return queueIds; }
    public int getLoopMode() { return loopMode; }
    public boolean isShuffle() { return shuffle; }
    public boolean isLocked() { return locked; }
    public long getPosition() { return position; }
    public long getDuration() { return duration; }
    public boolean isMediaFolderEnabled() { return mediaFolderEnabled; }
    public boolean isPictureEnabled() { return pictureEnabled; }
    public boolean isLevelling() { return levelling; }
}
