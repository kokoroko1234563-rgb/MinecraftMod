package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implementation Bench: a proof of ∀x̄. fast(x̄) = slow(x̄) makes every machine compute slow with
 * fast from now on. All theorems about slow still hold (they are equal), but computing gets
 * cheaper: this is how a faster algorithm, such as binary multiplication, pays off everywhere.
 * The proof stays in its slot.
 */
public class ImplementationBenchBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(44, 31, "slot.axiomatic.implementation_proof")), List.of());

    public ImplementationBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.IMPLEMENTATION_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of();
    }

    @Override
    protected List<Integer> required() {
        return List.of(0);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        var proof = ctx.theorem(0);
        if (proof.formula() instanceof Formula.Eq eq && eq.left() instanceof Term.App fast
                && eq.right() instanceof Term.App slow
                && ctx.state().signature().implementation(slow.function()).equals(ctx.state().signature().implementation(fast.function()))) {
            setStatus(Component.translatable("status.axiomatic.implemented", slow.function(), fast.function()));
            return List.of();
        }
        String[] pair = ctx.state().kernel().implement(proof);
        data((ServerLevel) level).setDirty();
        playSound(ModRegistries.SOUND_PROVEN, 0.8f);
        setStatus(Component.translatable("status.axiomatic.implemented", pair[0], pair[1]));
        return List.of();
    }
}
