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
    public static final long MAX_STEPS = 20_000_000;
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
        Evaluator evaluator = new Evaluator(state.signature(), MAX_STEPS);
        Truth truth = new Truth(state.signature(), state.deciders(), evaluator, n.value());
        Fact fact;
        try {
            fact = Certifier.byComputation(truth, property, List.of(n.value()));
        } catch (Evaluator.OutOfBudget e) {
            setStatus(Component.translatable("status.axiomatic.too_big"));
            return null;
        }
        NumeralData result = fact == null ? n : n.withFact(fact);
        List<ItemStack> out = List.of(NumeralItem.stack(result, 1));
        if (!outputsFit(out)) {
            return null;
        }
        in.shrink(1);
        long cost = Math.max(1, evaluator.steps());
        setStatus(Component.translatable(fact == null ? "status.axiomatic.not_certified" : "status.axiomatic.certified",
                property, NumeralItem.display(n.value()), cost));
        return new Job(cost, (int) Math.clamp(cost / 100, 10, 400), out);
    }

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
        List<ItemStack> out = List.of(in.copyWithCount(1));
        if (!outputsFit(out)) {
            return null;
        }
        Evaluator evaluator = new Evaluator(state.signature(), MAX_STEPS);
        Truth truth = new Truth(state.signature(), state.deciders(), evaluator, bound);
        TestBench.Result result;
        try {
            result = TestBench.test(st.formula(), bound, truth);
        } catch (Evaluator.OutOfBudget e) {
            setStatus(Component.translatable("status.axiomatic.too_big"));
            return null;
        } catch (IllegalArgumentException | ClassCastException e) {
            setStatus(Component.translatable("status.axiomatic.not_computable"));
            return null;
        }
        in.shrink(1);
        Library.Status status = state.library().recordTest(st.formula(), result);
        data(level).setDirty();
        setStatus(switch (result) {
            case TestBench.Passed p -> Component.translatable("status.axiomatic.tested", bound.toString(), p.cases());
            case TestBench.Counterexample c -> Component.translatable("status.axiomatic.counterexample", c.assignment().toString());
            case TestBench.Inconclusive i -> Component.translatable("status.axiomatic.inconclusive", i.assignment().toString());
        });
        if (status instanceof Library.Proven) {
            setStatus(Component.translatable("status.axiomatic.already_proven"));
        }
        long cost = Math.max(1, evaluator.steps());
        return new Job(cost, (int) Math.clamp(cost / 100, 10, 400), out);
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
