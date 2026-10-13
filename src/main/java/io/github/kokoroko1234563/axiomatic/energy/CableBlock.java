package io.github.kokoroko1234563.axiomatic.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Copper Cable: carries energy from generators to machines. A cable is just a block; the network
 * is found by searching through connected cables (see {@link EnergyNetwork}).
 */
public class CableBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5, 5, 5, 11, 11, 11);

    public CableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
