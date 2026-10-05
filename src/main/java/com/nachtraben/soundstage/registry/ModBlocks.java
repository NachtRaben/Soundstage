package com.nachtraben.soundstage.registry;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.ReceiverBlock;
import com.nachtraben.soundstage.block.PartyBoxBlock;
import com.nachtraben.soundstage.block.ScreenBlock;
import com.nachtraben.soundstage.block.speaker.CenterSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SatelliteSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SubwooferBlock;
import dev.armadeus.armalib.platform.registry.Registrar;
import dev.armadeus.armalib.platform.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static final Registrar<Block> BLOCKS = Registrar.create(SoundstageInit.MODID, Registries.BLOCK);

    private static BlockBehaviour.Properties props(String name) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of();
        //? if >=1.21.11 {
        /*properties.setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, name)));
        *///?}
        return properties;
    }

    public static final RegistryEntry<Block> SPEAKER_BLOCK = BLOCKS.register("speaker", () ->
            new SpeakerBlock(props("speaker").mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.5f, 6.0f).noOcclusion())
    );

    public static final RegistryEntry<Block> SATELLITE_SPEAKER_BLOCK = BLOCKS.register("satellite_speaker", () ->
            new SatelliteSpeakerBlock(props("satellite_speaker").mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.5f, 6.0f).noOcclusion())
    );

    public static final RegistryEntry<Block> CENTER_SPEAKER_BLOCK = BLOCKS.register("center_speaker", () ->
            new CenterSpeakerBlock(props("center_speaker").mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.5f, 6.0f).noOcclusion())
    );

    public static final RegistryEntry<Block> SUBWOOFER_BLOCK = BLOCKS.register("subwoofer", () ->
            new SubwooferBlock(props("subwoofer").mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.5f, 6.0f).noOcclusion())
    );

    public static final RegistryEntry<Block> PARTY_BOX_BLOCK = BLOCKS.register("party_box", () ->
            new PartyBoxBlock(props("party_box").mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.5f, 6.0f).noOcclusion())
    );

    public static final RegistryEntry<Block> RECEIVER_BLOCK = BLOCKS.register("receiver", () ->
            new ReceiverBlock(props("receiver").mapColor(MapColor.METAL).sound(SoundType.METAL).strength(1.5f, 6.0f).noOcclusion())
    );

    public static final RegistryEntry<Block> SCREEN_BLOCK = BLOCKS.register("screen", () ->
            new ScreenBlock(props("screen").mapColor(MapColor.COLOR_BLACK).sound(SoundType.METAL).strength(1.5f, 6.0f).noOcclusion())
    );

    public static void init() {
        BLOCKS.commit();
    }
}
