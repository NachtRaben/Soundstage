package com.nachtraben.soundstage.item;

import com.nachtraben.soundstage.block.PartyBoxBlockEntity;
import com.nachtraben.soundstage.block.ReceiverBlockEntity;
import com.nachtraben.soundstage.block.ScreenBlock;
import com.nachtraben.soundstage.block.ScreenBlockEntity;
import com.nachtraben.soundstage.block.speaker.AbstractSpeakerBlock;
import com.nachtraben.soundstage.block.speaker.SpeakerBlockEntity;
import com.nachtraben.soundstage.core.LinkTrail;
import com.nachtraben.soundstage.core.video.DisplayLayout;
import com.nachtraben.soundstage.util.ItemData;
import com.nachtraben.soundstage.util.PlayerMessage;
import com.nachtraben.soundstage.util.WorldAccess;
import dev.armadeus.armalib.platform.block.SaveView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpeakerLinkItem extends Item {

    public static final int MAX_LINK_DISTANCE = SpeakerBlockEntity.MAX_RANGE;

    private static final int MAX_LINK_TRAIL_PARTICLES = 48;

    public SpeakerLinkItem(Properties properties) {
        super(properties);
    }

    private static boolean withinMesh(Level level, BlockPos candidate, BlockPos receiverPos) {
        long maxSqr = (long) MAX_LINK_DISTANCE * MAX_LINK_DISTANCE;
        if (candidate.distSqr(receiverPos) <= maxSqr) {
            return true;
        }
        if (WorldAccess.blockEntityIfLoaded(level, receiverPos)
                instanceof ReceiverBlockEntity receiver) {
            for (BlockPos linked : receiver.getSpeakers()) {
                if (!linked.equals(candidate) && candidate.distSqr(linked) <= maxSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack stack = context.getItemInHand();

        if (player.isShiftKeyDown()) {
            if (blockEntity instanceof SpeakerBlockEntity speaker
                    && !(blockEntity instanceof PartyBoxBlockEntity)
                    && speaker.getReceiverPos() != null) {
                return unlinkSpeaker(level, player, pos, speaker);
            } else if (blockEntity instanceof ScreenBlockEntity screen && screen.getReceiverPos() != null) {
                return unlinkDisplay(level, player, pos, screen);
            }
        }

        if (blockEntity instanceof ReceiverBlockEntity) {
            CompoundTag tag = ItemData.customData(stack);
            if (tag == null) tag = new CompoundTag();
            tag.putLong("ReceiverPos", pos.asLong());
            ItemData.setCustomData(stack, tag);
            PlayerMessage.actionBar(player, Component.literal("Receiver position saved. Click a speaker to link."));
            return InteractionResult.SUCCESS;
        } else if (blockEntity instanceof SpeakerBlockEntity speaker) {
            if (blockEntity instanceof PartyBoxBlockEntity) {
                PlayerMessage.actionBar(player,
                        Component.literal("Party Boxes are standalone speakers and cannot be linked!"));
                return InteractionResult.SUCCESS;
            }
            CompoundTag tag = ItemData.customData(stack);
            if (tag != null && tag.contains("ReceiverPos")) {
                BlockPos receiverPos = BlockPos.of(SaveView.reader(tag).getLong("ReceiverPos", 0L));

                if (!withinMesh(level, pos, receiverPos)) {
                    PlayerMessage.actionBar(player, Component.literal(
                            "Too far from the receiver or any speaker already linked to it (max "
                                    + MAX_LINK_DISTANCE + " blocks)."));
                    return InteractionResult.SUCCESS;
                }

                if (!AbstractSpeakerBlock.canEdit(level, player, speaker)) {
                    PlayerMessage.actionBar(player,
                            Component.literal("This speaker's receiver is locked!"));
                    return InteractionResult.SUCCESS;
                }
                BlockEntity maybeReceiver = WorldAccess
                        .blockEntityIfLoaded(level, receiverPos);
                if (maybeReceiver instanceof ReceiverBlockEntity target && !target.mayControl(player)) {
                    PlayerMessage.actionBar(player,
                            Component.literal("That receiver is locked!"));
                    return InteractionResult.SUCCESS;
                }

                if (maybeReceiver instanceof ReceiverBlockEntity receiver) {
                    BlockPos oldReceiverPos = speaker.getReceiverPos();
                    if (oldReceiverPos != null && !oldReceiverPos.equals(receiverPos)) {
                        BlockEntity oldEntity = WorldAccess
                                .blockEntityIfLoaded(level, oldReceiverPos);
                        if (oldEntity instanceof ReceiverBlockEntity oldReceiver) {
                            oldReceiver.getSpeakers().remove(pos);
                            oldReceiver.setChanged();
                            level.sendBlockUpdated(oldReceiverPos, oldEntity.getBlockState(),
                                    oldEntity.getBlockState(), 3);
                        }
                    }
                    if (!receiver.getSpeakers().contains(pos)) {
                        receiver.getSpeakers().add(pos);
                        receiver.setChanged();
                        level.sendBlockUpdated(receiverPos, maybeReceiver.getBlockState(),
                                maybeReceiver.getBlockState(), 3);
                    }
                    speaker.setReceiverPos(receiverPos);
                    speaker.setChanged();
                    level.sendBlockUpdated(pos, speaker.getBlockState(), speaker.getBlockState(), 3);
                    PlayerMessage.actionBar(player, Component.literal("Speaker linked to Receiver!"));

                    if (level instanceof ServerLevel serverLevel) {
                        double[] trail = LinkTrail.sample(
                                pos.getX(), pos.getY(), pos.getZ(),
                                receiverPos.getX(), receiverPos.getY(), receiverPos.getZ(),
                                MAX_LINK_TRAIL_PARTICLES);
                        for (int i = 0; i < trail.length; i += 3) {
                            serverLevel.sendParticles(DustParticleOptions.REDSTONE,
                                trail[i], trail[i + 1], trail[i + 2], 1, 0, 0, 0, 0);
                        }
                    }

                    return InteractionResult.SUCCESS;
                } else {
                    PlayerMessage.actionBar(player, Component.literal(
                            "That receiver is gone or too far away to link right now."));
                }
            } else {
                PlayerMessage.actionBar(player, Component.literal("Click a Receiver first to begin linking."));
            }
        } else if (blockEntity instanceof ScreenBlockEntity screen) {
            CompoundTag tag = ItemData.customData(stack);
            if (tag == null || !tag.contains("ReceiverPos")) {
                PlayerMessage.actionBar(player, Component.literal("Click a Receiver first to begin linking."));
                return InteractionResult.SUCCESS;
            }
            BlockPos receiverPos = BlockPos.of(SaveView.reader(tag).getLong("ReceiverPos", 0L));

            long maxSqr = (long) MAX_LINK_DISTANCE * MAX_LINK_DISTANCE;
            if (pos.distSqr(receiverPos) > maxSqr) {
                PlayerMessage.actionBar(player, Component.literal(
                        "Too far from the receiver (max " + MAX_LINK_DISTANCE + " blocks)."));
                return InteractionResult.SUCCESS;
            }

            if (!(WorldAccess.blockEntityIfLoaded(level, receiverPos) instanceof ReceiverBlockEntity newReceiver)) {
                PlayerMessage.actionBar(player, Component.literal(
                        "That receiver is gone or too far away to link right now."));
                return InteractionResult.SUCCESS;
            }
            if (!newReceiver.mayControl(player)) {
                PlayerMessage.actionBar(player,
                        Component.literal("That receiver is locked!"));
                return InteractionResult.SUCCESS;
            }

            Direction facing = screen.getBlockState().getValue(ScreenBlock.FACING);
            DisplayLayout layout = ScreenBlockEntity.findDisplay(level, pos, facing);
            List<BlockPos> members = ScreenBlockEntity.memberPositions(pos, facing, layout);

            Set<BlockPos> checkedOldReceivers = new HashSet<>();
            for (BlockPos memberPos : members) {
                if (!(WorldAccess.blockEntityIfLoaded(level, memberPos) instanceof ScreenBlockEntity member)) {
                    continue;
                }
                BlockPos oldReceiverPos = member.getReceiverPos();
                if (oldReceiverPos == null || oldReceiverPos.equals(receiverPos)
                        || !checkedOldReceivers.add(oldReceiverPos)) {
                    continue;
                }
                if (WorldAccess.blockEntityIfLoaded(level, oldReceiverPos) instanceof ReceiverBlockEntity oldReceiver
                        && !oldReceiver.mayControl(player)) {
                    PlayerMessage.actionBar(player,
                            Component.literal("One of this display's receivers is locked!"));
                    return InteractionResult.SUCCESS;
                }
            }

            int linkedCount = 0;
            for (BlockPos memberPos : members) {
                if (!(WorldAccess.blockEntityIfLoaded(level, memberPos) instanceof ScreenBlockEntity member)) {
                    continue;
                }
                BlockPos oldReceiverPos = member.getReceiverPos();
                if (oldReceiverPos != null && !oldReceiverPos.equals(receiverPos)
                        && WorldAccess.blockEntityIfLoaded(level, oldReceiverPos)
                                instanceof ReceiverBlockEntity oldReceiver) {
                    oldReceiver.getScreens().remove(memberPos);
                    oldReceiver.setChanged();
                    level.sendBlockUpdated(oldReceiverPos, oldReceiver.getBlockState(), oldReceiver.getBlockState(), 3);
                }
                if (!newReceiver.getScreens().contains(memberPos)) {
                    newReceiver.getScreens().add(memberPos);
                }
                member.setReceiverPos(receiverPos);
                linkedCount++;
            }
            newReceiver.setChanged();
            level.sendBlockUpdated(receiverPos, newReceiver.getBlockState(), newReceiver.getBlockState(), 3);
            PlayerMessage.actionBar(player, Component.literal(linkedCount == 1
                    ? "Screen linked to Receiver!"
                    : "Display linked to Receiver (" + linkedCount + " screens)!"));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult unlinkSpeaker(Level level, Player player, BlockPos pos,
            SpeakerBlockEntity speaker) {
        if (!AbstractSpeakerBlock.canEdit(level, player, speaker)) {
            PlayerMessage.actionBar(player,
                    Component.literal("This speaker's receiver is locked!"));
            return InteractionResult.SUCCESS;
        }
        BlockPos receiverPos = speaker.getReceiverPos();
        if (receiverPos != null && WorldAccess
                .blockEntityIfLoaded(level, receiverPos) instanceof ReceiverBlockEntity receiver) {
            receiver.getSpeakers().remove(pos);
            receiver.setChanged();
            level.sendBlockUpdated(receiverPos, receiver.getBlockState(), receiver.getBlockState(), 3);
        }
        speaker.setReceiverPos(null);
        speaker.setChanged();
        level.sendBlockUpdated(pos, speaker.getBlockState(), speaker.getBlockState(), 3);
        PlayerMessage.actionBar(player, Component.literal("Speaker unlinked."));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult unlinkDisplay(Level level, Player player, BlockPos pos, ScreenBlockEntity screen) {
        Direction facing = screen.getBlockState().getValue(ScreenBlock.FACING);
        DisplayLayout layout = ScreenBlockEntity.findDisplay(level, pos, facing);
        List<BlockPos> members = ScreenBlockEntity.memberPositions(pos, facing, layout);

        Set<BlockPos> checkedReceivers = new HashSet<>();
        for (BlockPos memberPos : members) {
            if (!(WorldAccess.blockEntityIfLoaded(level, memberPos) instanceof ScreenBlockEntity member)) {
                continue;
            }
            BlockPos receiverPos = member.getReceiverPos();
            if (receiverPos == null || !checkedReceivers.add(receiverPos)) {
                continue;
            }
            if (WorldAccess.blockEntityIfLoaded(level, receiverPos) instanceof ReceiverBlockEntity receiver
                    && !receiver.mayControl(player)) {
                PlayerMessage.actionBar(player,
                        Component.literal("One of this display's receivers is locked!"));
                return InteractionResult.SUCCESS;
            }
        }

        int unlinkedCount = 0;
        for (BlockPos memberPos : members) {
            if (!(WorldAccess.blockEntityIfLoaded(level, memberPos) instanceof ScreenBlockEntity member)) {
                continue;
            }
            BlockPos receiverPos = member.getReceiverPos();
            if (receiverPos == null) {
                continue;
            }
            if (WorldAccess.blockEntityIfLoaded(level, receiverPos) instanceof ReceiverBlockEntity receiver) {
                receiver.getScreens().remove(memberPos);
                receiver.setChanged();
                level.sendBlockUpdated(receiverPos, receiver.getBlockState(), receiver.getBlockState(), 3);
            }
            member.setReceiverPos(null);
            unlinkedCount++;
        }
        PlayerMessage.actionBar(player, Component.literal(
                "Display unlinked (" + unlinkedCount + " screens)."));
        return InteractionResult.SUCCESS;
    }
}
