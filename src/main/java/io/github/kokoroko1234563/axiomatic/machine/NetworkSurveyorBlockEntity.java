package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.NetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Network Surveyor: scans the cable network it touches into a graph and reports its nodes, edges,
 * cycles and degrees. A network that is a tree (one route to every machine) is certified, which
 * makes energy transfer on it lossless. A network whose cables use both phases is checked as a
 * two-colouring; if no two neighbouring cables share a phase it is certified for alternating power.
 * (Until then the network is shorted, so a Surveyor checking it should touch the dynamo.) Each survey costs energy per node; any change to the
 * network voids the certificate until the next survey.
 */
public class NetworkSurveyorBlockEntity extends MachineBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(), List.of("button.axiomatic.survey"));
    public static final int ENERGY_PER_NODE = 10;

    private boolean requested = true;
    private Component report = Component.empty();

    public NetworkSurveyorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.NETWORK_SURVEYOR.type(), pos, state, 20_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        return report;
    }

    @Override
    public void onButton(ServerLevel level, net.minecraft.world.entity.player.Player player, int id) {
        requested = true;
    }

    /** Certifies what can be certified about one network and describes it. */
    private static Component survey(ServerLevel level, EnergyNetwork.Graph graph) {
        boolean tree = graph.isTree();
        if (tree) {
            NetworkData.get(level).certifyTree(graph.signature());
        }
        if (graph.eulerian()) {
            // The cables have an Euler circuit: every cable has an even number of cable neighbours.
            NetworkData.get(level).certifyEulerian(graph.signature());
        }
        Component status = Component.translatable(tree ? "status.axiomatic.network_tree" : "status.axiomatic.network_cycles",
                graph.edges() - graph.nodes() + 1).append(Component.translatable(graph.eulerian()
                        ? "status.axiomatic.network_even" : "status.axiomatic.network_odd"));
        if (graph.alternating()) {
            // The player's phases are the claimed two-colouring; check every edge joins different ones.
            if (graph.twoColoured()) {
                NetworkData.get(level).certifyBipartite(graph.signature());
                status = status.copy().append(Component.translatable("status.axiomatic.network_bipartite"));
            } else {
                BlockPos at = graph.clash().getFirst();
                status = status.copy().append(Component.translatable("status.axiomatic.network_clash",
                        at.getX(), at.getY(), at.getZ()));
            }
        }
        return status;
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        // Survey on request, and every 10 seconds so a changed network is noticed.
        if (!requested && level.getGameTime() % 200 != 0) {
            return null;
        }
        List<EnergyNetwork.Graph> networks = EnergyNetwork.networks(level, worldPosition);
        if (networks.isEmpty()) {
            setStatus(Component.translatable("status.axiomatic.no_network"));
            requested = false;
            return null;
        }
        long cost = (long) networks.stream().mapToInt(EnergyNetwork.Graph::nodes).sum() * ENERGY_PER_NODE;
        if (energy < cost) {
            setStatus(Component.translatable("status.axiomatic.no_energy"));
            return null;
        }
        requested = false;
        // Each network it touches is surveyed and certified on its own; the report shows the first.
        Component status = null;
        for (EnergyNetwork.Graph graph : networks) {
            Component line = survey(level, graph);
            if (status == null) {
                status = line;
                report = Component.translatable("label.axiomatic.network", graph.nodes(), graph.edges());
            }
        }
        setStatus(status);
        markChanged();
        return new Job(cost, 20, List.of());
    }
}
