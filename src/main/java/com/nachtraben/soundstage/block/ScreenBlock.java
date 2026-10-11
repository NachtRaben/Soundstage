package com.nachtraben.soundstage.block;

import com.mojang.serialization.MapCodec;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.item.SpeakerLinkItem;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import com.nachtraben.soundstage.util.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ScreenBlock extends BaseEntityBlock {
    //? if >=1.21.11 {
    /*public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction>
            FACING = HorizontalDirectionalBlock.FACING;
    *///?} else {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;
    //?}

    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape WEST_SHAPE = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 2, 16, 16);

    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    public ScreenBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction facing = clickedFace.getAxis().isHorizontal()
                ? clickedFace
                : context.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SCREEN_BLOCK_ENTITY.get().create(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        //? if >=1.21.11 {
        /*return RenderShape.INVISIBLE;
        *///?} else {
        return RenderShape.ENTITYBLOCK_ANIMATED;
        //?}
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SCREEN_BLOCK_ENTITY.get(),
                (lvl, p, s, be) -> be.tick(lvl, p, s));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ScreenBlockEntity screen)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (BlockPos neighbourPos : ScreenBlockEntity.inPlaneNeighbours(pos, facing)) {
            if (!(WorldAccess.blockEntityIfLoaded(level, neighbourPos)
                    instanceof ScreenBlockEntity neighbour)
                    || neighbour.getBlockState().getValue(FACING) != facing
                    || neighbour.getReceiverPos() == null) {
                continue;
            }
            BlockPos receiverPos = neighbour.getReceiverPos();
            if (!(WorldAccess.blockEntityIfLoaded(level, receiverPos)
                    instanceof ReceiverBlockEntity receiver) || !receiver.mayControl(player)) {
                continue;
            }
            receiver.getScreens().add(pos);
            receiver.setChanged();
            level.sendBlockUpdated(receiverPos, receiver.getBlockState(), receiver.getBlockState(), 3);
            screen.setReceiverPos(receiverPos);
            return;
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        ScreenBlockEntity.bumpEpoch(level);
    }

    //? if >=1.20.5 {
    @Override
    //?}
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player.getMainHandItem().getItem() instanceof SpeakerLinkItem
                || player.getOffhandItem().getItem() instanceof SpeakerLinkItem) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
            SoundstageClientInit.openDisplayScreen(screen);
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.20.5 {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem(state, level, pos, player, hit)
                : InteractionResult.PASS;
    }
    *///?}
}
