package io.github.kokoroko1234563.axiomatic.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The cable network around a generator, as a graph: nodes are the cables and the machines
 * touching them, edges are cable–cable and cable–machine contacts. Energy loses 2% per cable on
 * the way to each machine (at most half), unless the network is certified a tree by a Network
 * Surveyor: then every route is unique and transfer is lossless. Machines touching the generator
 * directly always get their energy without loss.
 */
public final class EnergyNetwork {
    /** Upper bound on cables searched per network, so a huge cable mesh cannot stall a tick. */
    public static final int MAX_CABLES = 1024;
    public static final double LOSS_PER_CABLE = 0.02;
    public static final double MAX_LOSS = 0.5;

    /** A machine on the network and the fraction of sent energy that reaches it. */
    public record Consumer(MachineBlockEntity machine, double efficiency) {}

    /** The graph of a network: its nodes and edges, and a signature that changes with its shape. */
    public record Graph(Set<BlockPos> cables, Set<BlockPos> machines, int edges, Map<BlockPos, Integer> degrees,
            Set<BlockPos> phaseB, List<BlockPos> clash) {
        public int nodes() {
            return cables.size() + machines.size();
        }

        /** Connected (by construction) with one edge fewer than nodes: no cycles. */
        public boolean isTree() {
            return nodes() > 0 && edges == nodes() - 1;
        }

        /** Whether the cables use both phases: the network then runs alternating power. */
        public boolean alternating() {
            return !phaseB.isEmpty() && phaseB.size() < cables.size();
        }

        /** Whether every cable–cable edge joins different phases: a two-colouring of the cables. */
        public boolean twoColoured() {
            return clash.isEmpty();
        }

        public boolean allDegreesEven() {
            return degrees.values().stream().allMatch(d -> d % 2 == 0);
        }

        /**
         * Whether the cables alone have an Euler circuit: connected (by construction), with a
         * cycle, and every cable joined to an even number of other cables. A courier can then walk
         * every cable exactly once and come back, passing each machine on the way.
         */
        public boolean eulerian() {
            if (cables.size() < 3) {
                return false;
            }
            for (BlockPos c : cables) {
                int n = 0;
                for (Direction d : Direction.values()) {
                    if (cables.contains(c.relative(d))) {
                        n++;
                    }
                }
                if (n % 2 != 0) {
                    return false;
                }
            }
            return true;
        }

        /** Identifies this exact network; any added or removed cable or machine changes it. */
        public long signature() {
            long h = 1125899906842597L;
            for (BlockPos p : new TreeSet<>(cables)) {
                h = 31 * h + p.asLong() + (phaseB.contains(p) ? 17 : 0);
            }
            h = 31 * h + 7;
            for (BlockPos p : new TreeSet<>(machines)) {
                h = 31 * h + p.asLong();
            }
            return h;
        }
    }

    private EnergyNetwork() {}

    /** From the config when it is loaded (it is not in unit tests), else the defaults. */
    private static double lossPerCable() {
        try {
            return io.github.kokoroko1234563.axiomatic.AxiomaticConfig.CABLE_LOSS.get();
        } catch (IllegalStateException e) {
            return LOSS_PER_CABLE;
        }
    }

    private static double maxLoss() {
        try {
            return io.github.kokoroko1234563.axiomatic.AxiomaticConfig.MAX_CABLE_LOSS.get();
        } catch (IllegalStateException e) {
            return MAX_LOSS;
        }
    }

    /** The cable graph reachable through cables touching {@code start}. */
    public static Graph graph(Level level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos n = start.relative(d);
            if (isCable(level, n) && cables.add(n.immutable())) {
                queue.add(n);
            }
        }
        while (!queue.isEmpty() && cables.size() < MAX_CABLES) {
            BlockPos pos = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (isCable(level, n) && cables.add(n)) {
                    queue.add(n);
                }
            }
        }
        Set<BlockPos> machines = new HashSet<>();
        Map<BlockPos, Integer> degrees = new HashMap<>();
        Set<BlockPos> phaseB = new HashSet<>();
        for (BlockPos c : cables) {
            if (CableBlock.phase(level.getBlockState(c))) {
                phaseB.add(c);
            }
        }
        List<BlockPos> clash = new ArrayList<>();
        int edges = 0;
        for (BlockPos c : cables) {
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (cables.contains(n)) {
                    // Count each cable–cable edge once.
                    if (c.asLong() < n.asLong()) {
                        edges++;
                        if (phaseB.contains(c) == phaseB.contains(n)) {
                            clash.add(c);
                        }
                    }
                    degrees.merge(c, 1, Integer::sum);
                } else if (level.getBlockEntity(n) instanceof MachineBlockEntity) {
                    machines.add(n.immutable());
                    edges++;
                    degrees.merge(c, 1, Integer::sum);
                    degrees.merge(n.immutable(), 1, Integer::sum);
                }
            }
        }
        return new Graph(cables, machines, edges, degrees, phaseB, clash);
    }

    private static boolean isCable(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof CableBlock;
    }

    /** The machines a generator powers and how much of the energy reaches each. */
    public static List<Consumer> consumers(Level level, BlockPos generator, boolean lossless) {
        List<Consumer> found = new ArrayList<>();
        Set<BlockPos> direct = new HashSet<>();
        for (Direction d : Direction.values()) {
            BlockPos n = generator.relative(d);
            if (level.getBlockEntity(n) instanceof MachineBlockEntity m && m.acceptsEnergy()) {
                found.add(new Consumer(m, 1.0));
                direct.add(n);
            }
        }
        // Hop counts through cables, from the cables touching the generator.
        Map<BlockPos, Integer> hops = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos n = generator.relative(d);
            if (isCable(level, n)) {
                hops.put(n, 1);
                queue.add(n);
            }
        }
        Set<BlockPos> reached = new HashSet<>(direct);
        while (!queue.isEmpty() && hops.size() < MAX_CABLES) {
            BlockPos pos = queue.poll();
            int h = hops.get(pos);
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (isCable(level, n)) {
                    if (!hops.containsKey(n)) {
                        hops.put(n, h + 1);
                        queue.add(n);
                    }
                } else if (!n.equals(generator) && reached.add(n.immutable())
                        && level.getBlockEntity(n) instanceof MachineBlockEntity m && m.acceptsEnergy()) {
                    double efficiency = lossless ? 1.0 : Math.max(1 - maxLoss(), 1 - lossPerCable() * h);
                    found.add(new Consumer(m, efficiency));
                }
            }
        }
        return found;
    }

    /**
     * Shares up to {@code budget} energy among the consumers as evenly as they accept it, losing
     * a part on the way to each. Returns what the generator spent.
     */
    public static long distribute(List<Consumer> consumers, long budget) {
        long spent = 0;
        List<Consumer> open = new ArrayList<>(consumers);
        while (budget - spent > 0 && !open.isEmpty()) {
            long share = Math.max(1, (budget - spent) / open.size());
            List<Consumer> full = new ArrayList<>();
            for (Consumer c : open) {
                long offer = Math.min(share, budget - spent);
                long delivered = c.machine().receiveEnergy((long) Math.floor(offer * c.efficiency()));
                long cost = c.efficiency() >= 1 ? delivered : (long) Math.ceil(delivered / c.efficiency());
                spent += Math.min(cost, offer);
                if (delivered < (long) Math.floor(offer * c.efficiency()) || delivered == 0) {
                    full.add(c);
                }
                if (budget - spent <= 0) {
                    break;
                }
            }
            open.removeAll(full);
        }
        return spent;
    }
}
