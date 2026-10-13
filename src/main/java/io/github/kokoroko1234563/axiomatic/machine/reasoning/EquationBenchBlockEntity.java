package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;

/** Equation Bench: two circuits give the (unproven) statement "left = right". */
public class EquationBenchBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.left"), MachineLayout.input(44, 31, "slot.axiomatic.right"),
            MachineLayout.output(116, 31, null)), List.of());

    public EquationBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.EQUATION_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(0, 1);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        Formula f = Formula.eq(ctx.term(0), ctx.term(1));
        ctx.state().signature().checkFormula(f);
        return List.of(ctx.unproven(f));
    }
}
