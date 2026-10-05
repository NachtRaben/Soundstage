package com.nachtraben.soundstage.block.speaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import dev.armadeus.armalib.platform.block.SaveView;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public record SpeakerFinish(ResourceLocation casing, ResourceLocation grille) {

    public static final SpeakerFinish DEFAULT = new SpeakerFinish(
            BuiltInRegistries.BLOCK.getKey(Blocks.STRIPPED_DARK_OAK_WOOD),
            BuiltInRegistries.BLOCK.getKey(Blocks.GRAY_WOOL));

    public static final Codec<SpeakerFinish> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("casing").forGetter(SpeakerFinish::casing),
            ResourceLocation.CODEC.fieldOf("grille").forGetter(SpeakerFinish::grille)
    ).apply(instance, SpeakerFinish::new));

    public static final StreamCodec<ByteBuf, SpeakerFinish> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SpeakerFinish::casing,
            ResourceLocation.STREAM_CODEC, SpeakerFinish::grille,
            SpeakerFinish::new);

    public static final TagKey<Block> CASING_MATERIALS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(
                    com.nachtraben.soundstage.SoundstageInit.MODID, "speaker_casing"));

    public static boolean isCasingMaterial(Block block) {
        return block.builtInRegistryHolder().is(CASING_MATERIALS);
    }

    public static boolean isGrilleMaterial(Block block) {
        return block.builtInRegistryHolder().is(BlockTags.WOOL);
    }

    public SpeakerFinish withCasing(Block block) {
        return new SpeakerFinish(BuiltInRegistries.BLOCK.getKey(block), grille);
    }

    public SpeakerFinish withGrille(Block block) {
        return new SpeakerFinish(casing, BuiltInRegistries.BLOCK.getKey(block));
    }

    public Block casingBlock() {
        return resolve(casing, Blocks.STRIPPED_DARK_OAK_WOOD);
    }

    public boolean isTransparent() {
        return !casingBlock().defaultBlockState().canOcclude();
    }

    public Block grilleBlock() {
        return resolve(grille, Blocks.GRAY_WOOL);
    }

    private static Block resolve(ResourceLocation id, Block fallback) {
        //? if >=1.21.11 {
        /*Block block = BuiltInRegistries.BLOCK.getValue(id);
        *///?} else {
        Block block = BuiltInRegistries.BLOCK.get(id);
        //?}
        return block == Blocks.AIR ? fallback : block;
    }

    public void save(SaveView.Writer tag) {
        tag.putString(CASING_KEY, casing.toString());
        tag.putString(GRILLE_KEY, grille.toString());
    }

    public static SpeakerFinish load(SaveView.Reader tag, SpeakerFinish fallback) {
        return new SpeakerFinish(
                parse(tag, CASING_KEY, fallback.casing),
                parse(tag, GRILLE_KEY, fallback.grille));
    }

    static final String CASING_KEY = "casing";
    static final String GRILLE_KEY = "grille";

    private static ResourceLocation parse(SaveView.Reader tag, String key, ResourceLocation fallback) {
        String stored = tag.getStringOrNull(key);
        @Nullable ResourceLocation parsed = stored != null ? ResourceLocation.tryParse(stored) : null;
        return parsed == null ? fallback : parsed;
    }
}
