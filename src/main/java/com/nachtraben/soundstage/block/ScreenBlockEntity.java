package com.nachtraben.soundstage.block;

import com.nachtraben.soundstage.audio.SpeakerAudience;
import com.nachtraben.soundstage.audio.video.ScreenDisplays;
import com.nachtraben.soundstage.core.video.DisplayLayout;
import com.nachtraben.soundstage.core.video.EpochCache;
import com.nachtraben.soundstage.registry.ModBlockEntities;
import dev.armadeus.armalib.platform.block.SaveView;
import com.nachtraben.soundstage.util.WorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ScreenBlockEntity extends BlockEntity implements dev.armadeus.armalib.platform.block.LoadAware {

    public static final int AUDIENCE_RANGE = 64;

    public enum FitMode {
        FIT,
        FILL,
    }

    @Nullable
    private BlockPos receiverPos;

    private FitMode fitMode = FitMode.FIT;

    private static final AtomicLong SERVER_LAYOUT_EPOCH = new AtomicLong();
    private static final AtomicLong CLIENT_LAYOUT_EPOCH = new AtomicLong();

    static void bumpEpoch(Level level) {
        (level.isClientSide() ? CLIENT_LAYOUT_EPOCH : SERVER_LAYOUT_EPOCH).incrementAndGet();
    }

    private static long currentEpoch(Level level) {
        return (level.isClientSide() ? CLIENT_LAYOUT_EPOCH : SERVER_LAYOUT_EPOCH).get();
    }

    private final EpochCache<DisplayLayout> displayLayoutCache = new EpochCache<>();

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public BlockPos getReceiverPos() { return receiverPos; }

    public void setReceiverPos(@Nullable BlockPos receiverPos) {
        if (Objects.equals(this.receiverPos, receiverPos)) return;
        this.receiverPos = receiverPos;
        setChanged();
        syncToClients();
    }

    public FitMode getFitMode() { return fitMode; }

    public void setFitMode(FitMode fitMode) {
        if (this.fitMode == fitMode) return;
        this.fitMode = fitMode;
        setChanged();
        syncToClients();
    }

    public static BlockPos anchorPosition(BlockPos start, Direction facing, DisplayLayout layout) {
        return start.relative(rightAxis(facing), layout.anchorCol() - layout.startCol())
                .relative(Direction.UP, layout.anchorRow() - layout.startRow());
    }

    @Nullable
    public DisplayLayout getDisplayLayout() {
        Level currentLevel = level;
        if (currentLevel == null) {
            return null;
        }
        Direction facing = getBlockState().getValue(ScreenBlock.FACING);
        long key = (currentEpoch(currentLevel) << 3) | (facing.ordinal() & 0x7);
        return displayLayoutCache.get(key, () -> findDisplay(currentLevel, getBlockPos(), facing));
    }

    @Override
    public void onLoad() {
        //? if neoforge
        super.onLoad();
        if (level != null) {
            bumpEpoch(level);
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    //? if <1.21.11 {
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }
    //?}

    //? if >=1.21.11 {
    /*@Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        save(SaveView.writer(output));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        load(SaveView.reader(input));
    }
    *///?} else if >=1.20.5 {
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(SaveView.writer(tag));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(SaveView.reader(tag));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        save(SaveView.writer(tag));
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        load(SaveView.reader(tag));
    }
    *///?}

    private void save(SaveView.Writer out) {
        if (receiverPos != null) {
            out.putLong("receiverPos", receiverPos.asLong());
        }
        out.putInt("fitMode", fitMode.ordinal());
    }

    private void load(SaveView.Reader in) {
        Long storedReceiver = in.getLongOrNull("receiverPos");
        receiverPos = storedReceiver == null ? null : BlockPos.of(storedReceiver);
        FitMode[] modes = FitMode.values();
        int storedFitMode = in.getInt("fitMode", FitMode.FIT.ordinal());
        fitMode = storedFitMode >= 0 && storedFitMode < modes.length ? modes[storedFitMode] : FitMode.FIT;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    //? if >=1.20.5 {
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    //?} else {
    /*public CompoundTag getUpdateTag() {
    *///?}
        //? if >=1.21.11 {
        /*CompoundTag tag = saveCustomOnly(registries);
        *///?} else if >=1.20.5 {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        //?} else {
        /*CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        *///?}
        return tag;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 4 == 0) {
            ServerLevel serverLevel = (ServerLevel) level;
            publishAudience(serverLevel, pos);
            if (receiverPos != null) {
                DisplayLayout layout = getDisplayLayout();
                if (layout != null) {
                    ScreenDisplays.publish(serverLevel, pos, layout.heightCells());
                }
            }
        }
    }

    private void publishAudience(ServerLevel level, BlockPos pos) {
        double reachSqr = (double) AUDIENCE_RANGE * AUDIENCE_RANGE;
        List<ServerPlayer> listeners = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < reachSqr) {
                listeners.add(player);
            }
        }
        SpeakerAudience.publish(level, pos, listeners);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            bumpEpoch(level);
        }
        if (level != null && !level.isClientSide()) {
            SpeakerAudience.forget(level, getBlockPos());
            ScreenDisplays.forget(level, getBlockPos());
        }
    }

    static Direction rightAxis(Direction facing) {
        return facing.getCounterClockWise();
    }

    static BlockPos[] inPlaneNeighbours(BlockPos pos, Direction facing) {
        Direction right = rightAxis(facing);
        return new BlockPos[] {
                pos.relative(right), pos.relative(right.getOpposite()),
                pos.above(), pos.below(),
        };
    }

    private static long packPos(BlockPos pos, Direction facing) {
        Direction right = rightAxis(facing);
        return DisplayLayout.pack(pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ(), pos.getY());
    }

    public static DisplayLayout findDisplay(Level level, BlockPos start, Direction facing) {
        long startCell = packPos(start, facing);
        int startCol = DisplayLayout.unpackCol(startCell);
        int startRow = DisplayLayout.unpackRow(startCell);

        Set<BlockPos> visited = new HashSet<>();
        Set<Long> cells = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(start);
        cells.add(startCell);
        frontier.add(start);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.poll();
            for (BlockPos candidate : inPlaneNeighbours(current, facing)) {
                if (!visited.add(candidate)) {
                    continue;
                }
                long cell = packPos(candidate, facing);
                int col = DisplayLayout.unpackCol(cell);
                int row = DisplayLayout.unpackRow(cell);
                if (Math.abs(col - startCol) > DisplayLayout.MAX_SPAN - 1
                        || Math.abs(row - startRow) > DisplayLayout.MAX_SPAN - 1) {
                    continue;
                }
                if (WorldAccess.blockEntityIfLoaded(level, candidate) instanceof ScreenBlockEntity screen
                        && screen.getBlockState().getValue(ScreenBlock.FACING) == facing) {
                    cells.add(cell);
                    frontier.add(candidate);
                }
            }
        }
        return DisplayLayout.of(cells, startCell, DisplayLayout.MAX_SPAN);
    }

    public static List<BlockPos> memberPositions(BlockPos start, Direction facing, DisplayLayout layout) {
        Direction right = rightAxis(facing);
        List<BlockPos> positions = new ArrayList<>();
        for (int col = 0; col < layout.widthCells(); col++) {
            for (int row = 0; row < layout.heightCells(); row++) {
                if (!layout.contains(col, row)) {
                    continue;
                }
                positions.add(start.relative(right, col - layout.startCol())
                        .relative(Direction.UP, row - layout.startRow()));
            }
        }
        return positions;
    }
}
