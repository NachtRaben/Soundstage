package com.nachtraben.soundstage.block.speaker;

import com.nachtraben.soundstage.audio.AudioManagerState;
import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.core.ChannelMode;
import com.nachtraben.soundstage.core.PlacementFacing;
import com.nachtraben.soundstage.item.SpeakerLinkItem;
import com.nachtraben.soundstage.util.ItemData;
import com.nachtraben.soundstage.util.ItemUse;
import com.nachtraben.soundstage.util.PlayerMessage;
import com.nachtraben.soundstage.util.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSpeakerBlock extends BaseEntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    public static final BooleanProperty GRILLE = BooleanProperty.create("grille");

    public AbstractSpeakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0).setValue(GRILLE, true));
    }

    public static final int SPEAKER_FACINGS = 8;

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        float yaw = context.getRotation();
        return this.defaultBlockState().setValue(ROTATION,
                PlacementFacing.rotationToward(yaw, SPEAKER_FACINGS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, GRILLE);
    }

    @Override
    //? if >=1.21.11 {
    /*protected net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
                                                                   BlockPos pos, BlockState state,
                                                                   boolean includeData) {
        net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
    *///?} else if >=1.20.5 {
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
                                                                BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
    //?} else {
    /*public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level,
                                                                BlockPos pos, BlockState state) {
        net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(level, pos, state);
    *///?}
        if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            ItemData.setFinish(stack, speaker.getFinish());
            ItemData.setSettings(stack, speaker.settings());
        }
        ItemData.setBlockState(stack, GRILLE, state.getValue(GRILLE));
        return stack;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (placer != null) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof SpeakerBlockEntity speaker) {
                //? if <1.20.5 {
                /*var carriedFinish = com.nachtraben.soundstage.util.ItemData.finish(stack);
                if (carriedFinish != null) speaker.setFinish(carriedFinish);
                var carriedSettings = com.nachtraben.soundstage.util.ItemData.settings(stack);
                if (carriedSettings != null) speaker.applySettings(carriedSettings);
                *///?}
                var settings = ItemData.settings(stack);
                var aim = PlacementFacing.aimAfterPlacement(placer.getXRot(),
                        settings == null ? null
                                : new PlacementFacing.Aim(settings.pitch(), settings.yawOffset()));
                speaker.setPitch(aim.pitch());
                speaker.setYawOffset(aim.yawOffset());

                speaker.setChanged();
                if (!level.isClientSide()) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        //? if >=1.21.11 {
        /*return RenderShape.INVISIBLE;
        *///?} else {
        return RenderShape.ENTITYBLOCK_ANIMATED;
        //?}
    }

    public ChannelMode[] getValidModes() {
        if (this instanceof SubwooferBlock) {
            return new ChannelMode[]{ ChannelMode.LFE };
        } else if (this instanceof CenterSpeakerBlock) {
            return new ChannelMode[]{ ChannelMode.CENTER };
        } else if (this instanceof SatelliteSpeakerBlock) {
            return new ChannelMode[]{ ChannelMode.SURROUND_LEFT, ChannelMode.SURROUND_RIGHT };
        } else {
            return new ChannelMode[]{ ChannelMode.MONO, ChannelMode.LEFT, ChannelMode.RIGHT };
        }
    }

    //? if >=1.20.5 {
    @Override
    //?}
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, Player player, @NotNull BlockHitResult hitResult) {
        if (player.getMainHandItem().getItem() instanceof SpeakerLinkItem
                || player.getOffhandItem().getItem() instanceof SpeakerLinkItem) {
            return InteractionResult.PASS;
        }

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof SpeakerBlockEntity sbe)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                if (canEdit(level, player, sbe)) {
                    ChannelMode[] modes = getValidModes();
                    int nextIdx = 0;
                    for (int i = 0; i < modes.length; i++) {
                        if (modes[i] == sbe.getChannelMode()) {
                            nextIdx = (i + 1) % modes.length;
                            break;
                        }
                    }
                    sbe.setChannelMode(modes[nextIdx]);
                    sbe.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    PlayerMessage.actionBar(player, Component.literal("Speaker Mode set to: " + modes[nextIdx].name()));
                } else {
                    PlayerMessage.actionBar(player, Component.literal("Receiver is locked!"));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide()) {
            SoundstageClientInit.openSpeakerConfigScreen(sbe);
        } else {

        }
        return InteractionResult.SUCCESS;
    }

    public static boolean canEdit(Level level, Player player, SpeakerBlockEntity speaker) {
        BlockPos ctrlPos = speaker.getReceiverPos();
        if (ctrlPos == null) return true;
        BlockEntity cEntity = WorldAccess.blockEntityIfLoaded(level, ctrlPos);
        return !(cEntity instanceof IAudioReceiver ctrl)
                || ctrl.mayControl(player);
    }

    protected boolean acceptsFinish(BlockState state) {
        return true;
    }

    //? if >=1.20.5 {
    @Override
    //?}
    //? if >=1.21.11 || <1.20.5 {
    /*protected @NotNull InteractionResult useItemOn(
    *///?} else {
    protected @NotNull net.minecraft.world.ItemInteractionResult useItemOn(
    //?}
            @NotNull ItemStack stack, @NotNull BlockState state,
            @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
            @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        var skip = ItemUse.PASS;
        if (!acceptsFinish(state) || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            return skip;
        }
        boolean shears = stack.getItem() instanceof ShearsItem;
        Block material =
                stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        boolean casing = material != null && SpeakerFinish.isCasingMaterial(material);
        boolean grille = material != null && SpeakerFinish.isGrilleMaterial(material);
        if (!(shears || casing || grille)) return skip;
        if (shears && !state.getValue(GRILLE)) return skip;

        if (level.isClientSide()) {
            return ItemUse.SUCCESS;
        }
        if (!canEdit(level, player, speaker)) {
            PlayerMessage.actionBar(player, Component.literal("Receiver is locked!"));
            return ItemUse.CONSUME;
        }

        BlockState updated = state;
        if (shears) {
            updated = state.setValue(GRILLE, false);
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        } else if (casing) {
            speaker.setFinish(speaker.getFinish().withCasing(material));
            level.playSound(null, pos, material.defaultBlockState().getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 1.0f, 0.9f);
        } else {
            speaker.setFinish(speaker.getFinish().withGrille(material));
            updated = state.setValue(GRILLE, true);
            level.playSound(null, pos, SoundEvents.WOOL_PLACE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (updated != state) {
            level.setBlock(pos, updated, 3);
        } else {
            level.sendBlockUpdated(pos, state, state, 3);
        }
        return ItemUse.SUCCESS;
    }

    //? if <1.20.5 {
    /*@Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<net.minecraft.world.item.ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof SpeakerBlockEntity speaker) {
            for (net.minecraft.world.item.ItemStack drop : drops) {
                if (!drop.is(asItem())) continue;
                com.nachtraben.soundstage.util.ItemData.setFinish(drop, speaker.getFinish());
                com.nachtraben.soundstage.util.ItemData.setSettings(drop, speaker.settings());
            }
        }
        return drops;
    }
    *///?}

    //? if <1.20.5 {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return com.nachtraben.soundstage.util.ItemUse.use(this::useItemOn, this::useWithoutItem, state, level, pos,
                player, hand, hit);
    }
    *///?}

    public static void onSpeakerRemoved(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        AudioManagerState.removePlayer(pos, serverLevel);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof SpeakerBlockEntity speaker) {
            BlockPos receiverPos = speaker.getReceiverPos();
            if (receiverPos != null) {
                BlockEntity ctrlEntity = WorldAccess
                        .blockEntityIfLoaded(level, receiverPos);
                if (ctrlEntity instanceof ReceiverBlockEntity cbe) {
                    cbe.getSpeakers().remove(pos);
                    cbe.setChanged();
                    level.sendBlockUpdated(receiverPos, cbe.getBlockState(), cbe.getBlockState(), 3);
                }
            }
        }
    }

    //? if <1.21.11 {
    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState,
            boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            onSpeakerRemoved(level, pos);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
    //?}

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state,
            net.minecraft.world.level.block.entity.@NotNull BlockEntityType<T> type) {
        return (tickLevel, tickPos, tickState, be) -> {
            if (be instanceof SpeakerBlockEntity speaker) {
                tickSpeaker(tickLevel, tickPos, speaker);
            }
        };
    }

    protected static void tickSpeaker(Level level, BlockPos pos, SpeakerBlockEntity speaker) {
        SpeakerBlockEntity.serverTick(level, pos, speaker);
        SpeakerBlockEntity.clientTick(level, pos, speaker);
    }
}
