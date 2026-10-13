package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Certifier;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
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
 * Test Bench, numeral mode: tries to certify a numeral with a chosen property, by witness search
 * (or its linked decider). Every candidate costs energy, more for bigger numerals. Witnesses are
 * searched up to the numeral's own value, which covers properties like Even and ≤.
 */
public class TestBenchBlockEntity extends MachineBlockEntity {
    /** Hard limit on one search, so a huge numeral cannot freeze the server. */
    public static final long MAX_STEPS = 20_000_000;
    private static final MachineLayout LAYOUT = new MachineLayout(
            List.of(MachineLayout.input(56, 35, null), MachineLayout.output(116, 35, null)),
            List.of("button.axiomatic.next_property"));

    private String property = "Even";

    public TestBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.TEST_BENCH_BE.get(), pos, state, 100_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    public String property() {
        return property;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return slot == 0 && stack.is(ModRegistries.NUMERAL_ITEM.get());
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
