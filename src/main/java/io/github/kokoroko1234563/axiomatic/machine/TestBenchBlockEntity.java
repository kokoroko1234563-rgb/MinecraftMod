package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Certifier;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.knowledge.TestBench;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Truth;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Test Bench. Numeral mode: tries to certify a numeral with a chosen property, by witness search
 * (or its linked decider). Every candidate costs energy, more for bigger numerals. Witnesses are
 * searched up to the numeral's own value, which covers properties like Even and ≤.
 * Range mode: a statement and a bound numeral; every case up to the bound is computed and the
 * statement becomes Tested up to the bound in the Library, or Refuted by a counterexample.
 */
public class TestBenchBlockEntity extends MachineBlockEntity {
    /** Hard limit on one search, so a huge numeral cannot freeze the server. */
    public static final long MAX_STEPS = 200_000_000;
    private static final MachineLayout LAYOUT = new MachineLayout(
            List.of(MachineLayout.input(44, 31, "slot.axiomatic.test_subject"), MachineLayout.output(116, 31, null),
                    MachineLayout.input(62, 31, "slot.axiomatic.test_bound")),
            List.of("button.axiomatic.next_property"));
    /** Range mode: at most this many cases per run, so testing cannot stall the server. */
    public static final long MAX_CASES = 100_000;

    private String property = "Even";

    public TestBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.TEST_BENCH.type(), pos, state, 100_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    public String property() {
        return property;
    }

    @Override
    public Component info() {
        return Component.translatable("label.axiomatic.property", property);
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> stack.is(ModRegistries.NUMERAL_ITEM.get()) || stack.is(ModRegistries.STATEMENT_ITEM.get());
            case 2 -> stack.is(ModRegistries.NUMERAL_ITEM.get());
            default -> false;
        };
    }

    /** One-place properties the world knows, in definition order. */
    static List<String> properties(MathState state) {
        return state.signature().all().stream()
                .filter(d -> d instanceof Definition.Predicate && d.params().size() == 1)
                .map(Definition::name).toList();
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        List<String> props = properties(data(level).state());
        if (props.isEmpty()) {
            return;
        }
        int next = (props.indexOf(property) + 1) % props.size();
        property = props.get(next);
        setStatus(Component.translatable("status.axiomatic.property", property));
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        ItemStack in = items.get(0);
        if (in.isEmpty()) {
            return null;
        }
        MathState state = data(level).state();
        if (in.is(ModRegistries.STATEMENT_ITEM.get())) {
            return testRange(level, state, in);
        }
        if (!(state.signature().get(property) instanceof Definition.Predicate)) {
            setStatus(Component.translatable("status.axiomatic.unknown_property", property));
            return null;
        }
        NumeralData n = NumeralItem.data(in);
        if (n.hasProperty(property, false)) {
            // Already certified: pass it through for free.
            List<ItemStack> out = List.of(NumeralItem.stack(n, 1));
            if (!outputsFit(out)) {
                return null;
            }
            in.shrink(1);
            return new Job(0, 1, out);
        }
        ItemStack outSlot = items.get(1);
        if (!computing() && !outSlot.isEmpty() && outSlot.getCount() >= outSlot.getMaxStackSize()) {
            return null;
        }
        // Search on a worker thread with copies of the definitions; commit here.
        var sig = state.signature().copy();
        var deciders = state.deciders().copy(sig);
        String prop = property;
        return runAsync(() -> {
            Evaluator evaluator = new Evaluator(sig, MAX_STEPS);
            Truth truth = new Truth(sig, deciders, evaluator, n.value());
            try {
                return new Search(Certifier.byComputation(truth, prop, List.of(n.value())), evaluator.steps(), false);
            } catch (Evaluator.OutOfBudget e) {
                return new Search(null, evaluator.steps(), true);
            }
        }, search -> {
            ItemStack now = items.get(0);
            if (!now.is(ModRegistries.NUMERAL_ITEM.get()) || !NumeralItem.data(now).equals(n)) {
                return null;
            }
            NumeralData result = search.fact() == null ? n : n.withFact(search.fact());
            List<ItemStack> out = List.of(NumeralItem.stack(result, 1));
            if (!outputsFit(out)) {
                return null;
            }
            now.shrink(1);
            long cost = Math.max(1, search.steps());
            if (search.fact() != null) {
                playSound(ModRegistries.SOUND_CERTIFIED, 0.7f);
                io.github.kokoroko1234563.axiomatic.progress.Milestones.award(level, worldPosition, io.github.kokoroko1234563.axiomatic.progress.Milestones.CERTIFIED);
            }
            setStatus(search.tooBig() ? Component.translatable("status.axiomatic.too_big")
                    : Component.translatable(search.fact() == null ? "status.axiomatic.not_certified" : "status.axiomatic.certified",
                            prop, NumeralItem.display(n.value()), cost));
            return new Job(cost, (int) Math.clamp(cost / 100, 10, 400), out);
        });
    }

    private record Search(Fact fact, long steps, boolean tooBig) {}

    private record RangeRun(TestBench.Result result, long steps, Component problem) {}

    /**
     * Range mode: checks the statement for every value of its variables from 0 to the bound
     * numeral, and records the result in the Library: Tested up to the bound, or Refuted.
     */
    private Job testRange(ServerLevel level, MathState state, ItemStack in) {
        StatementData st = MathItems.statement(in);
        if (st == null || !items.get(2).is(ModRegistries.NUMERAL_ITEM.get())) {
            setStatus(Component.translatable("status.axiomatic.needs_bound"));
            return null;
        }
        BigInteger bound = NumeralItem.data(items.get(2)).value();
        int vars = Syntax.freeVars(st.formula()).size();
        BigInteger cases = bound.add(BigInteger.ONE).pow(Math.max(vars, 1));
        if (cases.compareTo(BigInteger.valueOf(MAX_CASES)) > 0) {
            setStatus(Component.translatable("status.axiomatic.too_many_cases", cases.toString()));
            return null;
        }
        if (!computing() && !outputsFit(List.of(in.copyWithCount(1)))) {
            return null;
        }
        var sig = state.signature().copy();
        var deciders = state.deciders().copy(sig);
        Formula formula = st.formula();
        return runAsync(() -> {
            Evaluator evaluator = new Evaluator(sig, MAX_STEPS);
            Truth truth = new Truth(sig, deciders, evaluator, bound);
            try {
                return new RangeRun(TestBench.test(formula, bound, truth), evaluator.steps(), null);
            } catch (Evaluator.OutOfBudget e) {
                return new RangeRun(null, evaluator.steps(), Component.translatable("status.axiomatic.too_big"));
            } catch (IllegalArgumentException | ClassCastException e) {
                return new RangeRun(null, evaluator.steps(), Component.translatable("status.axiomatic.not_computable"));
            }
        }, run -> {
            ItemStack now = items.get(0);
            if (now.isEmpty() || !st.equals(MathItems.statement(now))) {
                return null;
            }
            List<ItemStack> out = List.of(now.copyWithCount(1));
            if (!outputsFit(out)) {
                return null;
            }
            now.shrink(1);
            long cost = Math.max(1, run.steps());
            if (run.problem() != null) {
                setStatus(run.problem());
                return new Job(cost, 10, out);
            }
            Library.Status status = state.library().recordTest(formula, run.result());
            data(level).setDirty();
            setStatus(switch (run.result()) {
                case TestBench.Passed p -> Component.translatable("status.axiomatic.tested", bound.toString(), p.cases());
                case TestBench.Counterexample c -> {
                    playSound(ModRegistries.SOUND_REFUTED, 0.8f);
                    yield Component.translatable("status.axiomatic.counterexample", c.assignment().toString());
                }
                case TestBench.Inconclusive i -> Component.translatable("status.axiomatic.inconclusive", i.assignment().toString());
            });
            if (status instanceof Library.Proven) {
                setStatus(Component.translatable("status.axiomatic.already_proven"));
            }
            return new Job(cost, (int) Math.clamp(cost / 100, 10, 400), out);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("property", property);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        property = input.getStringOr("property", "Even");
    }

}
