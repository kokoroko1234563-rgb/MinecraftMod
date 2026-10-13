package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for reasoning machines: each one applies one kernel rule to its input slots. Inputs listed
 * in {@link #consumed()} are used up (the statement being transformed); the others are tools that
 * stay (the equation applied, a variable, a circuit). Applying a rule costs 1 energy unit.
 */
public abstract class ReasoningBlockEntity extends MachineBlockEntity {
    /** Everything a rule needs: the world's mathematics and the input slots. */
    public final class Context {
        final MathState state;
        final Printer printer;

        Context(MathState state) {
            this.state = state;
            this.printer = new Printer(state.signature());
        }

        public MathState state() {
            return state;
        }

        public Printer printer() {
            return printer;
        }

        public ItemStack slot(int i) {
            return items.get(i);
        }

        /** The proven statement in slot i; fails with a message if there is none. */
        public Theorem theorem(int i) {
            StatementData data = MathItems.statement(items.get(i));
            if (data == null || !data.proven()) {
                throw new ProofException(Component.translatable("status.axiomatic.needs_proven", i + 1).getString());
            }
            var refuted = state.library().refuted(java.util.Set.copyOf(data.dependencies()));
            if (!refuted.isEmpty()) {
                throw new ProofException(Component.translatable("status.axiomatic.card_refuted",
                        String.join(", ", refuted)).getString());
            }
            return data.theorem(state.kernel());
        }

        /** The statement in slot i, proven or not. */
        public Formula formula(int i) {
            StatementData data = MathItems.statement(items.get(i));
            if (data == null) {
                throw new ProofException(Component.translatable("status.axiomatic.needs_statement", i + 1).getString());
            }
            return data.formula();
        }

        public Term term(int i) {
            Term t = MathItems.termOf(items.get(i));
            if (t == null) {
                throw new ProofException(Component.translatable("status.axiomatic.needs_circuit", i + 1).getString());
            }
            return t;
        }

        public String variable(int i) {
            if (!(MathItems.termOf(items.get(i)) instanceof Term.Var v)) {
                throw new ProofException(Component.translatable("status.axiomatic.needs_variable", i + 1).getString());
            }
            return v.name();
        }

        public ItemStack proven(Theorem thm) {
            return MathItems.statementStack(StatementData.of(thm, printer));
        }

        public ItemStack unproven(Formula f) {
            return MathItems.statementStack(StatementData.unproven(f, printer));
        }
    }

    protected ReasoningBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 10_000);
    }

    /** Which input slots are used up when the rule succeeds. */
    protected abstract List<Integer> consumed();

    /** Which input slots must be filled before the rule is tried. */
    protected abstract List<Integer> required();

    /**
     * Applies the rule. Throws {@link ProofException} with a player-readable reason on refusal;
     * returns an empty list to stay idle quietly.
     */
    protected abstract List<ItemStack> apply(Context ctx);

    /** Whether this application deserves the "proven" chime (induction, naming a theorem). */
    protected boolean milestone() {
        return false;
    }

    /** Energy for one application. */
    protected long cost(Context ctx) {
        return 1;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        if (layout().slots().get(slot).role() == MachineLayout.Role.OUTPUT) {
            return false;
        }
        return stack.is(ModRegistries.STATEMENT_ITEM.get()) || MathItems.termOf(stack) != null
                || stack.getItem() instanceof MathItems.AxiomTabletItem;
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        for (int i : required()) {
            if (items.get(i).isEmpty()) {
                return null;
            }
        }
        Context ctx = new Context(data(level).state());
        List<ItemStack> out;
        try {
            out = apply(ctx);
        } catch (ProofException e) {
            setStatus(Component.literal(e.getMessage()));
            return null;
        }
        if (out.isEmpty() || !outputsFit(out)) {
            return null;
        }
        for (int i : consumed()) {
            items.get(i).shrink(1);
        }
        data(level).setDirty();
        setStatus(Component.translatable("status.axiomatic.applied"));
        playSound(milestone() ? io.github.kokoroko1234563.axiomatic.registry.ModRegistries.SOUND_PROVEN
                : io.github.kokoroko1234563.axiomatic.registry.ModRegistries.SOUND_APPLIED, 0.6f);
        return new Job(cost(ctx), 10, out);
    }
}
