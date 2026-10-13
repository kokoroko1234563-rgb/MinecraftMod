package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;

/**
 * Equation Bench: two circuits give the (unproven) statement "left = right"; the same circuit
 * twice gives the proven "t = t" (reflexivity of equality).
 */
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

    /** Showing an axiom or t = t is not proving anything: no "Q.E.D." advancement. */
    @Override
    protected List<String> milestones(List<ItemStack> out) {
        return List.of();
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
        if (ctx.term(0).equals(ctx.term(1))) {
            // The same circuit on both sides: equality is reflexive, so this one is proven.
            return List.of(ctx.proven(ctx.state().kernel().reflexivity(ctx.term(0))));
        }
        Formula f = Formula.eq(ctx.term(0), ctx.term(1));
        ctx.state().signature().checkFormula(f);
        return List.of(ctx.unproven(f));
    }
}
