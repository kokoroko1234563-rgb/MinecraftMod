package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Truth;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prime Forge: turns a candidate into the next prime at or above it, certified by its method card.
 * The card is a statement "H(n) → P(n)" with one variable, such as the decider link
 * "isPrime(n) = 1 → Prime(n)" (trial division) or a conjectured Fermat test; the forge tries
 * n, n + 1, … until the premises hold. A better method card is a cheaper forge; a false
 * conjectured method fails the first time it is caught (Carmichael numbers such as 561).
 */
public class PrimeForgeBlockEntity extends MachineBlockEntity {
    /** Candidates tried per run before giving up. */
    public static final int MAX_CANDIDATES = 10_000;
    private static final long MAX_STEPS = 400_000_000;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.candidate"), MachineLayout.input(62, 31, "slot.axiomatic.method_card"),
            MachineLayout.output(116, 31, null)), List.of());

    public PrimeForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.PRIME_FORGE.type(), pos, state, 1_000_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> stack.is(ModRegistries.NUMERAL_ITEM.get());
            case 1 -> stack.is(ModRegistries.STATEMENT_ITEM.get());
            default -> false;
        };
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        ItemStack in = items.get(0);
        StatementData card = MathItems.statement(items.get(1));
        if (in.isEmpty() || card == null) {
            return null;
        }
        NumeralData candidate = NumeralItem.data(in);
        Object key = java.util.List.of(candidate, card);
        if (!computing() && (failedBefore(key) || !items.get(2).isEmpty()
                && items.get(2).getCount() >= items.get(2).getMaxStackSize())) {
            return null;
        }
        MathState state = data(level).state();
        String variable;
        Cards.Use use;
        try {
            variable = methodVariable(card);
            use = Cards.prepare(state.library(), card);
        } catch (ProofException e) {
            setStatus(Component.literal(e.getMessage()));
            rememberFailure(key);
            return null;
        }
        var sig = state.signature().copy();
        var deciders = state.deciders().copy(sig);
        return runAsync(() -> search(sig, deciders, use, variable, candidate.value()), r -> {
            ItemStack now = items.get(0);
            if (now.isEmpty() || !NumeralItem.data(now).equals(candidate)) {
                return null;
            }
            long cost = Math.max(1, r.steps());
            if (r.outcome() != null && r.outcome().commit(state.library())) {
                // This candidate refuted the method's conjecture: the candidate is used up.
                data(level).setDirty();
                setStatus(r.outcome().message());
                now.shrink(1);
                return null;
            }
            if (r.outcome() == null || r.outcome().fact() == null) {
                setStatus(r.tooBig() ? Component.translatable("status.axiomatic.too_big")
                        : Component.translatable("status.axiomatic.forge_none", MAX_CANDIDATES));
                rememberFailure(key);
                return null;
            }
            BigInteger prime = r.outcome().fact().args().get(0);
            List<ItemStack> out = List.of(NumeralItem.stack(NumeralData.ZERO.withValue(prime).withFact(r.outcome().fact()), 1));
            if (!outputsFit(out)) {
                return null;
            }
            now.shrink(1);
            setStatus(Component.translatable("status.axiomatic.forged", NumeralItem.display(prime), cost));
            return new Job(cost, (int) Math.clamp(cost / 200, 10, 600), out);
        });
    }

    private record Forged(Cards.Outcome outcome, long steps, boolean tooBig) {}

    /**
     * Worker thread: tries candidates upward. Stops at the first one the method certifies, or at
     * the first one that refutes the method's conjecture.
     */
    private static Forged search(io.github.kokoroko1234563.axiomatic.core.logic.Signature sig,
            io.github.kokoroko1234563.axiomatic.core.knowledge.Deciders deciders, Cards.Use use, String variable,
            BigInteger start) {
        Evaluator evaluator = new Evaluator(sig, MAX_STEPS);
        BigInteger value = start;
        try {
            for (int i = 0; i < MAX_CANDIDATES; i++, value = value.add(BigInteger.ONE)) {
                Cards.Outcome outcome = use.evaluate(Map.of(variable, value), List.of(), true,
                        new Truth(sig, deciders, evaluator, value));
                if (outcome.fact() != null || outcome.refutedConjecture() != null) {
                    return new Forged(outcome, evaluator.steps(), false);
                }
            }
            return new Forged(null, evaluator.steps(), false);
        } catch (Evaluator.OutOfBudget e) {
            return new Forged(null, evaluator.steps(), true);
        }
    }

    /** The card's one variable, which the forge counts up; its conclusion must be P(variable). */
    private static String methodVariable(StatementData card) {
        Formula conclusion = card.formula();
        while (conclusion instanceof Formula.Implies imp) {
            conclusion = imp.conclusion();
        }
        var vars = Syntax.freeVars(card.formula());
        if (!(conclusion instanceof Formula.Pred p) || p.args().size() != 1 || !(p.args().get(0) instanceof Term.Var v)
                || vars.size() != 1) {
            throw new ProofException(Component.translatable("status.axiomatic.method_shape").getString());
        }
        return v.name();
    }
}
