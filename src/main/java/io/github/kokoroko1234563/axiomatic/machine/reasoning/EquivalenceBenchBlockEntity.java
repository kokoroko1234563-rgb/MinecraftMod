package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Equivalence Bench: recognises a rebuilt design. Give it two designs, as circuits f(…) and g(…)
 * or statements P(…) and Q(…); if their defining equations are the same up to names, it proves
 * f(x̄) = g(x̄) (or P ⇔ Q as two implications), so every theorem about one applies to the other.
 * The designs stay in their slots.
 */
public class EquivalenceBenchBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.design_a"), MachineLayout.input(44, 31, "slot.axiomatic.design_b"),
            MachineLayout.output(116, 31, null), MachineLayout.output(134, 31, null)), List.of());

    public EquivalenceBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.EQUIVALENCE_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of();
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        if (!items.get(2).isEmpty() || !items.get(3).isEmpty()) {
            return List.of();
        }
        return ctx.state().kernel().sameDesign(name(ctx, 0), name(ctx, 1)).stream().map(ctx::proven).toList();
    }

    private String name(Context ctx, int slot) {
        if (MathItems.termOf(items.get(slot)) instanceof Term.App a) {
            return a.function();
        }
        var st = MathItems.statement(items.get(slot));
        if (st != null && st.formula() instanceof Formula.Pred p) {
            return p.name();
        }
        throw new ProofException(Component.translatable("status.axiomatic.needs_design").getString());
    }
}
