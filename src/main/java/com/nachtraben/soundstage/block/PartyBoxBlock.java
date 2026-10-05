package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.block.speaker.SpeakerBlock;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
    public net.minecraft.world.level.block.state.BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        float yaw = context.getRotation();
        if (com.nachtraben.soundstage.RigStaging.fault("placement-not-toward-placer")) yaw = -yaw;
        return this.defaultBlockState().setValue(ROTATION,
                com.nachtraben.soundstage.core.PlacementFacing.rotationToward(yaw, 4));
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof com.nachtraben.soundstage.block.PartyBoxBlockEntity speaker) {
            speaker.setPitch(0.0f);
            speaker.setYawOffset(0.0f);
            if (!level.isClientSide() && placer instanceof net.minecraft.world.entity.player.Player player) {
                speaker.setOwnerUUID(player.getUUID());
            }
        }
    }

    //? if >=1.20.5 {
    @Override
    //?}
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (player.getMainHandItem().getItem() instanceof com.nachtraben.soundstage.item.SpeakerLinkItem || player.getOffhandItem().getItem() instanceof com.nachtraben.soundstage.item.SpeakerLinkItem) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof com.nachtraben.soundstage.block.PartyBoxBlockEntity sbe)) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                com.nachtraben.soundstage.client.SoundstageClientInit.openSpeakerConfigScreen(sbe);
            } else {
                com.nachtraben.soundstage.client.SoundstageClientInit.openSpeakerScreen(sbe);
            }
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.PARTY_BOX_BLOCK_ENTITY.get(),
                (tickLevel, tickPos, tickState, be) -> {
                    tickSpeaker(tickLevel, tickPos, be);
                    PartyBoxBlockEntity.tick(tickLevel, tickPos, tickState, be);
                });
    }
}
