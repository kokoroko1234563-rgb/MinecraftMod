package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Certifier;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Truth;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Infinite Prime Well: emits the primes one after another, forever, each certified by computation
 * (the world's linked Prime decider if there is one, otherwise trial division). Its recipe needs
 * the proof that primes never run out, which is what guarantees each search ends.
 */
public class PrimeWellBlockEntity extends MachineBlockEntity {
    private static final long MAX_STEPS = 400_000_000;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.output(116, 31, null), MachineLayout.output(134, 31, null)), List.of());

    private BigInteger last = BigInteger.ONE;

    public PrimeWellBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.PRIME_WELL.type(), pos, state, 1_000_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        return Component.translatable("label.axiomatic.well_last", NumeralItem.display(last));
    }

    private record Found(Fact fact, long steps, boolean stuck) {}

    @Override
    protected Job tryStartJob(ServerLevel level) {
        if (!computing() && !items.get(0).isEmpty() && !items.get(1).isEmpty()) {
            return null;
        }
        MathState state = data(level).state();
        var sig = state.signature().copy();
        var deciders = state.deciders().copy(sig);
        BigInteger from = last;
        return runAsync(() -> {
            Evaluator evaluator = new Evaluator(sig, MAX_STEPS);
            try {
                for (BigInteger v = from.add(BigInteger.ONE); ; v = v.add(BigInteger.ONE)) {
                    Truth truth = new Truth(sig, deciders, evaluator, v);
                    Truth.Value value = truth.property("Prime", List.of(v));
                    if (value == Truth.Value.TRUE) {
                        return new Found(Certifier.byComputation(truth, "Prime", List.of(v)), evaluator.steps(), false);
                    }
                    if (value == Truth.Value.UNKNOWN) {
                        return new Found(null, evaluator.steps(), true);
                    }
                }
            } catch (Evaluator.OutOfBudget e) {
                return new Found(null, evaluator.steps(), true);
            }
        }, found -> {
            if (found.fact() == null) {
                setStatus(Component.translatable("status.axiomatic.well_stuck"));
                return null;
            }
            BigInteger prime = found.fact().args().get(0);
            List<ItemStack> out = List.of(NumeralItem.stack(NumeralData.ZERO.withValue(prime).withFact(found.fact()), 1));
            if (!outputsFit(out)) {
                return null;
            }
            last = prime;
            long cost = Math.max(1, found.steps());
            setStatus(Component.translatable("status.axiomatic.forged", NumeralItem.display(prime), cost));
            markChanged();
            return new Job(cost, (int) Math.clamp(cost / 200, 10, 600), out);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("last", last.toString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        last = new BigInteger(input.getStringOr("last", "1"));
    }
}
