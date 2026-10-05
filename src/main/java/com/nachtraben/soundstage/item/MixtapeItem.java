package com.nachtraben.soundstage.item;

import dev.armadeus.armalib.platform.block.SaveView;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.audio.AudioManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public class MixtapeItem extends Item {
    public MixtapeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        
        BlockPos pos = context.getClickedPos();
        BlockEntity be = context.getLevel().getBlockEntity(pos);
        if (be instanceof ReceiverBlockEntity receiver) {
            ItemStack stack = context.getItemInHand();
            net.minecraft.nbt.CompoundTag data = com.nachtraben.soundstage.util.ItemData.customData(stack);
            if (data != null && data.contains("URLs")) {
                java.util.List<String> urlList = SaveView.reader(data).getStringList("URLs");
                if (!urlList.isEmpty()) {
                    if (context.getPlayer() instanceof ServerPlayer sp) {
                        for (int i = 0; i < urlList.size(); i++) {
                            String url = urlList.get(i);
                            AudioManager.get().play(pos, url, (net.minecraft.server.level.ServerLevel)context.getLevel(), sp);
                        }
                        com.nachtraben.soundstage.util.PlayerMessage.actionBar(context.getPlayer(), Component.literal("Loaded mixtape into receiver!"));
                    }
                }
            } else {
                if (context.getPlayer() != null) {
                    com.nachtraben.soundstage.util.PlayerMessage.actionBar(context.getPlayer(), Component.literal("This mixtape is empty. Shift-right-click air to add URLs."));
                }
            }
            return InteractionResult.SUCCESS;
        }
        
        return InteractionResult.PASS;
    }
}
