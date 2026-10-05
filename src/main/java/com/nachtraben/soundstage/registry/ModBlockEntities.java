package com.nachtraben.soundstage.registry;

import com.nachtraben.soundstage.SoundstageInit;
import com.nachtraben.soundstage.block.PartyBoxBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.CenterSpeakerBlockEntity;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.block.speaker.SatelliteSpeakerBlockEntity;
import com.nachtraben.soundstage.block.speaker.SubwooferBlockEntity;
import dev.armadeus.armalib.platform.registry.Registrar;
import dev.armadeus.armalib.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import dev.armadeus.armalib.platform.registry.BlockEntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES = Registrar.create(SoundstageInit.MODID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistryEntry<BlockEntityType<SpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY = BLOCK_ENTITIES.register("speaker", () ->
            BlockEntityTypes.create(SpeakerBlockEntity::new, true, ModBlocks.SPEAKER_BLOCK.get())
    );

    public static final RegistryEntry<BlockEntityType<SatelliteSpeakerBlockEntity>> SATELLITE_SPEAKER_BLOCK_ENTITY = BLOCK_ENTITIES.register("satellite_speaker", () ->
            BlockEntityTypes.create(SatelliteSpeakerBlockEntity::new, true, ModBlocks.SATELLITE_SPEAKER_BLOCK.get())
    );

    public static final RegistryEntry<BlockEntityType<CenterSpeakerBlockEntity>> CENTER_SPEAKER_BLOCK_ENTITY = BLOCK_ENTITIES.register("center_speaker", () ->
            BlockEntityTypes.create(CenterSpeakerBlockEntity::new, true, ModBlocks.CENTER_SPEAKER_BLOCK.get())
    );

    public static final RegistryEntry<BlockEntityType<SubwooferBlockEntity>> SUBWOOFER_BLOCK_ENTITY = BLOCK_ENTITIES.register("subwoofer", () ->
            BlockEntityTypes.create(SubwooferBlockEntity::new, true, ModBlocks.SUBWOOFER_BLOCK.get())
    );

    public static final RegistryEntry<BlockEntityType<PartyBoxBlockEntity>> PARTY_BOX_BLOCK_ENTITY = BLOCK_ENTITIES.register("party_box", () ->
            BlockEntityTypes.create(PartyBoxBlockEntity::new, true, ModBlocks.PARTY_BOX_BLOCK.get())
    );

    public static final RegistryEntry<BlockEntityType<ReceiverBlockEntity>> RECEIVER_BLOCK_ENTITY = BLOCK_ENTITIES.register("receiver", () ->
            BlockEntityTypes.create(ReceiverBlockEntity::new, !opOnlyDataOff(), ModBlocks.RECEIVER_BLOCK.get())
    );

    public static boolean opOnlyDataOff() {
        boolean off = !dev.armadeus.armalib.platform.Platform.isClient()
                && com.nachtraben.soundstage.RigStaging.serverStages("soundstage:op-only-data", "flag-off");
        if (off) {
            org.slf4j.LoggerFactory.getLogger("Soundstage").warn("Soundstage: red control — the receiver's saved "
                    + "data may be set by anyone's item (rig soundstage:op-only-data)");
        }
        return off;
    }

    public static final RegistryEntry<BlockEntityType<ScreenBlockEntity>> SCREEN_BLOCK_ENTITY = BLOCK_ENTITIES.register("screen", () ->
            BlockEntityTypes.create(ScreenBlockEntity::new, true, ModBlocks.SCREEN_BLOCK.get())
    );

    public static void init() {
        BLOCK_ENTITIES.commit();
    }
}
