package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Truth;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
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
        if (!io.github.kokoroko1234563.axiomatic.core.logic.Signature.isFunction(def) || def instanceof Definition.Abstract) {
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
        StatementData card = MathItems.statement(items.get(CARD));
        Key key = new Key(f, inputs, card);
        if (!computing() && (failedBefore(key) || !items.get(OUTPUT).isEmpty()
                && items.get(OUTPUT).getCount() >= items.get(OUTPUT).getMaxStackSize())) {
            return null;
        }
        Cards.Use use = null;
        if (card != null) {
            try {
                use = Cards.prepare(state.library(), card);
            } catch (ProofException e) {
                setStatus(Component.literal(e.getMessage()));
                rememberFailure(key);
                return null;
            }
        }
        // Compute on a worker thread with copies of the definitions; commit here.
        var sig = state.signature().copy();
        var deciders = state.deciders().copy(sig);
        Cards.Use cardUse = use;
        return runAsync(() -> run(sig, deciders, f, inputs, cardUse), r -> commit(level, state, key, arity, r));
    }

    /** What the inputs that started a computation were, to check they are still there at commit. */
    private record Key(String function, List<NumeralData> inputs, StatementData card) {}

    private record Result(BigInteger value, long steps, Cards.Outcome card, boolean tooBig) {}

    /** Worker thread: computes f and applies the card. */
    private static Result run(io.github.kokoroko1234563.axiomatic.core.logic.Signature sig,
            io.github.kokoroko1234563.axiomatic.core.knowledge.Deciders deciders, String f, List<NumeralData> inputs,
            Cards.Use use) {
        Evaluator evaluator = new Evaluator(sig, MAX_STEPS);
        try {
            BigInteger value = evaluator.apply(f, inputs.stream().map(NumeralData::value).toList());
            Cards.Outcome outcome = null;
            if (use != null) {
                Formula conclusion = use.card().formula();
                while (conclusion instanceof Formula.Implies imp) {
                    conclusion = imp.conclusion();
                }
                if (!(conclusion instanceof Formula.Pred p) || p.args().size() != 1
                        || !(p.args().get(0) instanceof Term.App app) || !app.function().equals(f)
                        || !app.args().stream().allMatch(t -> t instanceof Term.Var)) {
                    outcome = new Cards.Outcome(null, Component.translatable("status.axiomatic.card_mismatch", f), null, null);
                } else {
                    Map<String, BigInteger> assignment = new HashMap<>();
                    for (int i = 0; i < app.args().size(); i++) {
                        assignment.put(((Term.Var) app.args().get(i)).name(), inputs.get(i).value());
                    }
                    List<Fact> premises = new ArrayList<>();
                    inputs.forEach(n -> premises.addAll(n.facts()));
                    // Witnesses are searched up to the largest number involved, as on the Test Bench.
                    BigInteger cap = inputs.stream().map(NumeralData::value).reduce(value, BigInteger::max);
                    // Typed ports: a card's premises about the inputs must be met by their certificates.
                    outcome = use.evaluate(assignment, premises, false, new Truth(sig, deciders, evaluator, cap));
                }
            }
            return new Result(value, evaluator.steps(), outcome, false);
        } catch (Evaluator.OutOfBudget e) {
            return new Result(null, evaluator.steps(), null, true);
        }
    }

    /** Server thread: turns the result into a job if the inputs are still the same. */
    private Job commit(ServerLevel level, MathState state, Key key, int arity, Result r) {
        for (int i = 0; i < arity; i++) {
            ItemStack in = items.get(1 + i);
            if (in.isEmpty() || !NumeralItem.data(in).equals(key.inputs().get(i))) {
                return null;
            }
        }
        if (r.tooBig()) {
            setStatus(Component.translatable("status.axiomatic.too_big"));
            rememberFailure(key);
            return null;
        }
        NumeralData result = NumeralData.ZERO.withValue(r.value());
        long cost = Math.max(1, r.steps());
        if (r.card() != null) {
            if (r.card().commit(state.library())) {
                data(level).setDirty();
                playSound(ModRegistries.SOUND_REFUTED, 1.0f);
                setStatus(r.card().message());
                rememberFailure(key);
                return null;
            }
            if (r.card().fact() == null) {
                setStatus(r.card().message());
                rememberFailure(key);
                return null;
            }
            result = result.withFact(r.card().fact());
            cost += 1;
        }
        List<ItemStack> out = List.of(NumeralItem.stack(result, 1));
        if (!outputsFit(out)) {
            return null;
        }
        for (int i = 0; i < arity; i++) {
            items.get(1 + i).shrink(1);
        }
        setStatus(Component.translatable("status.axiomatic.computed", NumeralItem.display(r.value()), cost));
        return new Job(cost, (int) Math.clamp(cost / 100, 4, 400), out);
    }
}
