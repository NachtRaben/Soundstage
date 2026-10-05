package com.nachtraben.soundstage.util;

import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.block.speaker.SpeakerSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class ItemData {
    private ItemData() {
    }

    //? if >=1.20.5 {
    public static SpeakerFinish finish(ItemStack stack) {
        return stack.get(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_FINISH.get());
    }

    public static void setFinish(ItemStack stack, SpeakerFinish finish) {
        stack.set(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_FINISH.get(), finish);
    }

    public static SpeakerSettings settings(ItemStack stack) {
        return stack.get(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_SETTINGS.get());
    }

    public static void setSettings(ItemStack stack, SpeakerSettings settings) {
        stack.set(com.nachtraben.soundstage.registry.ModComponents.SPEAKER_SETTINGS.get(), settings);
    }

    public static void setBlockState(ItemStack stack, BooleanProperty property, boolean value) {
        stack.set(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                                net.minecraft.world.item.component.BlockItemStateProperties.EMPTY)
                        .with(property, value));
    }

    public static CompoundTag customData(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }
    //?} else {
    /*private static final String ROOT = "soundstage";

    public static SpeakerFinish finish(ItemStack stack) {
        return read(stack, "finish", SpeakerFinish.CODEC);
    }

    public static void setFinish(ItemStack stack, SpeakerFinish finish) {
        write(stack, "finish", SpeakerFinish.CODEC, finish);
    }

    public static SpeakerSettings settings(ItemStack stack) {
        return read(stack, "settings", SpeakerSettings.CODEC);
    }

    public static void setSettings(ItemStack stack, SpeakerSettings settings) {
        write(stack, "settings", SpeakerSettings.CODEC, settings);
    }

    public static void setBlockState(ItemStack stack, BooleanProperty property, boolean value) {
        stack.getOrCreateTagElement(net.minecraft.world.item.BlockItem.BLOCK_STATE_TAG).putString(property.getName(), String.valueOf(value));
    }

    public static CompoundTag customData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.copy();
    }

    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag);
    }

    private static <T> T read(ItemStack stack, String key, com.mojang.serialization.Codec<T> codec) {
        CompoundTag root = stack.getTagElement(ROOT);
        if (root == null || !root.contains(key)) return null;
        return codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, root.get(key)).result().orElse(null);
    }

    private static <T> void write(ItemStack stack, String key, com.mojang.serialization.Codec<T> codec, T value) {
        codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, value).result()
                .ifPresent(tag -> stack.getOrCreateTagElement(ROOT).put(key, tag));
    }
    *///?}

    public static ItemStack withBlockEntityData(ItemStack stack, BlockEntityType<?> type, CompoundTag data) {
        //? if >=1.21.11 {
        /*stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                net.minecraft.world.item.component.TypedEntityData.of(type, data));
        *///?} else {
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, type, data);
        //?}
        return stack;
    }
}
