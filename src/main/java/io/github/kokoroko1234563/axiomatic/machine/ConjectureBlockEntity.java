package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.AxiomaticConfig;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Conjecture block: keeps a conjecture alive. While powered it pays upkeep, "the cost of
 * uncertainty"; unpowered it goes dormant and statements built on it stop working (not a
 * disaster). If a background check refutes the conjecture, the block fails: a disaster.
 */
public class ConjectureBlockEntity extends MachineBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(), List.of());

    private String conjecture = "";
    private boolean failed;

    public ConjectureBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CONJECTURE_BLOCK.type(), pos, state, 10_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    public String conjecture() {
        return conjecture;
    }

    /** Binds the block to a conjecture (normally done by placing the item from the Conjecture Bench). */
    public void bind(String name) {
        conjecture = name;
        markChanged();
    }

    @Override
    public Component info() {
        return Component.translatable("label.axiomatic.conjecture", conjecture);
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        return null;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (failed || conjecture.isEmpty() || level.getGameTime() % 20 != 0) {
            return;
        }
        var data = data(level);
        Library library = data.state().library();
        Library.Conjecture c = library.conjectureNamed(conjecture);
        if (c == null) {
            return;
        }
        switch (c.state()) {
            case REFUTED -> disaster(level, c);
            case PROVEN -> setStatus(Component.translatable("status.axiomatic.conjecture_proven"));
            case ACTIVE, DORMANT -> {
                long upkeep = AxiomaticConfig.CONJECTURE_UPKEEP.get();
                boolean paid = energy >= upkeep;
                if (paid) {
                    energy -= upkeep;
                    markChanged();
                }
                if (paid == (c.state() == Library.ConjectureState.DORMANT)) {
                    library.setDormant(conjecture, !paid);
                    data.setDirty();
                }
                setStatus(Component.translatable(paid ? "status.axiomatic.conjecture_active" : "status.axiomatic.conjecture_dormant"));
            }
        }
    }

    /** The conjecture was refuted: the block blows up and leaves paradox junk. */
    private void disaster(ServerLevel level, Library.Conjecture c) {
        failed = true;
        BlockPos pos = worldPosition;
        io.github.kokoroko1234563.axiomatic.progress.Milestones.award(level, pos, io.github.kokoroko1234563.axiomatic.progress.Milestones.DISASTER);
        boolean contained = io.github.kokoroko1234563.axiomatic.block.ContainmentControllerBlock.contains(level, pos);
        String counterexample = c.counterexample() == null ? "?" : c.counterexample().toString();
        level.players().stream().filter(p -> p.blockPosition().closerThan(pos, 64)).forEach(p -> p.sendSystemMessage(
                Component.translatable("message.axiomatic.disaster", c.name(), counterexample)));
        level.playSound(null, pos, ModRegistries.SOUND_DISASTER.get(), SoundSource.BLOCKS, 1.5f, 1.0f);
        level.removeBlock(pos, false);
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(ModRegistries.PARADOX_JUNK.get()));
        if (contained) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.02);
        } else {
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2.0f,
                    AxiomaticConfig.DISASTER_BLOCK_DAMAGE.get() ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        }
        if (level.getBlockState(pos).isAir()) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String name = components.get(ModRegistries.CONJECTURE.get());
        if (name != null) {
            conjecture = name;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistries.CONJECTURE.get(), conjecture);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("conjecture", conjecture);
        output.putBoolean("failed", failed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        conjecture = input.getStringOr("conjecture", "");
        failed = input.getBooleanOr("failed", false);
    }
}
