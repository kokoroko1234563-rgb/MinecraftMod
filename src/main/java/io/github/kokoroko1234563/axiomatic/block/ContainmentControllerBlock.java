package io.github.kokoroko1234563.axiomatic.block;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Containment Chamber controller. A conjecture block sealed on all six sides by obsidian, tinted
 * glass or a controller, with a controller within {@link #RANGE} blocks, fails harmlessly: its
 * disaster stays inside the chamber.
 */
public class ContainmentControllerBlock extends Block {
    public static final int RANGE = 4;

    public ContainmentControllerBlock(Properties properties) {
        super(properties);
    }

    /** Whether a disaster at {@code pos} is contained. */
    public static boolean contains(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState wall = level.getBlockState(pos.relative(d));
            if (!wall.is(Blocks.OBSIDIAN) && !wall.is(Blocks.TINTED_GLASS) && !wall.is(ModRegistries.CONTAINMENT_CONTROLLER.get())) {
                return false;
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-RANGE, -RANGE, -RANGE), pos.offset(RANGE, RANGE, RANGE))) {
            if (level.getBlockState(p).is(ModRegistries.CONTAINMENT_CONTROLLER.get())) {
                return true;
            }
        }
        return false;
    }
}
