package com.nachtraben.soundstage.registry;

//? if >=1.20.5 {
import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import dev.armadeus.armalib.platform.registry.Registrar;
import dev.armadeus.armalib.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
//?}

public class ModComponents {
    //? if >=1.20.5 {
    public static final Registrar<DataComponentType<?>> COMPONENTS =
            Registrar.create(SoundstageInit.MODID, Registries.DATA_COMPONENT_TYPE);

    public static final RegistryEntry<DataComponentType<SpeakerFinish>> SPEAKER_FINISH =
            COMPONENTS.register("speaker_finish", () -> DataComponentType.<SpeakerFinish>builder()
                    .persistent(SpeakerFinish.CODEC)
                    .networkSynchronized(SpeakerFinish.STREAM_CODEC)
                    .build());

    public static final RegistryEntry<DataComponentType<com.nachtraben.soundstage.block.speaker.SpeakerSettings>> SPEAKER_SETTINGS =
            COMPONENTS.register("speaker_settings", () -> DataComponentType.<com.nachtraben.soundstage.block.speaker.SpeakerSettings>builder()
                    .persistent(com.nachtraben.soundstage.block.speaker.SpeakerSettings.CODEC)
                    .networkSynchronized(com.nachtraben.soundstage.block.speaker.SpeakerSettings.STREAM_CODEC)
                    .build());
    //?}

    public static void init() {
        //? if >=1.20.5 {
        COMPONENTS.commit();
        //?}
    }
}
