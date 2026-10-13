package io.github.kokoroko1234563.axiomatic.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Copper Cable: carries energy from generators to machines. A cable is just a block; the network
 * is found by searching through connected cables (see {@link EnergyNetwork}).
 *
 * <p>Using a cable with an empty hand switches its phase (A or B). A network whose cables use
 * both phases runs alternating power: twice as fast once a Network Surveyor has checked that
 * neighbouring cables always differ (a two-colouring), a short circuit until then.
 */
public class CableBlock extends Block {
    public static final BooleanProperty PHASE = BooleanProperty.create("phase");
    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);

    public CableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
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
        return SHAPE;
    }
}
