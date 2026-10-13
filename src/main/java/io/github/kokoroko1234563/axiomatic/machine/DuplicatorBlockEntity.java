package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Duplicator: copies a numeral, certificates included. Machines consume their inputs, so this is
 * how a number gets used twice. In the unary age copying costs the numeral's value.
 */
public class DuplicatorBlockEntity extends MachineBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(
            List.of(MachineLayout.input(56, 35, null), MachineLayout.output(116, 26, null),
                    MachineLayout.output(116, 44, null)), List.of());

    public DuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DUPLICATOR_BE.get(), pos, state, 10_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return slot == 0 && stack.is(ModRegistries.NUMERAL_ITEM.get());
    }

    /** What a copy costs: the value now; its digit count once binary numerals are proven (later). */
    public static long copyCost(BigInteger value) {
        return value.min(BigInteger.valueOf(Long.MAX_VALUE / 2)).longValue() + 1;
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        ItemStack in = items.get(0);
        if (in.isEmpty()) {
            setStatus(Component.empty());
            return null;
        }
        NumeralData n = NumeralItem.data(in);
        List<ItemStack> out = List.of(NumeralItem.stack(n, 1), NumeralItem.stack(n, 1));
        if (!outputsFit(out)) {
            return null;
        }
        in.shrink(1);
        long cost = copyCost(n.value());
        setStatus(Component.translatable("status.axiomatic.cost", cost));
        return new Job(cost, (int) Math.clamp(cost / 50, 4, 400), out);
    }
}
