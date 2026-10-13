package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Induction Engine (axiom 5): base case P(0), step P(n) → P(S(n)) and the variable n give P(n) for all n. */
public class InductionEngineBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.base"), MachineLayout.input(44, 31, "slot.axiomatic.step"),
            MachineLayout.input(62, 31, "slot.axiomatic.variable"), MachineLayout.output(116, 31, null)), List.of());

    public InductionEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.INDUCTION_ENGINE.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected boolean milestone() {
        return true;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(0, 1);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1, 2);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        return List.of(ctx.proven(ctx.state().kernel().induction(ctx.theorem(0), ctx.theorem(1), ctx.variable(2))));
    }
}
