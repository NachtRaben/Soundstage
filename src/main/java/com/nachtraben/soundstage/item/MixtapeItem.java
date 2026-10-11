package com.nachtraben.soundstage.item;

import com.nachtraben.soundstage.audio.AudioManager;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.util.ItemData;
import com.nachtraben.soundstage.util.PlayerMessage;
import dev.armadeus.armalib.platform.block.SaveView;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

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
            CompoundTag data = ItemData.customData(stack);
            if (data != null && data.contains("URLs")) {
                List<String> urlList = SaveView.reader(data).getStringList("URLs");
                if (!urlList.isEmpty()) {
                    if (context.getPlayer() instanceof ServerPlayer sp) {
                        for (int i = 0; i < urlList.size(); i++) {
                            String url = urlList.get(i);
                            AudioManager.get().play(pos, url, (ServerLevel)context.getLevel(), sp);
                        }
                        PlayerMessage.actionBar(context.getPlayer(),
                                Component.literal("Loaded mixtape into receiver!"));
                    }
                }
            } else {
                if (context.getPlayer() != null) {
                    PlayerMessage.actionBar(context.getPlayer(),
                            Component.literal("This mixtape is empty. Shift-right-click air to add URLs."));
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
