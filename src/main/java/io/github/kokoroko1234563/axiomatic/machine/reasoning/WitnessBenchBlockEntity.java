package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Witness Bench: proves "∃k. φ(k)" from a witness t and a proof of φ(t). The target may also be a
 * property defined by an ∃, such as Even(Double(n)); the bench unfolds it, proves the ∃ and
 * folds it back, which is how the opening hour's first proof works.
 */
public class WitnessBenchBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.target"), MachineLayout.input(44, 31, "slot.axiomatic.witness"),
            MachineLayout.input(62, 31, "slot.axiomatic.instance"), MachineLayout.output(116, 31, null)), List.of());

    public WitnessBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.WITNESS_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(0, 2);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1, 2);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        return List.of(ctx.proven(prove(ctx.state().kernel(), ctx.formula(0), ctx.term(1), ctx.theorem(2))));
    }

    /** The bench's rule, also used by tests. */
    public static Theorem prove(Kernel kernel, Formula target, Term witness, Theorem instance) {
        if (target instanceof Formula.Exists ex) {
            return kernel.existsIntro(ex, witness, instance);
        }
        if (target instanceof Formula.Pred p
                && kernel.signature().get(p.name()) instanceof Definition.Predicate def
                && def.body() instanceof Formula.Exists) {
            // Unfold P(args) to its ∃ body, prove that, then fold back with the definition.
            Theorem fold = kernel.definingStatements(p.name()).get(1); // body → P(params)
            java.util.Map<String, Term> assignment = new java.util.HashMap<>();
            for (int i = 0; i < def.params().size(); i++) {
                assignment.put(def.params().get(i), p.args().get(i));
            }
            Theorem foldHere = kernel.instantiate(fold, assignment);
            Formula.Exists body = (Formula.Exists) ((Formula.Implies) foldHere.formula()).premise();
            Theorem proven = kernel.modusPonens(foldHere, kernel.existsIntro(body, witness, instance));
            if (!Syntax.alphaEquals(proven.formula(), target)) {
                throw new ProofException("Unexpected result " + proven.formula());
            }
            return proven;
        }
        throw new ProofException(Component.translatable("status.axiomatic.needs_exists").getString());
    }
}
