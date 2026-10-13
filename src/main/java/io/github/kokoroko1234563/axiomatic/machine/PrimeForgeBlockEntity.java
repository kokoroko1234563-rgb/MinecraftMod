package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
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
    private static final long MAX_STEPS = 50_000_000;
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
        MathState state = data(level).state();
        String variable;
        try {
            variable = methodVariable(card);
            Cards.usable(state.library(), card);
        } catch (ProofException e) {
            setStatus(Component.literal(e.getMessage()));
            return null;
        }
        BigInteger start = NumeralItem.data(in).value();
        Evaluator evaluator = new Evaluator(state.signature(), MAX_STEPS);
        Fact found = null;
        BigInteger value = start;
        try {
            for (int i = 0; i < MAX_CANDIDATES && found == null; i++, value = value.add(BigInteger.ONE)) {
                Truth truth = new Truth(state.signature(), state.deciders(), evaluator, value);
                try {
                    found = Cards.apply(state.library(), card, Map.of(variable, value), List.of(), true, truth,
                            () -> data(level).setDirty());
                } catch (ProofException e) {
                    if (!state.library().refuted(java.util.Set.copyOf(card.dependencies())).isEmpty()) {
                        // This candidate refuted the method's conjecture: stop here.
                        setStatus(Component.literal(e.getMessage()));
                        in.shrink(1);
                        return null;
                    }
                    // The method does not vouch for this candidate: try the next one.
                }
            }
        } catch (Evaluator.OutOfBudget e) {
            setStatus(Component.translatable("status.axiomatic.too_big"));
            return null;
        }
        if (found == null) {
            setStatus(Component.translatable("status.axiomatic.forge_none", MAX_CANDIDATES));
            return null;
        }
        BigInteger prime = found.args().get(0);
        List<ItemStack> out = List.of(NumeralItem.stack(NumeralData.ZERO.withValue(prime).withFact(found), 1));
        if (!outputsFit(out)) {
            return null;
        }
        in.shrink(1);
        long cost = Math.max(1, evaluator.steps());
        setStatus(Component.translatable("status.axiomatic.forged", NumeralItem.display(prime), cost));
        return new Job(cost, (int) Math.clamp(cost / 200, 10, 600), out);
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
