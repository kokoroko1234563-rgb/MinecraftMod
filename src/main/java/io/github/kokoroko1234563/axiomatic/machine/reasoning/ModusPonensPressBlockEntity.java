package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Modus Ponens Press: "P → Q" and "P" give "Q". The implication stays, like a tool. With a proof
 * of ⊥ in the first slot, any statement in the second slot comes out proven (ex falso).
 */
public class ModusPonensPressBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.implication"), MachineLayout.input(44, 31, "slot.axiomatic.premise"),
            MachineLayout.output(116, 31, null)), List.of());

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
        var first = io.github.kokoroko1234563.axiomatic.item.MathItems.statement(items.get(0));
        if (first != null && first.proven() && first.formula() instanceof io.github.kokoroko1234563.axiomatic.core.logic.Formula.Bottom) {
            // Ex falso: from ⊥, the statement in the second slot (proven or not) follows.
            return List.of(ctx.proven(ctx.state().kernel().exFalso(ctx.theorem(0), ctx.formula(1))));
        }
        return List.of(ctx.proven(ctx.state().kernel().modusPonens(ctx.theorem(0), ctx.theorem(1))));
    }
}
