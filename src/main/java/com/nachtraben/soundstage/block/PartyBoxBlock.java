package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.block.speaker.SpeakerBlock;
import com.nachtraben.soundstage.client.SoundstageClientInit;
import com.nachtraben.soundstage.core.PlacementFacing;
import com.nachtraben.soundstage.item.SpeakerLinkItem;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public class PartyBoxBlock extends SpeakerBlock {
    public PartyBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.PARTY_BOX_BLOCK_ENTITY.get().create(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        float yaw = context.getRotation();
        return this.defaultBlockState().setValue(ROTATION,
                PlacementFacing.rotationToward(yaw, 4));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof PartyBoxBlockEntity speaker) {
            speaker.setPitch(0.0f);
            speaker.setYawOffset(0.0f);
            if (!level.isClientSide() && placer instanceof Player player) {
                speaker.setOwnerUUID(player.getUUID());
            }
        }
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

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof PartyBoxBlockEntity sbe)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                SoundstageClientInit.openSpeakerConfigScreen(sbe);
            } else {
                SoundstageClientInit.openSpeakerScreen(sbe);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.PARTY_BOX_BLOCK_ENTITY.get(),
                (tickLevel, tickPos, tickState, be) -> {
                    tickSpeaker(tickLevel, tickPos, be);
                    PartyBoxBlockEntity.tick(tickLevel, tickPos, tickState, be);
                });
    }
}
