package com.nachtraben.soundstage.item;

import com.nachtraben.soundstage.block.IAudioReceiver;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.speaker.SpeakerFinish;
import com.nachtraben.soundstage.network.RemoteOpenS2CPacket;
import com.nachtraben.soundstage.util.ItemData;
import com.nachtraben.soundstage.util.PlayerMessage;
import com.nachtraben.soundstage.util.Players;
import com.nachtraben.soundstage.util.WorldAccess;
import dev.armadeus.armalib.platform.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class RemoteItem extends Item {

    public RemoteItem(Properties properties) {
        super(properties);
    }

    private static volatile long flashNanos;
    private static volatile InteractionHand flashHand;

    public static long flashNanos() {
        return flashNanos;
    }

    public static InteractionHand flashHand() {
        return flashHand;
    }

    private static void flashed(InteractionHand hand) {
        flashNanos = System.nanoTime();
        flashHand = hand;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide()) {
            flashed(context.getHand());
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            if (!receiver.mayControl(player)) return InteractionResult.SUCCESS;
            ItemData.setFinish(stack, SpeakerFinish.DEFAULT.withCasing(receiver.getFinish().casingBlock()));
        } else {
            return InteractionResult.SUCCESS;
        }
        ItemData.setRemoteLink(stack, GlobalPos.of(level.dimension(), pos.immutable()));
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.11 {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) flashed(hand);
        else if (player instanceof ServerPlayer serverPlayer) open(serverPlayer, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
    *///?} else {
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                                      InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) flashed(hand);
        else if (player instanceof ServerPlayer serverPlayer) open(serverPlayer, stack);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    //?}

    private static void open(ServerPlayer player, ItemStack stack) {
        GlobalPos link = ItemData.remoteLink(stack);
        if (link == null) {
            PlayerMessage.actionBar(player, Component.literal("Link this remote: sneak and use it on an A/V receiver"));
            return;
        }
        ServerLevel level = Players.level(player);
        if (!level.dimension().equals(link.dimension())) return;
        BlockPos pos = link.pos();
        if (!IAudioReceiver.withinControlRange(player, pos)) return;
        var found = WorldAccess.blockEntityIfLoaded(level, pos);
        if (!(found instanceof ReceiverBlockEntity receiver)) return;
        if (!receiver.mayControl(player)) return;
        Network.sendToPlayer(player, new RemoteOpenS2CPacket(pos));
    }

    //? if >=1.21.11 {
    /*@Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        describe(stack, tooltip);
    }
    *///?} else if >=1.20.5 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        describe(stack, tooltip::add);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level,
                                java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        describe(stack, tooltip::add);
    }
    *///?}

    private static void describe(ItemStack stack, Consumer<Component> tooltip) {
        GlobalPos link = ItemData.remoteLink(stack);
        if (link == null) {
            tooltip.accept(Component.translatable("tooltip.soundstage.remote.not_linked")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.soundstage.remote.how").withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockPos pos = link.pos();
        tooltip.accept(Component.translatable("tooltip.soundstage.remote.linked").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.soundstage.remote.where",
                pos.getX(), pos.getY(), pos.getZ(), dimensionName(link)).withStyle(ChatFormatting.GRAY));
    }

    private static String dimensionName(GlobalPos link) {
        //? if >=1.21.11 {
        /*String path = link.dimension().identifier().getPath();
        *///?} else {
        String path = link.dimension().location().getPath();
        //?}
        if (path.startsWith("the_")) path = path.substring(4);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
