package com.nachtraben.soundstage.registry;

import com.nachtraben.soundstage.SoundstageInit;
import dev.armadeus.armalib.platform.registry.CreativeTabs;
import dev.armadeus.armalib.platform.registry.Registrar;
import dev.armadeus.armalib.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModTabs {
    public static final Registrar<CreativeModeTab> TABS =
            Registrar.create(SoundstageInit.MODID, Registries.CREATIVE_MODE_TAB);

    public static final RegistryEntry<CreativeModeTab> SOUNDSTAGE_TAB = TABS.register("soundstage_tab", () ->
        CreativeTabs.create(
            Component.translatable("itemGroup.soundstage.soundstage_tab"),
            () -> new ItemStack(ModItems.SPEAKER_ITEM.get())
        )
    );

    public static void init() {
        TABS.commit();
    }
}
