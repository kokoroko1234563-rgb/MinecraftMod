package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Instantiator: a proven statement, a variable and a circuit give the statement with the circuit put in. */
public class InstantiatorBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 35, "slot.axiomatic.statement"), MachineLayout.input(44, 35, "slot.axiomatic.variable"),
            MachineLayout.input(62, 35, "slot.axiomatic.circuit"), MachineLayout.output(116, 35, null)), List.of());

    public InstantiatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.INSTANTIATOR.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(0);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1, 2);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        return List.of(ctx.proven(ctx.state().kernel().instantiate(ctx.theorem(0), ctx.variable(1), ctx.term(2))));
    }
}
