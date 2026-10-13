package io.github.kokoroko1234563.axiomatic.energy;

import io.github.kokoroko1234563.axiomatic.AxiomaticConfig;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Lets other mods' generators power Axiomatic machines through NeoForge's energy capability.
 * Machines only take energy in: Axiomatic energy cannot be pulled out to run other mods' machines.
 * {@link AxiomaticConfig#FOREIGN_ENERGY_PER_UNIT} foreign units make one unit here.
 */
public final class ForeignEnergy implements EnergyHandler {
    private final MachineBlockEntity machine;
    private final Journal journal = new Journal();
    /** Foreign energy received towards the next unit (less than one unit's worth). */
    private int credit;

    public ForeignEnergy(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public static void register(RegisterCapabilitiesEvent event) {
        for (ModRegistries.Machine<?> m : ModRegistries.MACHINES) {
            event.registerBlockEntity(Capabilities.Energy.BLOCK, m.type(),
                    (be, side) -> be.acceptsEnergy() ? be.foreignEnergy() : null);
        }
    }

    private static int rate() {
        try {
            return AxiomaticConfig.FOREIGN_ENERGY_PER_UNIT.get();
        } catch (IllegalStateException e) {
            return 4;
        }
    }

    @Override
    public long getAmountAsLong() {
        return saturatedTimes(machine.energy(), rate());
    }

    @Override
    public long getCapacityAsLong() {
        return saturatedTimes(machine.capacity(), rate());
    }

    private static long saturatedTimes(long a, int b) {
        long r = a * b;
        return b != 0 && r / b != a ? Long.MAX_VALUE : r;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int rate = rate();
        long room = machine.capacity() - machine.energy();
        if (amount <= 0 || rate <= 0 || room <= 0) {
            return 0;
        }
        // Foreign energy below one unit is kept as credit, so slow generators still get through.
        long total = credit + (long) amount;
        long units = Math.min(total / rate, room);
        journal.updateSnapshots(transaction);
        int accepted;
        if (units < room) {
            accepted = amount;
            credit = (int) (total - units * rate);
        } else {
            accepted = (int) (units * rate - credit);
            credit = 0;
        }
        machine.setEnergyQuietly(machine.energy() + units);
        return accepted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }

    /** Undoes inserts in aborted transactions; marks the machine changed once a root commits. */
    private record State(long energy, int credit) {}

    private final class Journal extends SnapshotJournal<State> {
        @Override
        protected State createSnapshot() {
            return new State(machine.energy(), credit);
        }

        @Override
        protected void revertToSnapshot(State snapshot) {
            machine.setEnergyQuietly(snapshot.energy());
            credit = snapshot.credit();
        }

        @Override
        protected void onRootCommit(State originalState) {
            if (machine.energy() != originalState.energy()) {
                machine.energyChanged();
            }
        }
    }
}
