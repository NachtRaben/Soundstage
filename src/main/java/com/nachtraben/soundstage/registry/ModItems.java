package com.nachtraben.soundstage.registry;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.item.SpeakerLinkItem;
import dev.armadeus.armalib.platform.registry.Registrar;
import dev.armadeus.armalib.platform.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Registrar<Item> ITEMS = Registrar.create(SoundstageInit.MODID, Registries.ITEM);

    private static Item.Properties itemProps(String name) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.11 {
        /*properties.setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SoundstageInit.MODID, name)));
        *///?}
        return properties;
    }

    private static Item.Properties blockItemProps(String name) {
        Item.Properties properties = itemProps(name);
        //? if >=1.21.11 {
        /*properties.useBlockDescriptionPrefix();
        *///?}
        return properties;
    }

    public static final RegistryEntry<Item> SPEAKER_ITEM = ITEMS.register("speaker", () ->
            new BlockItem(ModBlocks.SPEAKER_BLOCK.get(), blockItemProps("speaker"))
    );

    public static final RegistryEntry<Item> SATELLITE_SPEAKER_ITEM = ITEMS.register("satellite_speaker", () ->
            new BlockItem(ModBlocks.SATELLITE_SPEAKER_BLOCK.get(), blockItemProps("satellite_speaker"))
    );

    public static final RegistryEntry<Item> CENTER_SPEAKER_ITEM = ITEMS.register("center_speaker", () ->
            new BlockItem(ModBlocks.CENTER_SPEAKER_BLOCK.get(), blockItemProps("center_speaker"))
    );

    public static final RegistryEntry<Item> SUBWOOFER_ITEM = ITEMS.register("subwoofer", () ->
            new BlockItem(ModBlocks.SUBWOOFER_BLOCK.get(), blockItemProps("subwoofer"))
    );

    public static final RegistryEntry<Item> PARTY_BOX_ITEM = ITEMS.register("party_box", () ->
            new BlockItem(ModBlocks.PARTY_BOX_BLOCK.get(), blockItemProps("party_box"))
    );

    public static final RegistryEntry<Item> RECEIVER_ITEM = ITEMS.register("receiver", () ->
            new BlockItem(ModBlocks.RECEIVER_BLOCK.get(), blockItemProps("receiver"))
    );

    public static final RegistryEntry<Item> SCREEN_ITEM = ITEMS.register("screen", () ->
            new BlockItem(ModBlocks.SCREEN_BLOCK.get(), blockItemProps("screen"))
    );

    public static final RegistryEntry<Item> SPEAKER_LINK = ITEMS.register("speaker_link", () ->
            new SpeakerLinkItem(itemProps("speaker_link").stacksTo(1))
    );

    public static void init() {
        ITEMS.commit();
    }
}
