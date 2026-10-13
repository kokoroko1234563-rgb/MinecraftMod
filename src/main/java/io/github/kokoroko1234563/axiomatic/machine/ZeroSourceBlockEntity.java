package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Zero Source: outputs the numeral 0 (axiom 1). */
public class ZeroSourceBlockEntity extends MachineBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(
            List.of(MachineLayout.output(116, 31, null)), List.of());

    public ZeroSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.ZERO_SOURCE.type(), pos, state, 1_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        List<ItemStack> out = List.of(NumeralItem.stack(NumeralData.ZERO, 1));
        if (!outputsFit(out)) {
            return null;
        }
        setStatus(Component.translatable("status.axiomatic.working"));
        io.github.kokoroko1234563.axiomatic.progress.Milestones.award(level, worldPosition, io.github.kokoroko1234563.axiomatic.progress.Milestones.NUMERAL);
        return new Job(1, 10, out);
    }
}
