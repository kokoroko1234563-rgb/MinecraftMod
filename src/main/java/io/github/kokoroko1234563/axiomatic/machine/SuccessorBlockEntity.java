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

/** Successor: takes a numeral and outputs it one higher (axiom 2). Certificates do not carry over. */
public class SuccessorBlockEntity extends MachineBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(
            List.of(MachineLayout.input(56, 31, null), MachineLayout.output(116, 31, null)), List.of());

    public SuccessorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.SUCCESSOR.type(), pos, state, 1_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return slot == 0 && stack.is(ModRegistries.NUMERAL_ITEM.get());
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        ItemStack in = items.get(0);
        if (in.isEmpty()) {
            setStatus(Component.empty());
            return null;
        }
        NumeralData n = NumeralItem.data(in);
        List<ItemStack> out = List.of(NumeralItem.stack(n.withValue(n.value().add(BigInteger.ONE)), 1));
        if (!outputsFit(out)) {
            return null;
        }
        in.shrink(1);
        setStatus(Component.translatable("status.axiomatic.working"));
        return new Job(1, 4, out);
    }
}
