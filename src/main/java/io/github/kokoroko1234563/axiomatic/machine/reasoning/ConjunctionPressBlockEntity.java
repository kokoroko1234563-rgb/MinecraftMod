package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/** Conjunction Press: "P" and "Q" give "P ∧ Q"; a lone "P ∧ Q" is split into "P" and "Q". */
public class ConjunctionPressBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.left"), MachineLayout.input(44, 31, "slot.axiomatic.right"),
            MachineLayout.output(116, 31, null), MachineLayout.output(134, 31, null)), List.of());

    public ConjunctionPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CONJUNCTION_PRESS.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return items.get(1).isEmpty() ? List.of(0) : List.of(0, 1);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        Kernel k = ctx.state().kernel();
        Theorem left = ctx.theorem(0);
        if (items.get(1).isEmpty()) {
            if (!(left.formula() instanceof Formula.And)) {
                throw new io.github.kokoroko1234563.axiomatic.core.logic.ProofException(
                        net.minecraft.network.chat.Component.translatable("status.axiomatic.needs_and").getString());
            }
            return List.of(ctx.proven(k.andLeft(left)), ctx.proven(k.andRight(left)));
        }
        return List.of(ctx.proven(k.andIntro(left, ctx.theorem(1))));
    }
}
