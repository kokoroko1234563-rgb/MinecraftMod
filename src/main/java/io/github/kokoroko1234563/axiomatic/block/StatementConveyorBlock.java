package io.github.kokoroko1234563.axiomatic.block;

import com.mojang.serialization.MapCodec;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Statement Conveyor: moves statement and circuit items from the container behind it to the one
 * in front, one every few ticks. Factories use it to apply theorems to streams of items.
 */
public class StatementConveyorBlock extends DirectionalBlock {
    public static final MapCodec<StatementConveyorBlock> CODEC = simpleCodec(StatementConveyorBlock::new);
    private static final int INTERVAL = 8;

    public StatementConveyorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Items flow away from the player who places it.
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, INTERVAL);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        Container from = HopperBlockEntity.getContainerAt(level, pos.relative(facing.getOpposite()));
        Container to = HopperBlockEntity.getContainerAt(level, pos.relative(facing));
        if (from != null && to != null) {
            moveOne(from, to, facing);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    private static void moveOne(Container from, Container to, Direction facing) {
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty() || !(stack.is(ModRegistries.STATEMENT_ITEM.get()) || stack.is(ModRegistries.CIRCUIT_ITEM.get()))) {
                continue;
            }
            if (from instanceof WorldlyContainer w && !w.canTakeItemThroughFace(slot, stack, facing)) {
                continue;
            }
            ItemStack one = stack.copyWithCount(1);
            ItemStack rest = HopperBlockEntity.addItem(from, to, one, facing.getOpposite());
            if (rest.isEmpty()) {
                stack.shrink(1);
                from.setChanged();
                return;
            }
        }
    }
}
