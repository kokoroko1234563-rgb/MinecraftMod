package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Modus Ponens Press: "P → Q" and "P" give "Q". The implication stays, like a tool. */
public class ModusPonensPressBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 35, "slot.axiomatic.implication"), MachineLayout.input(44, 35, "slot.axiomatic.premise"),
            MachineLayout.output(116, 35, null)), List.of());

    public ModusPonensPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.MODUS_PONENS_PRESS.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(1);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        return List.of(ctx.proven(ctx.state().kernel().modusPonens(ctx.theorem(0), ctx.theorem(1))));
    }
}
