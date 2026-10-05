package com.nachtraben.soundstage.block.speaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nachtraben.soundstage.core.ChannelMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SpeakerSettings(ChannelMode channelMode, float pitch, float yawOffset,
                              int range, int delayMs, int volumePercent) {

    public static final Codec<SpeakerSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("channel_mode").forGetter(s -> s.channelMode().name()),
            Codec.FLOAT.fieldOf("pitch").forGetter(SpeakerSettings::pitch),
            Codec.FLOAT.fieldOf("yaw_offset").forGetter(SpeakerSettings::yawOffset),
            Codec.INT.fieldOf("range").forGetter(SpeakerSettings::range),
            Codec.INT.fieldOf("delay_ms").forGetter(SpeakerSettings::delayMs),
            Codec.INT.fieldOf("volume_percent").forGetter(SpeakerSettings::volumePercent)
    ).apply(instance, (mode, pitch, yaw, range, delay, volume) ->
            new SpeakerSettings(parseMode(mode), pitch, yaw, range, delay, volume)));

    public static final StreamCodec<ByteBuf, SpeakerSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, s -> s.channelMode().name(),
            ByteBufCodecs.FLOAT, SpeakerSettings::pitch,
            ByteBufCodecs.FLOAT, SpeakerSettings::yawOffset,
            ByteBufCodecs.VAR_INT, SpeakerSettings::range,
            ByteBufCodecs.VAR_INT, SpeakerSettings::delayMs,
            ByteBufCodecs.VAR_INT, SpeakerSettings::volumePercent,
            (mode, pitch, yaw, range, delay, volume) ->
                    new SpeakerSettings(parseMode(mode), pitch, yaw, range, delay, volume));

    private static ChannelMode parseMode(String name) {
        for (ChannelMode mode : ChannelMode.values()) {
            if (mode.name().equals(name)) return mode;
        }
        return ChannelMode.MONO;
    }
}
