package io.github.kokoroko1234563.axiomatic.machine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Courier Station: delivers its items to the machines on its cable network, taking turns, one
 * item per machine visit for 1 unit. The courier walks every cable exactly once, so it only runs on
 * a network a Network Surveyor has certified Eulerian: every cable joined to an even number of
 * other cables (a loop with no loose ends, for example).
 */
public class CourierStationBlockEntity extends MachineBlockEntity {
    public static final int SLOTS = 3;
    public static final int ENERGY_PER_DELIVERY = 1;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.cargo"), MachineLayout.input(44, 31, "slot.axiomatic.cargo"),
            MachineLayout.input(62, 31, "slot.axiomatic.cargo")), List.of());

    private int turn;

    public CourierStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.COURIER_STATION.type(), pos, state, 2_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return slot < SLOTS;
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        return null;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        int cargo = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                cargo = i;
                break;
            }
        }
        if (cargo < 0) {
            setStatus(Component.empty());
            return;
        }
        List<EnergyNetwork.Graph> networks = EnergyNetwork.networks(level, worldPosition);
        if (networks.isEmpty()) {
            setStatus(Component.translatable("status.axiomatic.no_network"));
            return;
        }
        NetworkData certs = NetworkData.get(level);
        if (!networks.stream().allMatch(g -> certs.isCertifiedEulerian(g.signature()))) {
            setStatus(Component.translatable("status.axiomatic.courier_needs_euler"));
            return;
        }
        if (energy < ENERGY_PER_DELIVERY) {
            setStatus(Component.translatable("status.axiomatic.no_energy"));
            return;
        }
        List<BlockPos> stops = new ArrayList<>(networks.stream().flatMap(g -> g.machines().stream()).distinct().toList());
        stops.remove(worldPosition);
        stops.sort(Comparator.comparingLong(BlockPos::asLong));
        // One visit per run: the next machine that takes the item.
        for (int k = 0; k < stops.size(); k++) {
            BlockPos stop = stops.get((turn + k) % stops.size());
            if (!(level.getBlockEntity(stop) instanceof MachineBlockEntity machine)) {
                continue;
            }
            ItemStack one = items.get(cargo).copyWithCount(1);
            ItemStack left = HopperBlockEntity.addItem(this, machine, one, Direction.UP);
            if (left.isEmpty()) {
                items.get(cargo).shrink(1);
                energy -= ENERGY_PER_DELIVERY;
                machine.setChanged();
                turn = (turn + k + 1) % stops.size();
                setStatus(Component.translatable("status.axiomatic.courier_delivered", stops.size()));
                markChanged();
                return;
            }
        }
        setStatus(Component.translatable("status.axiomatic.courier_nowhere"));
    }
}
