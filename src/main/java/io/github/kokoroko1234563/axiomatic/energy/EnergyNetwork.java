package io.github.kokoroko1234563.axiomatic.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Finds the machines a generator can power: those touching the generator directly or through a
 * chain of connected cables. Kept deliberately simple: no loss, no throughput limit.
 */
public final class EnergyNetwork {
    /** Upper bound on cables searched per network, so a huge cable mesh cannot stall a tick. */
    public static final int MAX_CABLES = 1024;

    private EnergyNetwork() {}

    public static List<MachineBlockEntity> consumers(Level level, BlockPos generator) {
        List<MachineBlockEntity> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(generator);
        queue.add(generator);
        int cables = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() instanceof CableBlock) {
                    if (++cables <= MAX_CABLES) {
                        queue.add(next);
                    }
                } else if (level.getBlockEntity(next) instanceof MachineBlockEntity machine && machine.acceptsEnergy()) {
                    found.add(machine);
                }
            }
        }
        return found;
    }

    /** Shares {@code amount} among the consumers as evenly as they accept it; returns what was delivered. */
    public static long distribute(List<MachineBlockEntity> consumers, long amount) {
        long delivered = 0;
        List<MachineBlockEntity> open = new ArrayList<>(consumers);
        while (amount - delivered > 0 && !open.isEmpty()) {
            long share = Math.max(1, (amount - delivered) / open.size());
            List<MachineBlockEntity> full = new ArrayList<>();
            for (MachineBlockEntity m : open) {
                long give = Math.min(share, amount - delivered);
                long got = m.receiveEnergy(give);
                delivered += got;
                if (got < give) {
                    full.add(m);
                }
                if (amount - delivered <= 0) {
                    break;
                }
            }
            open.removeAll(full);
            if (full.isEmpty() && share == 1 && amount - delivered > 0 && open.isEmpty()) {
                break;
            }
        }
        return delivered;
    }
}
