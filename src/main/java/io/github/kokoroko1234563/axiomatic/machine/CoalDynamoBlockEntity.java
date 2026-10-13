package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Coal Dynamo: burns furnace fuel for energy and sends it down connected cables. One unit of
 * energy pays for one axiom use or one computation step; a piece of coal gives 16,000.
 */
public class CoalDynamoBlockEntity extends MachineBlockEntity {
    public static final int ENERGY_PER_TICK = 10;
    private static final MachineLayout LAYOUT = new MachineLayout(
            List.of(MachineLayout.fuel(80, 35, "slot.axiomatic.fuel")), List.of());

    private int burnLeft;
    private List<MachineBlockEntity> consumers = List.of();

    public CoalDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.COAL_DYNAMO_BE.get(), pos, state, 20_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public boolean acceptsEnergy() {
        return false;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return level != null && level.fuelValues().isFuel(stack);
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        return null;
    }

    @Override
    protected void tick(ServerLevel level) {
        if (burnLeft <= 0 && energy < capacity) {
            ItemStack fuel = items.get(0);
            int duration = fuel.isEmpty() ? 0 : fuel.getBurnTime(null, level.fuelValues());
            if (duration > 0) {
                var remainder = fuel.getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty() && remainder != null) {
                    items.set(0, remainder.create());
                }
                burnLeft = duration;
                markChanged();
            }
        }
        if (burnLeft > 0) {
            burnLeft--;
            energy = Math.min(capacity, energy + ENERGY_PER_TICK);
            setStatus(Component.translatable("status.axiomatic.burning"));
        } else {
            setStatus(Component.translatable("status.axiomatic.no_fuel"));
        }
        if (level.getGameTime() % 20 == 0) {
            consumers = EnergyNetwork.consumers(level, worldPosition);
        }
        if (energy > 0 && !consumers.isEmpty()) {
            consumers = consumers.stream().filter(m -> !m.isRemoved()).toList();
            long sent = EnergyNetwork.distribute(consumers, energy);
            if (sent > 0) {
                energy -= sent;
                markChanged();
            }
        }
        super.tick(level);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("burn_left", burnLeft);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnLeft = input.getIntOr("burn_left", 0);
    }
}
