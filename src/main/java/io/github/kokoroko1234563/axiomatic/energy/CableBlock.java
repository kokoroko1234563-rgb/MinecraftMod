package io.github.kokoroko1234563.axiomatic.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Copper Cable: carries energy from generators to machines. A cable is just a block; the network
 * is found by searching through connected cables (see {@link EnergyNetwork}). It draws arms toward
 * neighbouring cables and machines, like a pipe.
 *
 * <p>Using a cable with an empty hand switches its phase (A or B). A network whose cables use
 * both phases runs alternating power: twice as fast once a Network Surveyor has checked that
 * neighbouring cables always differ (a two-colouring), a short circuit until then.
 */
public class CableBlock extends Block {
    public static final BooleanProperty PHASE = BooleanProperty.create("phase");
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    /** The arm toward each side, drawn when a cable or machine is there. */
    private static final java.util.Map<Direction, VoxelShape> ARMS = java.util.Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5), Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11), Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11), Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
    private static final java.util.Map<BlockState, VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public CableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(PHASE, false);
        for (BooleanProperty side : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }

    /** Cables join cables and machines (whatever can send or take energy); the look only. */
    private static boolean connects(BlockState neighbour) {
        return neighbour.getBlock() instanceof CableBlock
                || neighbour.getBlock() instanceof io.github.kokoroko1234563.axiomatic.machine.MachineBlock;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d),
                    connects(context.getLevel().getBlockState(context.getClickedPos().relative(d))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
            BlockState neighbourState, net.minecraft.util.RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connects(neighbourState));
    }

    /** Phase B (true) or phase A (false). */
    public static boolean phase(BlockState state) {
        return state.getValue(PHASE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockState next = state.cycle(PHASE);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            player.sendOverlayMessage(Component.translatable(phase(next) ? "message.axiomatic.phase_b"
                    : "message.axiomatic.phase_a"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, st -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if (st.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d))) {
                    shape = net.minecraft.world.phys.shapes.Shapes.or(shape, ARMS.get(d));
                }
            }
            return shape;
        });
    }
}
