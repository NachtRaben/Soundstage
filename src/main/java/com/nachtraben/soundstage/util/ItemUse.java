package com.nachtraben.soundstage.util;

public final class ItemUse {
    //? if >=1.21.11 {
    /*public static final net.minecraft.world.InteractionResult PASS = net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
    public static final net.minecraft.world.InteractionResult SUCCESS = net.minecraft.world.InteractionResult.SUCCESS;
    public static final net.minecraft.world.InteractionResult CONSUME = net.minecraft.world.InteractionResult.CONSUME;
    *///?} else if >=1.20.5 {
    public static final net.minecraft.world.ItemInteractionResult PASS = net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    public static final net.minecraft.world.ItemInteractionResult SUCCESS = net.minecraft.world.ItemInteractionResult.SUCCESS;
    public static final net.minecraft.world.ItemInteractionResult CONSUME = net.minecraft.world.ItemInteractionResult.CONSUME;
    //?} else {
    /*public static final net.minecraft.world.InteractionResult PASS = net.minecraft.world.InteractionResult.PASS;
    public static final net.minecraft.world.InteractionResult SUCCESS = net.minecraft.world.InteractionResult.SUCCESS;
    public static final net.minecraft.world.InteractionResult CONSUME = net.minecraft.world.InteractionResult.CONSUME;

    public interface WithItem {
        net.minecraft.world.InteractionResult use(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.block.state.BlockState state,
                                                  net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                  net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                  net.minecraft.world.phys.BlockHitResult hit);
    }

    public interface WithoutItem {
        net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                                                  net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                  net.minecraft.world.phys.BlockHitResult hit);
    }

    public static net.minecraft.world.InteractionResult use(WithItem withItem, WithoutItem withoutItem,
                                                             net.minecraft.world.level.block.state.BlockState state,
                                                             net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                             net.minecraft.world.entity.player.Player player,
                                                             net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            net.minecraft.world.InteractionResult result = withItem.use(stack, state, level, pos, player, hand, hit);
            if (result != PASS) return result;
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? withoutItem.use(state, level, pos, player, hit)
                : net.minecraft.world.InteractionResult.PASS;
    }
    *///?}

    private ItemUse() {
    }

    public static void rightClickWithItem(net.minecraft.server.level.ServerLevel level,
                                           net.minecraft.server.level.ServerPlayer player,
                                           net.minecraft.core.BlockPos pos,
                                           net.minecraft.world.item.Item item,
                                           net.minecraft.world.phys.BlockHitResult hit) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        //? if >=1.20.5 {
        level.getBlockState(pos).useItemOn(stack, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        //?} else {
        /*player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        level.getBlockState(pos).use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        *///?}
    }
}
