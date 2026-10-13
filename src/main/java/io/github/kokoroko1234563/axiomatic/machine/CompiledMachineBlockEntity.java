package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Certifier;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Truth;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
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
 * Compiled Machine: runs a player-defined function on numerals. Put a circuit f(…) in the design
 * slot to choose the function; its inputs go in port order. A theorem card (a proven statement
 * about f, such as "Even(Double(n))" or "Even(a) → Even(b) → Even(a + b)") in the card slot
 * certifies each output for 1 unit, passing certificates through the factory. A conjectural card
 * works too, but every use is checked in the background: a counterexample is a disaster.
 */
public class CompiledMachineBlockEntity extends MachineBlockEntity {
    public static final int DESIGN = 0;
    public static final int CARD = 4;
    public static final int OUTPUT = 5;
    private static final long MAX_STEPS = 5_000_000;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 51, "slot.axiomatic.design"),
            MachineLayout.input(26, 31, "slot.axiomatic.port_1"), MachineLayout.input(44, 31, "slot.axiomatic.port_2"),
            MachineLayout.input(62, 31, "slot.axiomatic.port_3"),
            MachineLayout.input(62, 51, "slot.axiomatic.card"), MachineLayout.output(116, 31, null)), List.of());

    public CompiledMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.COMPILED_MACHINE.type(), pos, state, 100_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        String f = function();
        return f == null ? Component.translatable("label.axiomatic.no_design") : Component.translatable("label.axiomatic.computes", f);
    }

    /** The function chosen by the design slot, or null. */
    public String function() {
        return MathItems.termOf(items.get(DESIGN)) instanceof Term.App a ? a.function() : null;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return switch (slot) {
            case DESIGN -> stack.is(ModRegistries.CIRCUIT_ITEM.get());
            case CARD -> stack.is(ModRegistries.STATEMENT_ITEM.get());
            case OUTPUT -> false;
            default -> stack.is(ModRegistries.NUMERAL_ITEM.get());
        };
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        String f = function();
        if (f == null) {
            return null;
        }
        MathState state = data(level).state();
        Definition def = state.signature().get(f);
        if (def == null || def instanceof Definition.Predicate) {
            setStatus(Component.translatable("status.axiomatic.unknown_function", f));
            return null;
        }
        int arity = def.params().size();
        if (arity > 3) {
            setStatus(Component.translatable("status.axiomatic.too_many_ports", f));
            return null;
        }
        List<NumeralData> inputs = new ArrayList<>();
        for (int i = 0; i < arity; i++) {
            ItemStack in = items.get(1 + i);
            if (in.isEmpty()) {
                return null;
            }
            inputs.add(NumeralItem.data(in));
        }
        Evaluator evaluator = new Evaluator(state.signature(), MAX_STEPS);
        BigInteger value;
        try {
            value = evaluator.apply(f, inputs.stream().map(NumeralData::value).toList());
        } catch (Evaluator.OutOfBudget e) {
            setStatus(Component.translatable("status.axiomatic.too_big"));
            return null;
        }
        NumeralData result = NumeralData.ZERO.withValue(value);
        long cost = Math.max(1, evaluator.steps());

        StatementData card = MathItems.statement(items.get(CARD));
        if (card != null) {
            try {
                Fact fact = certify(level, state, f, card, inputs, evaluator, value);
                result = result.withFact(fact);
                cost += 1;
            } catch (ProofException e) {
                setStatus(Component.literal(e.getMessage()));
                return null;
            }
        }
        List<ItemStack> out = List.of(NumeralItem.stack(result, 1));
        if (!outputsFit(out)) {
            return null;
        }
        for (int i = 0; i < arity; i++) {
            items.get(1 + i).shrink(1);
        }
        setStatus(Component.translatable("status.axiomatic.computed", NumeralItem.display(value), cost));
        return new Job(cost, (int) Math.clamp(cost / 100, 4, 400), out);
    }

    /**
     * Applies a theorem card. The card must conclude a property of f applied to its variables;
     * those variables take this machine's inputs in order. Premises are matched against the
     * inputs' certificates.
     */
    private Fact certify(ServerLevel level, MathState state, String f, StatementData card, List<NumeralData> inputs,
            Evaluator evaluator, BigInteger output) {
        Library library = state.library();
        BigInteger testedUpTo = null;
        if (!card.proven()) {
            // A Tested statement works as a card inside its tested range.
            if (library.status(card.formula()) instanceof Library.Tested t) {
                testedUpTo = t.upTo();
            } else {
                throw new ProofException(Component.translatable("status.axiomatic.card_unproven").getString());
            }
        } else if (!card.hypotheses().isEmpty()) {
            throw new ProofException(Component.translatable("status.axiomatic.card_unproven").getString());
        }
        Set<String> refuted = library.refuted(Set.copyOf(card.dependencies()));
        if (!refuted.isEmpty()) {
            throw new ProofException(Component.translatable("status.axiomatic.card_refuted", String.join(", ", refuted)).getString());
        }
        for (String dep : card.dependencies()) {
            if (dep.startsWith("conjecture:")) {
                Library.Conjecture c = library.conjectureNamed(dep.substring("conjecture:".length()));
                if (c != null && c.state() == Library.ConjectureState.DORMANT) {
                    throw new ProofException(Component.translatable("status.axiomatic.card_dormant", c.name()).getString());
                }
            }
        }
        Formula conclusion = card.formula();
        while (conclusion instanceof Formula.Implies imp) {
            conclusion = imp.conclusion();
        }
        if (!(conclusion instanceof Formula.Pred p) || p.args().size() != 1
                || !(p.args().get(0) instanceof Term.App app) || !app.function().equals(f)
                || !app.args().stream().allMatch(t -> t instanceof Term.Var)) {
            throw new ProofException(Component.translatable("status.axiomatic.card_mismatch", f).getString());
        }
        Map<String, BigInteger> assignment = new HashMap<>();
        for (int i = 0; i < app.args().size(); i++) {
            assignment.put(((Term.Var) app.args().get(i)).name(), inputs.get(i).value());
        }
        if (testedUpTo != null) {
            for (BigInteger v : assignment.values()) {
                if (v.compareTo(testedUpTo) > 0) {
                    throw new ProofException(Component.translatable("status.axiomatic.outside_tested",
                            testedUpTo.toString()).getString());
                }
            }
        }
        List<Fact> premises = new ArrayList<>();
        inputs.forEach(n -> premises.addAll(n.facts()));
        // Witnesses are searched up to the largest number involved, as on the Test Bench.
        BigInteger cap = inputs.stream().map(NumeralData::value).reduce(output, BigInteger::max);
        Truth truth = new Truth(state.signature(), state.deciders(), evaluator, cap);

        // Background check: a conjecture applied to numbers is computed for those numbers.
        for (Library.Conjecture c : library.conjectures()) {
            if (c.state() == Library.ConjectureState.ACTIVE && card.dependencies().contains("conjecture:" + c.name())
                    && Syntax.alphaEquals(c.statement(), card.formula())) {
                if (library.observe(c.name(), assignment, truth)) {
                    data(level).setDirty();
                    throw new ProofException(Component.translatable("status.axiomatic.refuted_now", c.name()).getString());
                }
            }
        }
        return Certifier.byStatement(card.formula(), Set.copyOf(card.dependencies()), assignment, premises, truth);
    }
}
