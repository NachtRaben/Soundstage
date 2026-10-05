package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.item.SpeakerLinkItem;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;
import com.mojang.serialization.MapCodec;

public class ReceiverBlock extends BaseEntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    protected static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<ReceiverBlock> CODEC = simpleCodec(ReceiverBlock::new);
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    public ReceiverBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        float yaw = context.getRotation();
        if (com.nachtraben.soundstage.RigStaging.fault("placement-not-toward-placer")) yaw = -yaw;
        return this.defaultBlockState().setValue(ROTATION,
                com.nachtraben.soundstage.core.PlacementFacing.rotationToward(yaw, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.RECEIVER_BLOCK_ENTITY.get().create(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        //? if >=1.21.11 {
        /*return RenderShape.INVISIBLE;
        *///?} else {
        return RenderShape.ENTITYBLOCK_ANIMATED;
        //?}
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.RECEIVER_BLOCK_ENTITY.get(), (lvl, p, s, be) -> be.tick(lvl, p, s));
    }
    
    @Override
    //? if >=1.21.11 {
    /*protected net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
                                                                   BlockPos pos, BlockState state, boolean includeData) {
    *///?} else if >=1.20.5 {
    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level,
                                                                BlockPos pos, BlockState state) {
    //?} else {
    /*public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level,
                                                                BlockPos pos, BlockState state) {
    *///?}
        //? if >=1.21.11 {
        /*net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
        *///?} else {
        net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(level, pos, state);
        //?}
        if (level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            com.nachtraben.soundstage.util.ItemData.setFinish(stack, receiver.getFinish());
        }
        return stack;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof ReceiverBlockEntity receiver) {
                receiver.setOwnerUUID(player.getUUID());
                //? if <1.20.5 {
                /*var carried = com.nachtraben.soundstage.util.ItemData.finish(stack);
                if (carried != null) receiver.setFinish(carried);
                *///?}
            }
        }
    }
    
    //? if >=1.20.5 {
    @Override
    //?}
    //? if >=1.21.11 || <1.20.5 {
    /*protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
    *///?} else {
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
    //?}
            BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        var skip = com.nachtraben.soundstage.util.ItemUse.PASS;
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)
                || !com.nachtraben.soundstage.block.speaker.SpeakerFinish.isCasingMaterial(blockItem.getBlock())
                || !(level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver)) {
            return skip;
        }
        if (level.isClientSide()) {
            return com.nachtraben.soundstage.util.ItemUse.SUCCESS;
        }
        if (!receiver.mayControl(player)) {
            com.nachtraben.soundstage.util.PlayerMessage.actionBar(player,
                    net.minecraft.network.chat.Component.literal("Receiver is locked!"));
            return com.nachtraben.soundstage.util.ItemUse.CONSUME;
        }
        receiver.setFinish(receiver.getFinish().withCasing(blockItem.getBlock()));
        level.playSound(null, pos, blockItem.getBlock().defaultBlockState().getSoundType().getPlaceSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.9f);
        return com.nachtraben.soundstage.util.ItemUse.SUCCESS;
    }

    //? if >=1.20.5 {
    @Override
    //?}
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.getMainHandItem().getItem() instanceof SpeakerLinkItem || player.getOffhandItem().getItem() instanceof SpeakerLinkItem) {
            return InteractionResult.PASS;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (level.isClientSide() && entity instanceof ReceiverBlockEntity cbe) {
            SoundstageClientInit.openReceiverScreen(cbe);
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.20.5 {
    /*@Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<net.minecraft.world.item.ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof ReceiverBlockEntity receiver) {
            for (net.minecraft.world.item.ItemStack drop : drops) {
                if (drop.is(asItem())) com.nachtraben.soundstage.util.ItemData.setFinish(drop, receiver.getFinish());
            }
        }
        return drops;
    }
    *///?}

    //? if <1.20.5 {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return com.nachtraben.soundstage.util.ItemUse.use(this::useItemOn, this::useWithoutItem, state, level, pos, player, hand, hit);
    }
    *///?}

    public static void onReceiverRemoved(Level level, BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        com.nachtraben.soundstage.audio.AudioManagerState.removePlayer(pos, serverLevel);
        unlinkSpeakers(level, pos);
        unlinkScreens(level, pos);
    }

    //? if <1.21.11 {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            onReceiverRemoved(level, pos);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
    //?}

    private static void unlinkSpeakers(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ReceiverBlockEntity cbe)) return;
        for (BlockPos speakerPos : new java.util.ArrayList<>(cbe.getSpeakers())) {
            if (com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, speakerPos)
                    instanceof com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity speaker
                    && pos.equals(speaker.getReceiverPos())) {
                speaker.setReceiverPos(null);
                level.sendBlockUpdated(speakerPos, speaker.getBlockState(), speaker.getBlockState(), 3);
            }
        }
        cbe.getSpeakers().clear();
        cbe.setChanged();
    }

    private static void unlinkScreens(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ReceiverBlockEntity cbe)) return;
        for (BlockPos screenPos : new java.util.ArrayList<>(cbe.getScreens())) {
            if (com.nachtraben.soundstage.util.WorldAccess.blockEntityIfLoaded(level, screenPos)
                    instanceof ScreenBlockEntity screen
                    && pos.equals(screen.getReceiverPos())) {
                screen.setReceiverPos(null);
            }
        }
        cbe.getScreens().clear();
        cbe.setChanged();
    }
}
