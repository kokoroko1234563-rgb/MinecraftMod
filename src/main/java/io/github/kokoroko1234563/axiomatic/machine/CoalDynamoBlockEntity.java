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
            List.of(MachineLayout.fuel(80, 31, "slot.axiomatic.fuel")), List.of());

    private int burnLeft;
    private List<EnergyNetwork.Consumer> consumers = List.of();
    private boolean lossless;
    /** Alternating power: double output, fuel burns twice as fast. */
    private boolean alternating;
    /** Phases mixed on a network not certified two-coloured: nothing reaches the machines. */
    private boolean shorted;

    public CoalDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.COAL_DYNAMO.type(), pos, state, 20_000);
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
            int speed = alternating ? 2 : 1;
            burnLeft = Math.max(0, burnLeft - speed);
            energy = Math.min(capacity, energy + (long) ENERGY_PER_TICK * speed);
            setStatus(Component.translatable(shorted ? "status.axiomatic.short_circuit"
                    : alternating ? "status.axiomatic.burning_alternating"
                    : lossless ? "status.axiomatic.burning_lossless" : "status.axiomatic.burning"));
        } else {
            setStatus(Component.translatable(shorted ? "status.axiomatic.short_circuit" : "status.axiomatic.no_fuel"));
        }
        if (level.getGameTime() % 20 == 0) {
            // Certificates from a Network Surveyor: a tree transfers without loss; a two-coloured
            // network with both phases runs alternating power. Mixed phases without one short out.
            // Each network the dynamo touches is judged by its own certificates.
            var certs = io.github.kokoroko1234563.axiomatic.world.NetworkData.get(level);
            List<EnergyNetwork.Graph> networks = EnergyNetwork.networks(level, worldPosition);
            lossless = !networks.isEmpty() && networks.stream().allMatch(g -> certs.isCertifiedTree(g.signature()));
            boolean anyAlternating = networks.stream().anyMatch(EnergyNetwork.Graph::alternating);
            boolean allCertified = networks.stream()
                    .allMatch(g -> !g.alternating() || certs.isCertifiedBipartite(g.signature()));
            boolean wasShorted = shorted;
            shorted = anyAlternating && !allCertified;
            alternating = anyAlternating && allCertified;
            consumers = EnergyNetwork.consumers(level, worldPosition, lossless);
            if (shorted) {
                // Only machines touching the dynamo still get power (a Surveyor there can check the
                // network); the short burns off what the dynamo holds for the cables.
                consumers = consumers.stream()
                        .filter(c -> c.machine().getBlockPos().distManhattan(worldPosition) == 1).toList();
                energy = 0;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.1);
                if (!wasShorted) {
                    level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.REDSTONE_TORCH_BURNOUT,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.0f);
                }
                markChanged();
            }
        }
        if (energy > 0 && !consumers.isEmpty()) {
            consumers = consumers.stream().filter(c -> !c.machine().isRemoved()).toList();
            long sent = EnergyNetwork.distribute(consumers, energy);
            if (sent > 0) {
                energy -= sent;
                markChanged();
            }
        }
        super.tick(level);
    }

    public boolean alternating() {
        return alternating;
    }

    public boolean shorted() {
        return shorted;
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
